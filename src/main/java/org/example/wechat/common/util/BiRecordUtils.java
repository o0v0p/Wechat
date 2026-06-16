package org.example.wechat.common.util;

import lombok.extern.slf4j.Slf4j;
import org.example.wechat.dao.FriendMapper;
import org.example.wechat.pojo.entity.BizFriend;
import org.example.wechat.pojo.entity.BizUser;
import org.example.wechat.pojo.entity.BizFriendApply;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.function.BiConsumer;

/**
 * 双向记录维护工具类
 * 用于保证好友申请/关系表的前向记录和反向记录的一致性
 */
@Slf4j
public class BiRecordUtils {

    /**
     * 双向记录对 - 包含正向和反向两条记录
     */
    public static class BiRecordPair {
        private final BizFriendApply forward;   // 正向记录（原记录）
        private final BizFriendApply reverse;   // 反向记录（新记录）
        private BizFriend forwardFriend;     // 当前用户的好友记录（对方存入自己表）
        private BizFriend reverseFriend;     // 对方用户的好友记录（自己存入对方表）

        public BiRecordPair(BizFriendApply forward, BizFriendApply reverse) {
            this.forward = forward;
            this.reverse = reverse;
        }

        public BizFriendApply getForward() { return forward; }
        public BizFriendApply getReverse() { return reverse; }
        public BizFriend getForwardFriend() { return forwardFriend; }
        public BizFriend getReverseFriend() { return reverseFriend; }

        /**
         * 设置两条好友记录
         */
        public BiRecordPair setFriendRecords(BizFriend forwardFriend, BizFriend reverseFriend) {
            this.forwardFriend = forwardFriend;
            this.reverseFriend = reverseFriend;
            return this;
        }

        /**
         * 同步设置两条记录的状态
         */
        public BiRecordPair setStatus(Integer status) {
            forward.setStatus(status);
            reverse.setStatus(status);
            return this;
        }

        /**
         * 同步设置两条记录的更新时间
         */
        public BiRecordPair setUpdateTime(LocalDateTime time) {
            forward.setUpdatedTime(time);
            reverse.setUpdatedTime(time);
            if (forwardFriend != null) forwardFriend.setUpdatedTime(time);
            if (reverseFriend != null) reverseFriend.setUpdatedTime(time);
            return this;
        }

        /**
         * 同步设置两条记录的更新人
         */
        public BiRecordPair setUpdater(Long userId) {
            forward.setUpdaterId(userId);
            reverse.setUpdaterId(userId);
            if (forwardFriend != null) forwardFriend.setUpdaterId(userId);
            if (reverseFriend != null) reverseFriend.setUpdaterId(userId);
            return this;
        }

        /**
         * 批量设置两条记录相同属性（自定义）
         */
        public BiRecordPair batchSet(BiConsumer<BizFriendApply, BizFriendApply> setter) {
            setter.accept(forward, reverse);
            return this;
        }

        /**
         * 同步更新两条记录（确保时间、更新人一致）
         */
        public void syncUpdate(Runnable updateLogic) {
            updateLogic.run();
            reverse.setUpdatedTime(forward.getUpdatedTime());
            reverse.setUpdaterId(forward.getUpdaterId());
        }
    }

    // ==================== 原有方法（保持不变） ====================

    /**
     * 从已同意的申请创建双向记录对（仅申请记录，不含好友表）
     * 【原有方法，保持不变】
     */
    public static BiRecordPair createFromAgreedApply(BizFriendApply agreedApply, Long currentUserId) {
        LocalDateTime now = LocalDateTime.now();

        BizFriendApply forward = new BizFriendApply();
        BeanUtils.copyProperties(agreedApply, forward);
        forward.setStatus(1);
        forward.setUpdatedTime(now);
        forward.setUpdaterId(currentUserId);

        BizFriendApply reverse = new BizFriendApply();
        reverse.setUserId(agreedApply.getFriendId());
        reverse.setFriendId(agreedApply.getUserId());
        reverse.setStatus(1);
        reverse.setApplyRemark(agreedApply.getApplyRemark());
        reverse.setSource(agreedApply.getSource());
        reverse.setCreatedTime(now);
        reverse.setUpdatedTime(now);
        reverse.setCreatorId(currentUserId);
        reverse.setUpdaterId(currentUserId);

        return new BiRecordPair(forward, reverse);
    }

    /**
     * 创建同意申请的双向记录对（仅申请记录）
     * 【原有方法，保持不变】
     */
    public static BiRecordPair createFromAgreedApply(BizFriendApply agreedApply) {
        return createFromAgreedApply(agreedApply, UserContext.getUserId());
    }

    /**
     * 从申请拒绝创建单向记录（只需更新原记录）
     * 【原有方法，保持不变】
     */
    public static BizFriendApply createFromRejectedApply(BizFriendApply originalApply, Long currentUserId) {
        LocalDateTime now = LocalDateTime.now();
        BizFriendApply rejected = new BizFriendApply();
        BeanUtils.copyProperties(originalApply, rejected);
        rejected.setStatus(2);
        rejected.setUpdatedTime(now);
        rejected.setUpdaterId(currentUserId);
        return rejected;
    }

    /**
     * 创建拒绝申请的单向记录
     * 【原有方法，保持不变】
     */
    public static BizFriendApply createFromRejectedApply(BizFriendApply originalApply) {
        return createFromRejectedApply(originalApply, UserContext.getUserId());
    }

    /**
     * 验证两条记录的关键字段是否一致
     * 【原有方法，保持不变】
     */
    public static boolean validateConsistency(BiRecordPair pair) {
        BizFriendApply forward = pair.getForward();
        BizFriendApply reverse = pair.getReverse();

        boolean consistent = true;

        if (!forward.getStatus().equals(reverse.getStatus())) {
            log.error("双向记录状态不一致: forward.status={}, reverse.status={}",
                    forward.getStatus(), reverse.getStatus());
            consistent = false;
        }

        if (forward.getUpdatedTime() != null && reverse.getUpdatedTime() != null) {
            if (!forward.getUpdatedTime().equals(reverse.getUpdatedTime())) {
                log.error("双向记录更新时间不一致: forward.updateTime={}, reverse.updateTime={}",
                        forward.getUpdatedTime(), reverse.getUpdatedTime());
                consistent = false;
            }
        }

        if (forward.getUpdaterId() != null && reverse.getUpdaterId() != null) {
            if (!forward.getUpdaterId().equals(reverse.getUpdaterId())) {
                log.error("双向记录更新人不一致: forward.updateId={}, reverse.updateId={}",
                        forward.getUpdaterId(), reverse.getUpdaterId());
                consistent = false;
            }
        }

        return consistent;
    }

    // ==================== 新增方法（包含好友表双向记录） ====================

    /**
     * 从已同意的申请创建完整双向记录对（包含申请记录 + 好友表记录）
     *
     * 好友表关系说明：
     * - forwardFriend: friend_id = 对方用户ID, creator_id = 当前用户ID（当前用户的好友列表中有对方）
     * - reverseFriend: friend_id = 当前用户ID, creator_id = 对方用户ID（对方的好友列表中有当前用户）
     *
     * @param agreedApply      已同意状态的申请记录
     * @param currentUserId    当前操作人ID（同意申请的人）
     * @param currentUser      当前用户信息（用于获取 signature 等）
     * @param targetUser       对方用户信息（用于获取 signature 等）
     * @param defaultCategory  默认分类ID
     * @return 完整双向记录对
     */
    public static BiRecordPair createFullBiRecord(BizFriendApply agreedApply, Long currentUserId, BizUser currentUser, BizUser targetUser, Long defaultCategory, String remark) {
        return createFullBiRecord(agreedApply, currentUserId, currentUser, targetUser, defaultCategory, defaultCategory, remark);
    }

    public static BiRecordPair createFullBiRecord(BizFriendApply agreedApply, Long currentUserId, BizUser currentUser, BizUser targetUser,
                                                 Long currentUserDefaultCategory, Long targetUserDefaultCategory, String remark) {

        LocalDateTime now = LocalDateTime.now();

        // ========== 1. 申请记录（双向） ==========
        // 正向记录：更新原申请记录
        BizFriendApply forward = new BizFriendApply();
        BeanUtils.copyProperties(agreedApply, forward);
        forward.setStatus(1);
        forward.setUpdatedTime(now);
        forward.setUpdaterId(currentUserId);

        // 反向记录：新建对方的好友申请记录
        BizFriendApply reverse = new BizFriendApply();
        reverse.setUserId(agreedApply.getFriendId());   // 当前用户
        reverse.setFriendId(agreedApply.getUserId());   // 对方用户
        reverse.setStatus(1);
        reverse.setApplyRemark(agreedApply.getApplyRemark());
        reverse.setNickname(remark);
        reverse.setSource(agreedApply.getSource());
        reverse.setCreatedTime(now);
        reverse.setUpdatedTime(now);
        reverse.setCreatorId(currentUserId);
        reverse.setUpdaterId(currentUserId);

        // ========== 2. 好友表记录（双向） ==========
        // friend_id = 好友的ID，creator_id = 自己的ID

        BizFriend forwardFriend = new BizFriend();
        forwardFriend.setFriendID(targetUser.getUserId());      // friend_id = 对方用户ID
        forwardFriend.setCategoryId(currentUserDefaultCategory);
        forwardFriend.setFriendName(targetUser.getUserName());  // 对方的用户名
        forwardFriend.setNickname(remark);                         // 备注
        forwardFriend.setSignature(targetUser.getUserSignature()); // signature 与对方 user_signature 保持一致
        forwardFriend.setNotDisturb(0);
        forwardFriend.setIsTop(0);
        forwardFriend.setCreatedTime(now);
        forwardFriend.setUpdatedTime(now);
        forwardFriend.setCreatorId(currentUserId);               // creator_id = 当前用户ID
        forwardFriend.setUpdaterId(currentUserId);

        // reverseFriend: 对方用户的好友记录（friend_id = 当前用户ID, creator_id = 对方用户ID）
        BizFriend reverseFriend = new BizFriend();
        reverseFriend.setFriendID(currentUser.getUserId());      // friend_id = 当前用户ID
        reverseFriend.setCategoryId(targetUserDefaultCategory);
        reverseFriend.setFriendName(currentUser.getUserName());  // 当前用户的用户名
        reverseFriend.setNickname(forward.getNickname());                         // 备注同原申请里一致
        reverseFriend.setSignature(currentUser.getUserSignature()); // signature 与当前用户 user_signature 保持一致
        reverseFriend.setNotDisturb(0);
        reverseFriend.setIsTop(0);
        reverseFriend.setCreatedTime(now);
        reverseFriend.setUpdatedTime(now);
        reverseFriend.setCreatorId(targetUser.getUserId());      // creator_id = 对方用户ID
        reverseFriend.setUpdaterId(targetUser.getUserId());

        return new BiRecordPair(forward, reverse)
                .setFriendRecords(forwardFriend, reverseFriend);
    }

    public static void processDelete(BizFriend forwardFriend, BizFriend reverseFriend,
                                     BizFriendApply forwardApply, BizFriendApply reverseApply,
                                     FriendMapper friendMapper, FriendMapper applyMapper,
                                     Long operatorId) {
        // 硬删除好友表
        if (forwardFriend != null) friendMapper.deleteFriend(forwardFriend.getCreatorId(), forwardFriend.getFriendID());
        if (reverseFriend != null) friendMapper.deleteFriend(reverseFriend.getCreatorId(), reverseFriend.getFriendID());

        // 软删除申请表
        LocalDateTime now = LocalDateTime.now();
        if (forwardApply != null) {
            forwardApply.setStatus(3);
            forwardApply.setUpdatedTime(now);
            forwardApply.setUpdaterId(operatorId);
            applyMapper.updateApply(forwardApply);
        }
        if (reverseApply != null) {
            reverseApply.setStatus(3);
            reverseApply.setUpdatedTime(now);
            reverseApply.setUpdaterId(operatorId);
            applyMapper.updateApply(reverseApply);
        }
    }
}