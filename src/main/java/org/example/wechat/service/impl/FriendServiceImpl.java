package org.example.wechat.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.exception.BusinessException;
import org.example.wechat.common.util.BiRecordUtils;
import org.example.wechat.common.util.UserContext;
import org.example.wechat.common.util.WsSessionManager;
import org.example.wechat.dao.BlacklistMapper;
import org.example.wechat.dao.CategoryMapper;
import org.example.wechat.dao.FriendMapper;
import org.example.wechat.dao.UserMapper;
import org.example.wechat.pojo.dto.FriendApplyDTO;
import org.example.wechat.pojo.entity.BizBlacklist;
import org.example.wechat.pojo.entity.BizCategory;
import org.example.wechat.pojo.entity.BizFriend;
import org.example.wechat.pojo.entity.BizUser;
import org.example.wechat.pojo.entity.BizFriendApply;
import org.example.wechat.pojo.vo.FriendApplyVO;
import org.example.wechat.pojo.vo.FriendDetailVO;
import org.example.wechat.pojo.vo.FriendListVO;
import org.example.wechat.service.FriendService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class FriendServiceImpl implements FriendService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private BlacklistMapper blacklistMapper;

    @Autowired
    private WsSessionManager wsSessionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String DEFAULT_CATEGORY_NAME = "我的好友";
    private static final String FRIEND_APPLY_LOCK_PREFIX = "lock:friend:apply:";

    @Override
    public List<FriendListVO> listFriends() {
        Long userId = UserContext.getUserId();
        List<FriendListVO> friendList = friendMapper.getfriendList(userId);
        if (friendList == null || friendList.isEmpty()) {
            return Collections.emptyList();
        }
        return friendList;
    }

    @Override
    public void applyFriend(FriendApplyDTO friendApplyDTO){

        Long userId = UserContext.getUserId();
        Long friendId = friendApplyDTO.getFriendId();
        if (friendId == null || userId.equals(friendId)) {
            throw BusinessException.badRequest("好友ID不可为用户ID或NULL");
        }

        String lockKey = buildFriendApplyLockKey(userId, friendId);
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 5, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            throw BusinessException.conflict("好友申请正在处理中，请勿重复提交");
        }
        try {
            BizUser targetUser = userMapper.getByUserId(friendId);
            if (targetUser == null) {
                throw BusinessException.notFound("用户不存在");
            }
            if (targetUser.getIsDeleted() == 1) {
                throw BusinessException.forbidden("对方账号已被注销或禁用");
            }
            if (friendMapper.checkIsFriend(userId, friendId) > 0) {
                throw BusinessException.conflict("已经是好友关系");
            }
            BizBlacklist blacklist = blacklistMapper.findByUser(friendId, userId);
            if (blacklist != null) {
                throw new BusinessException("对方已将您拉黑，无法添加好友");
            }
            BizFriendApply pendingApply = friendMapper.getPendingApply(userId, friendId);
            if (pendingApply != null) {
                throw BusinessException.conflict("已发送过好友申请...");
            }
            BizFriendApply reverseApply = friendMapper.getPendingApply(friendId, userId);
            if (reverseApply != null) {
                throw BusinessException.conflict("对方已向你发送好友申请...");
            }

            BizFriendApply bizFriendApply = new BizFriendApply();
            BeanUtils.copyProperties(friendApplyDTO, bizFriendApply);
            bizFriendApply.setUserId(userId);
            bizFriendApply.setStatus(0);
            friendMapper.addFriendApply(bizFriendApply);
            sendFriendNotice(friendId, "friend_apply", userId, "收到新的好友申请");
        } finally {
            Object currentValue = redisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(String.valueOf(currentValue))) {
                redisTemplate.delete(lockKey);
            }
        }

    }

    @Override
    @Transactional
    public void handleApply(Long userFriendId,Integer status,String remark){
        Long userId = UserContext.getUserId();
        BizFriendApply receiveApply = friendMapper.getByApplyId(userFriendId);
        if (receiveApply == null) {
            throw BusinessException.notFound("申请记录不存在");
        }
        if (!receiveApply.getFriendId().equals(userId)) {
            throw BusinessException.forbidden("无权处理此申请");
        }
        if (receiveApply.getStatus() != 0) {
            throw BusinessException.conflict("该申请已被处理");
        }
        if (status == 1) {  // 同意

            BizUser currentUser = userMapper.getByUserId(userId);
            BizUser targetUser = userMapper.getByUserId(receiveApply.getUserId());
            if (currentUser == null || targetUser == null) {
                throw BusinessException.notFound("用户信息不存在");
            }
            Long currentDefaultCategory = getOrCreateDefaultCategory(userId);
            Long targetDefaultCategory = getOrCreateDefaultCategory(receiveApply.getUserId());
            BiRecordUtils.BiRecordPair pair = BiRecordUtils.createFullBiRecord(
                    receiveApply, userId, currentUser, targetUser, currentDefaultCategory, targetDefaultCategory, remark);
            BizFriendApply forwardRecord = pair.getForward();
            BizFriend forwardFriend = pair.getForwardFriend();
            BizFriend reverseFriend = pair.getReverseFriend();
            friendMapper.updateApply(forwardRecord);
            friendMapper.addFriend(forwardFriend);
            friendMapper.addFriend(reverseFriend);
            sendFriendNotice(receiveApply.getUserId(), "friend_apply_result", userId, "好友申请已通过");
            sendFriendNotice(receiveApply.getUserId(), "friend_added", userId, "你们已经成为好友");

        } else if (status == 2) {  // 拒绝
            BizFriendApply rejectedRecord = BiRecordUtils.createFromRejectedApply(receiveApply);
            friendMapper.updateApply(rejectedRecord);
            sendFriendNotice(receiveApply.getUserId(), "friend_apply_result", userId, "好友申请已拒绝");
        } else {
            throw BusinessException.badRequest("无效的操作状态");
        }
    }

    @Override
    @Transactional
    public List<FriendApplyVO> listFriendApplies(){
        Long userId = UserContext.getUserId();
        List<FriendApplyVO> result = friendMapper.getApplyByUser(userId);
        return result != null ? result : new ArrayList<>();
    }

    @Override
    @Transactional
    public FriendDetailVO getFriendProfile(Long friendId){

        Long userId = UserContext.getUserId();
        BizUser bizUser = userMapper.getByUserId(friendId);
        if(bizUser == null){
            throw BusinessException.notFound("该用户不存在");
        }
        if (friendMapper.checkIsFriend(userId, friendId) <= 0) {
            throw BusinessException.forbidden("TA与你还不是好友");
        }
        FriendDetailVO friendDetailVO =  new FriendDetailVO();
        BeanUtils.copyProperties(bizUser,friendDetailVO);
        BizFriend bizFriend = friendMapper.getByFriendId(friendId,userId);
        BeanUtils.copyProperties(bizFriend,friendDetailVO);
        friendDetailVO.setChatId(friendMapper.getChatId(userId,friendId));
        return friendDetailVO;

    }

    @Override
    @Transactional
    public void moveCategory(String categoryName, Long friendId){
        if(categoryName == null || friendId == null)
            throw BusinessException.badRequest("移动分组名与ID不可为空");
        Long userId = UserContext.getUserId();
        if(!categoryMapper.existsByName(userId, categoryName))
            throw BusinessException.notFound("分组不存在：" + categoryName);
        if(friendMapper.checkIsFriend(userId, friendId) <= 0) {
            throw BusinessException.notFound("好友关系不存在");
        }
        int rows = friendMapper.moveCategory(userId,friendId,categoryName);;
        if (rows != 1) {
            throw BusinessException.conflict("移动失败，请重试");
        }
    }

    @Override
    public void updateRemark(String remark, Long friendId){
        if(remark == null || friendId == null)
            throw BusinessException.badRequest("新备注与好友ID不可为空");
        Long userId = UserContext.getUserId();
        int rows = friendMapper.updateRemark(remark,friendId,userId);
        if (rows != 1) {
            throw BusinessException.conflict("修改失败，请重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFriend(Long friendId){
        if(friendId == null)
            throw BusinessException.badRequest("好友ID不可为空");
        Long userId = UserContext.getUserId();
        if (userId.equals(friendId)) {
            throw BusinessException.badRequest("不能删除自己");
        }
        if(friendMapper.checkIsFriend(userId,friendId) <= 0)
            throw BusinessException.notFound("好友关系不存在");
        try {
            BizFriend forwardFriend = friendMapper.getByFriendId(userId, friendId);
            BizFriend reverseFriend = friendMapper.getByFriendId(friendId, userId);
            BizFriendApply forwardApply=friendMapper.getByRelation(userId, friendId);
            BizFriendApply reverseApply=friendMapper.getByRelation(friendId, userId);
            BiRecordUtils.processDelete(forwardFriend, reverseFriend, forwardApply, reverseApply, friendMapper, friendMapper, userId);
        } catch (Exception e) {
            log.error("删除好友失败: userId={}, friendId={}, error={}", userId, friendId, e.getMessage(), e);
            throw e;
        }
    }



    @Override
    @Transactional
    public void updateFriendSettings(org.example.wechat.pojo.dto.FriendSettingsDTO dto) {
        Long userId = UserContext.getUserId();
        if (dto.getFriendId() == null) {
            throw BusinessException.badRequest("好友ID不可为空");
        }
        if (friendMapper.checkIsFriend(userId, dto.getFriendId()) <= 0) {
            throw BusinessException.notFound("好友关系不存在");
        }
        // 如果传了 categoryId，校验分组是否属于当前用户
        if (dto.getCategoryId() != null) {
            BizCategory category = categoryMapper.getById(dto.getCategoryId());
            if (category == null || !category.getUserId().equals(userId)) {
                throw BusinessException.notFound("分组不存在");
            }
        }
        int rows = friendMapper.updateFriendSettings(dto, userId);
        if (rows != 1) {
            throw BusinessException.conflict("修改好友设置失败");
        }
    }

    private Long getOrCreateDefaultCategory(Long userId) {
        List<BizCategory> categories = categoryMapper.getByUserId(userId);
        if (categories != null) {
            for (BizCategory category : categories) {
                if (DEFAULT_CATEGORY_NAME.equals(category.getCategoryName())) {
                    return category.getCategoryId();
                }
            }
        }
        BizCategory category = new BizCategory();
        category.setUserId(userId);
        category.setCategoryName(DEFAULT_CATEGORY_NAME);
        categoryMapper.insertCategory(category);
        return category.getCategoryId();
    }

    private String buildFriendApplyLockKey(Long userId, Long friendId) {
        long minId = Math.min(userId, friendId);
        long maxId = Math.max(userId, friendId);
        return FRIEND_APPLY_LOCK_PREFIX + minId + ":" + maxId;
    }

    private void sendFriendNotice(Long toUserId, String type, Long operatorId, String message) {
        if (toUserId == null) {
            return;
        }
        try {
            Map<String, Object> notice = new HashMap<>();
            notice.put("type", type);
            notice.put("operatorId", operatorId);
            notice.put("message", message);
            wsSessionManager.sendToUser(toUserId, objectMapper.writeValueAsString(notice));
        } catch (JsonProcessingException e) {
            log.error("好友通知推送序列化失败: toUserId={}, type={}, error={}", toUserId, type, e.getMessage());
        }
    }
}
