package org.example.wechat.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.constants.GroupRoleConstants;
import org.example.wechat.common.constants.GroupPermitConstants;
import org.example.wechat.common.constants.GroupTypeConstants;
import org.example.wechat.common.exception.BusinessException;
import org.example.wechat.common.util.BizPermitChecker;
import org.example.wechat.common.util.UserContext;
import org.example.wechat.common.util.WsSessionManager;
import org.example.wechat.config.AppConfig;
import org.example.wechat.dao.*;
import org.example.wechat.pojo.dto.GroupAddDTO;
import org.example.wechat.pojo.dto.GroupUpdateDTO;
import org.example.wechat.pojo.entity.*;
import org.example.wechat.pojo.vo.*;
import org.example.wechat.service.GroupService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GroupServiceImpl implements GroupService {

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private GroupMapper groupMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WsSessionManager wsSessionManager;

    @Autowired
    private GroupUserMapper groupUserMapper;

    @Autowired
    private BizPermitChecker permissionChecker;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BlacklistMapper blacklistMapper;

    @Autowired
    private RoleMapper roleMapper;

    private static final int DEFAULT_MAX_NUM = 200;
    private static final int MAX_GROUP_NAME_LENGTH = 20;

    @Override
    public List<GroupSearchVO> searchGroups(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return groupMapper.selectByKey("%" + keyword + "%");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupListVO addGroup(GroupAddDTO groupAddDTO) {
        BizGroup bizGroup = new BizGroup();
        BeanUtils.copyProperties(groupAddDTO, bizGroup);
        if (bizGroup.getGroupAvatar() == null || bizGroup.getGroupAvatar().trim().isEmpty()) {
            bizGroup.setGroupAvatar(appConfig.getDefaultGroupPath());
        }
        bizGroup.setMaxNum(DEFAULT_MAX_NUM);
        bizGroup.setGroupType(GroupTypeConstants.GROUPTYPE_PUBLIC);
        Long userId = UserContext.getUserId();
        LocalDateTime now = LocalDateTime.now();
        bizGroup.setOwnerId(userId);
        List<Long> uniqueMemberIds = groupAddDTO.getMemberIds() == null
                ? Collections.emptyList()
                : groupAddDTO.getMemberIds().stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(userId))
                .distinct()
                .collect(Collectors.toList());
        int memCount = 1 + uniqueMemberIds.size();
        if (memCount > DEFAULT_MAX_NUM) {
            throw BusinessException.badRequest("创建群时成员数不能超过 " + DEFAULT_MAX_NUM + " 人");
        }
        BizUser owner = userMapper.getByUserIdAny(userId);
        if (owner == null || (owner.getIsDeleted() != null && owner.getIsDeleted() == 1)) {
            throw BusinessException.badRequest("当前用户不存在或已注销，创建群失败");
        }
        List<BizUser> groupUsers = new ArrayList<>();
        groupUsers.add(owner);
        for (Long memberId : uniqueMemberIds) {
            BizUser member = userMapper.getByUserIdAny(memberId);
            if (member == null) {
                throw BusinessException.badRequest("用户 " + memberId + " 不存在，创建群失败");
            }
            if (member.getIsDeleted() != null && member.getIsDeleted() == 1) {
                throw BusinessException.badRequest("用户 " + memberId + " 已注销，无法加入群聊");
            }
            BizBlacklist blacklist = blacklistMapper.findByUser(memberId, userId);
            if (blacklist != null) {
                throw BusinessException.forbidden("用户 " + memberId + " 已将您拉黑，无法加入群聊");
            }
            groupUsers.add(member);
        }
        if (bizGroup.getGroupName() == null || bizGroup.getGroupName().trim().isEmpty()) {
            bizGroup.setGroupName(buildDefaultGroupName(groupUsers, memCount));
        } else {
            bizGroup.setGroupName(bizGroup.getGroupName().trim());
        }
        int result = groupMapper.insert(bizGroup);
        if (result <= 0) {
            throw BusinessException.conflict("创建群组失败");
        }
        List<BizGroupUser> membersToInsert = new ArrayList<>();
        // 群主
        membersToInsert.add(BizGroupUser.builder()
                .groupId(bizGroup.getGroupId())
                .userId(userId)
                .bizRoleId(GroupRoleConstants.ROLE_OWNER)
                .applyType(0)
                .checkResult(1)
                .notDisturb(0)
                .isTop(0)
                .isDeleted(0)
                .createdTime(now)
                .updatedTime(now)
                .creatorId(userId)
                .updaterId(userId)
                .build());
        // 初始成员
        uniqueMemberIds.forEach(memberId -> membersToInsert.add(BizGroupUser.builder()
                .groupId(bizGroup.getGroupId())
                .userId(memberId)
                .bizRoleId(GroupRoleConstants.ROLE_MEMBER)
                .applyType(1)
                .inviteBy(userId)
                .checkResult(1)
                .notDisturb(0)
                .isTop(0)
                .isDeleted(0)
                .createdTime(now)
                .updatedTime(now)
                .creatorId(userId)
                .updaterId(userId)
                .build()));
        if (!membersToInsert.isEmpty()) {
            groupUserMapper.batchInsert(membersToInsert);
        }
        GroupListVO vo = new GroupListVO();
        vo.setGroupId(bizGroup.getGroupId());
        vo.setGroupName(bizGroup.getGroupName());
        vo.setGroupAvatar(bizGroup.getGroupAvatar());
        return vo;
    }

    @Override
    @Transactional
    public void setMemberRole(Long groupId, Long userId, Long roleId) {
        Long currentUserId = UserContext.getUserId();
        BizGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("群聊不存在");
        }
        BizGroupUser currentUser = groupUserMapper.selectByMemId(groupId, currentUserId);
        if (group.getOwnerId().equals(userId) || GroupRoleConstants.ROLE_OWNER.equals(roleId)) {
            throw BusinessException.forbidden("不能修改群主角色");
        }
        if (currentUser == null || currentUser.getIsDeleted() == 1 || currentUser.getCheckResult() != 1) {
            throw BusinessException.forbidden("您不在该群聊中");
        }
        if (!GroupRoleConstants.ROLE_OWNER.equals(currentUser.getBizRoleId())) {
            throw BusinessException.forbidden("非群主-无权限设置成员角色");
        }
        BizGroupUser targetUser = groupUserMapper.selectByMemId(groupId, userId);
        if (targetUser == null || targetUser.getIsDeleted() == 1) {
            throw BusinessException.notFound("目标用户不在群中");
        }
        groupUserMapper.updateMemberRole(groupId, userId, roleId, currentUserId);
    }

    @Override
    public List<GroupMemberVO> getGroupMembers(Long groupId) {
        assertActiveGroupMember(groupId, UserContext.getUserId());
        BizGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("群聊不存在");
        }
        List<GroupMemberVO> members = groupUserMapper.selectGroupMembersWithRole(groupId);
        if (CollectionUtils.isEmpty(members)) {
            return Collections.emptyList();
        }
        List<Long> userIds = members.stream()
                .map(GroupMemberVO::getUserId)
                .collect(Collectors.toList());
        List<BizUser> users = userMapper.selectBatchIds(userIds);
        Map<Long, BizUser> userMap = users.stream()
                .collect(Collectors.toMap(BizUser::getUserId, u -> u));
        for (GroupMemberVO member : members) {
            BizUser user = userMap.get(member.getUserId());
            if (user != null) {
                member.setUserAvatar(user.getUserAvatar());
                if (member.getNickname() == null || member.getNickname().isEmpty()) {
                    member.setNickname(user.getNickname());
                }
            }
        }
        return members;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMembers(Long groupId, List<Long> memberIds) {
        Long currentUserId = UserContext.getUserId();
        BizGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("群聊不存在");
        }
        permissionChecker.checkPermission(groupId, currentUserId, GroupPermitConstants.MEMBER_KICK, "无权限移除成员");
        for (Long userId : memberIds) {
            permissionChecker.checkCanOperateMember(groupId, currentUserId, userId, "无权限移除该成员");
            groupUserMapper.deleteByMem(groupId, userId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dismissGroup(Long groupId) {
        Long currentUserId = UserContext.getUserId();
        BizGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("群聊不存在");
        }
        if (!group.getOwnerId().equals(currentUserId)) {
            throw BusinessException.forbidden("只有群主可以解散群聊");
        }

        // 删除前先保存成员列表
        List<BizGroupUser> members = groupUserMapper.selectByGroupId(groupId);
        groupUserMapper.deleteByGroupId(groupId);
        int result = groupMapper.deleteById(groupId);
        if (result <= 0) {
            throw BusinessException.conflict("解散群聊失败");
        }

        // 推送：通知所有成员（含群主自己可选）群已解散
        try {
            Map<String, Object> notice = new HashMap<>();
            notice.put("type", "group_dismissed");
            notice.put("groupId", groupId);
            notice.put("groupName", group.getGroupName());
            notice.put("message", "群聊 " + group.getGroupName() + " 已被解散");
            String json = objectMapper.writeValueAsString(notice);
            for (BizGroupUser member : members) {
                if (!member.getUserId().equals(currentUserId)) {
                    wsSessionManager.sendToUser(member.getUserId(), json);
                }
            }
        } catch (JsonProcessingException e) {
            log.error("解散群聊推送序列化失败: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class) // ← 补上事务注解
    public void transferOwner(Long groupId, Long newOwnerId) {
        Long currentUserId = UserContext.getUserId();
        BizGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("群聊不存在");
        }
        if (!group.getOwnerId().equals(currentUserId)) {
            throw BusinessException.forbidden("只有群主可以转让群聊");
        }
        if (currentUserId.equals(newOwnerId)) {
            throw BusinessException.badRequest("不能将群主转让给自己");
        }
        BizUser newOwnerUser = userMapper.getByUserIdAny(newOwnerId);
        if (newOwnerUser == null || (newOwnerUser.getIsDeleted() != null && newOwnerUser.getIsDeleted() == 1)) {
            throw BusinessException.badRequest("新群主不存在或已注销");
        }
        BizGroupUser newOwner = groupUserMapper.selectByMemId(groupId, newOwnerId);
        if (newOwner == null || newOwner.getIsDeleted() == 1) {
            throw BusinessException.notFound("新群主不在群聊中或已退出");
        }
        group.setOwnerId(newOwnerId);
        int updateGroupResult = groupMapper.updateById(group);
        if (updateGroupResult <= 0) {
            throw BusinessException.conflict("更新群主失败");
        }
        int oldOwnerUpdate = groupUserMapper.updateMemberRole(groupId, currentUserId, GroupRoleConstants.ROLE_MEMBER, currentUserId);
        if (oldOwnerUpdate <= 0) {
            throw BusinessException.conflict("更新原群主角色失败");
        }
        int newOwnerUpdate = groupUserMapper.updateMemberRole(groupId, newOwnerId, GroupRoleConstants.ROLE_OWNER, currentUserId);
        if (newOwnerUpdate <= 0) {
            throw BusinessException.conflict("更新新群主角色失败");
        }

        // 推送：通知群内所有成员群主已变更
        try {
            BizUser oldOwnerUser = userMapper.getByUserId(currentUserId);
            BizUser newOwnerInfo = userMapper.getByUserId(newOwnerId);
            String oldName = oldOwnerUser != null ? oldOwnerUser.getNickname() : "原群主";
            String newName = newOwnerInfo != null ? newOwnerInfo.getNickname() : "新群主";

            Map<String, Object> notice = new HashMap<>();
            notice.put("type", "group_owner_transferred");
            notice.put("groupId", groupId);
            notice.put("oldOwnerId", currentUserId);
            notice.put("newOwnerId", newOwnerId);
            notice.put("newOwnerName", newName);
            notice.put("message", oldName + " 已将群主转让给 " + newName);
            String json = objectMapper.writeValueAsString(notice);

            List<BizGroupUser> members = groupUserMapper.selectByGroupId(groupId);
            for (BizGroupUser member : members) {
                wsSessionManager.sendToUser(member.getUserId(), json);
            }
        } catch (JsonProcessingException e) {
            log.error("转让群主推送序列化失败: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exitGroup(Long groupId) {
        Long currentUserId = UserContext.getUserId();
        BizGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("群聊不存在");
        }
        BizGroupUser currentMember = groupUserMapper.selectByMemId(groupId, currentUserId);
        if (currentMember == null || currentMember.getIsDeleted() == 1) {
            throw BusinessException.forbidden("您不在该群聊中");
        }
        if (group.getOwnerId().equals(currentUserId)) {
            int memberCount = groupUserMapper.countByGroupId(groupId); // ← 改为 countByGroupId
            if (memberCount <= 1) {
                dismissGroup(groupId);
                return;
            } else {
                throw BusinessException.badRequest("群主不能直接退出群聊，请先转让群主或解散群聊");
            }
        }

        // 删除推送前先拿成员列表
        List<BizGroupUser> members = groupUserMapper.selectByGroupId(groupId);

        int result = groupUserMapper.deleteByMem(groupId, currentUserId);
        if (result <= 0) {
            throw BusinessException.conflict("退出群聊失败");
        }

        // 推送：通知群内其他成员有人退群
        BizUser exitUser = userMapper.getByUserId(currentUserId);
        String nickname = (exitUser != null && exitUser.getNickname() != null)
                ? exitUser.getNickname() : "未知用户";
        try {
            Map<String, Object> notice = new HashMap<>();
            notice.put("type", "group_member_exit");
            notice.put("groupId", groupId);
            notice.put("userId", currentUserId);
            notice.put("nickname", nickname);
            notice.put("message", nickname + " 已退出群聊");
            String json = objectMapper.writeValueAsString(notice);
            for (BizGroupUser member : members) {
                if (!member.getUserId().equals(currentUserId)) {
                    wsSessionManager.sendToUser(member.getUserId(), json);
                }
            }
        } catch (JsonProcessingException e) {
            log.error("退出群聊推送序列化失败: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inviteMembers(Long groupId, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw BusinessException.badRequest("请选择要邀请的成员");
        }
        // 对群记录加行锁，串行化“查人数 + 插入成员”流程，避免并发邀请导致人数超限。
        BizGroup group = groupMapper.selectByIdForUpdate(groupId);
        if (group == null) {
            throw BusinessException.notFound("群聊不存在");
        }
        Long curUserId = UserContext.getUserId();
        LocalDateTime now = LocalDateTime.now();
        permissionChecker.checkPermission(
                groupId,
                curUserId,
                GroupPermitConstants.MEMBER_INVITE,
                "无权限邀请成员"
        );
        List<Long> uniqueMemberIds = memberIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(id -> !id.equals(curUserId))
                .collect(Collectors.toList());
        if (uniqueMemberIds.isEmpty()) {
            throw BusinessException.badRequest("没有有效的邀请成员");
        }
        int currentCount = groupUserMapper.countByGroupId(groupId);
        if (currentCount + uniqueMemberIds.size() > group.getMaxNum()) {
            throw BusinessException.badRequest("邀请人数超过群成员上限（最多" + group.getMaxNum() + "人）");
        }
        for (Long userId : uniqueMemberIds) {
            BizUser targetUser = userMapper.getByUserIdAny(userId);
            if (targetUser == null) {
                throw BusinessException.badRequest("用户 " + userId + " 不存在，邀请失败");
            }
            if (targetUser.getIsDeleted() != null && targetUser.getIsDeleted() == 1) {
                throw BusinessException.badRequest("用户 " + userId + " 已注销，无法邀请");
            }
            BizGroupUser activeMember = groupUserMapper.selectByMemId(groupId, userId);
            if (activeMember != null) {
                throw BusinessException.badRequest("用户 " + userId + " 已在群中，邀请失败");
            }
            BizBlacklist blacklist = blacklistMapper.findByUser(userId, curUserId);
            if (blacklist != null) {
                throw BusinessException.forbidden("用户 " + userId + " 已将您拉黑，无法邀请进群");
            }
        }
        for (Long userId : uniqueMemberIds) {
            groupUserMapper.clearMemApply(groupId, userId);
        }
        List<BizGroupUser> invitedUsers = uniqueMemberIds.stream()
                .map(userId -> BizGroupUser.builder()
                        .groupId(groupId)
                        .userId(userId)
                        .nickname(null)
                        .bizRoleId(GroupRoleConstants.ROLE_MEMBER)
                        .notDisturb(0)
                        .isTop(0)
                        .isDeleted(0)
                        .applyType(1)
                        .inviteBy(curUserId)
                        .applyRemark(null)
                        .checkBy(null)
                        .checkResult(1)
                        .createdTime(now)
                        .updatedTime(now)
                        .creatorId(curUserId)
                        .updaterId(curUserId)
                        .build())
                .toList();
        groupUserMapper.batchInsert(invitedUsers);
        try {
            Map<String, Object> notice = new HashMap<>();
            notice.put("type", "group_invited");
            notice.put("groupId", groupId);
            notice.put("groupName", group.getGroupName());
            notice.put("inviterId", curUserId);
            notice.put("message", "你已被邀请加入群聊 " + group.getGroupName());
            String json = objectMapper.writeValueAsString(notice);
            for (Long userId : uniqueMemberIds) {
                wsSessionManager.sendToUser(userId, json);
            }
        } catch (JsonProcessingException e) {
            log.error("群邀请推送序列化失败: groupId={}, error={}", groupId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void updateGroup(@Valid GroupUpdateDTO dto) {
        BizGroup existing = groupMapper.selectById(dto.getGroupId());
        if (existing == null) {
            throw BusinessException.notFound("群聊不存在");
        }
        long userId = UserContext.getUserId();
        if (!permissionChecker.isOwnerOrAdmin(dto.getGroupId(), userId)) {
            throw BusinessException.forbidden("只有群主或管理员才能修改群信息");
        }
        if (dto.getGroupName() != null && !dto.getGroupName().equals(existing.getGroupName())) {
            existing.setGroupName(dto.getGroupName());
        }
        if (dto.getGroupAvatar() != null) {
            existing.setGroupAvatar(dto.getGroupAvatar());
        }
        if (dto.getDescription() != null) {
            existing.setDescription(dto.getDescription());
        }
        if (dto.getGroupNotice() != null) {
            existing.setGroupNotice(dto.getGroupNotice());
        }
        int result = groupMapper.updateById(existing);
        if (result <= 0) {
            throw BusinessException.conflict("修改群信息失败");
        }
        try {
            Map<String, Object> notice = new HashMap<>();
            notice.put("type", "group_settings_update");
            notice.put("groupId", dto.getGroupId());
            notice.put("groupName", existing.getGroupName());
            notice.put("groupAvatar", existing.getGroupAvatar());
            notice.put("description", existing.getDescription());
            notice.put("groupNotice", existing.getGroupNotice());
            notice.put("action", "settings_changed");
            notice.put("message", "群设置已更新");
            String json = objectMapper.writeValueAsString(notice);
            List<BizGroupUser> members = groupUserMapper.selectByGroupId(dto.getGroupId());
            for (BizGroupUser member : members) {
                if (member.getIsDeleted() == 0) {
                    wsSessionManager.sendToUser(member.getUserId(), json);
                }
            }
        } catch (JsonProcessingException e) {
            log.error("群设置更新推送序列化失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public GroupDetailVO getGroupDetail(Long groupId) {
        assertActiveGroupMember(groupId, UserContext.getUserId());
        BizGroup bizGroup = groupMapper.selectById(groupId);
        if (bizGroup == null) {
            throw BusinessException.notFound("群聊不存在");
        }
        GroupDetailVO vo = new GroupDetailVO();
        BeanUtils.copyProperties(bizGroup, vo);
        vo.setMemberCount(groupUserMapper.countByGroupId(groupId));
        List<GroupMemberVO> members = getGroupMembers(groupId);
        vo.setMembers(members);
        return vo;
    }

    @Override
    @Transactional
    public void applyJoinGroup(Long groupId, String applyRemark) {
        Long currentUserId = UserContext.getUserId();
        BizGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("群聊不存在");
        }
        BizGroupUser activeMember = groupUserMapper.selectByMemId(groupId, currentUserId);
        if (activeMember != null) {
            throw BusinessException.conflict("您已经是群成员");
        }
        BizGroupUser pendingApply = groupUserMapper.selectPendingApply(groupId, currentUserId);
        if (pendingApply != null) {
            throw BusinessException.conflict("您已有待处理的入群申请，请勿重复提交");
        }
        if (groupUserMapper.countByGroupId(groupId) >= group.getMaxNum()) {
            throw BusinessException.badRequest("群成员已达上限，无法申请加入");
        }
        LocalDateTime now = LocalDateTime.now();
        BizGroupUser applyRecord = BizGroupUser.builder()
                .groupId(groupId)
                .userId(currentUserId)
                .nickname(UserContext.getUsername())
                .bizRoleId(GroupRoleConstants.ROLE_MEMBER)
                .notDisturb(0)
                .isTop(0)
                .isDeleted(0)
                .applyType(0)           // 用户申请
                .inviteBy(null)
                .applyRemark(applyRemark)
                .checkBy(null)
                .checkResult(2)         // 待处理
                .createdTime(now)
                .updatedTime(now)
                .creatorId(currentUserId)
                .updaterId(currentUserId)
                .build();
        int result = groupUserMapper.insertApply(applyRecord);
        if (result <= 0) {
            throw BusinessException.conflict("提交申请失败");
        }

        try {
            Map<String, Object> notice = new HashMap<>();
            notice.put("type", "group_apply");
            notice.put("groupId", groupId);
            notice.put("groupName", group.getGroupName());
            notice.put("applyUserId", currentUserId);
            notice.put("message", "收到新的入群申请");
            String json = objectMapper.writeValueAsString(notice);
            List<BizGroupUser> members = groupUserMapper.selectByGroupId(groupId);
            for (BizGroupUser member : members) {
                if (GroupRoleConstants.ROLE_OWNER.equals(member.getBizRoleId())
                        || GroupRoleConstants.ROLE_ADMIN.equals(member.getBizRoleId())) {
                    wsSessionManager.sendToUser(member.getUserId(), json);
                }
            }
        } catch (JsonProcessingException e) {
            log.error("入群申请推送序列化失败: groupId={}, userId={}, error={}", groupId, currentUserId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void auditApply(Long applyId, Integer checkResult) {
        if (checkResult == null || (checkResult != 0 && checkResult != 1)) {
            throw BusinessException.forbidden("非法审核结果，请明确同意/拒绝");
        }
        Long currentUserId = UserContext.getUserId();
        BizGroupUser apply = groupUserMapper.selectByGroupMemId(applyId);
        if (apply == null) {
            throw BusinessException.notFound("申请记录不存在");
        }
        if (apply.getCheckResult() != null && apply.getCheckResult() != 2) {
            throw BusinessException.conflict("该申请已审核过");
        }
        BizGroup group = groupMapper.selectById(apply.getGroupId());
        if (group == null) {
            throw BusinessException.notFound("群聊不存在");
        }
        if (!permissionChecker.isOwnerOrAdmin(group.getGroupId(), currentUserId)) {
            throw BusinessException.forbidden("只有群主或管理员才有审核权限");
        }
        if (checkResult == 1) {
            // 同意入群会新增正式成员，因此对群记录加行锁，避免多个审核并发通过后突破人数上限。
            group = groupMapper.selectByIdForUpdate(apply.getGroupId());
            if (group == null) {
                throw BusinessException.notFound("群聊不存在");
            }
            BizUser applyUser = userMapper.getByUserIdAny(apply.getUserId());
            if (applyUser == null || (applyUser.getIsDeleted() != null && applyUser.getIsDeleted() == 1)) {
                throw BusinessException.badRequest("申请人不存在或已注销，无法通过申请");
            }
            if (groupUserMapper.countByGroupId(group.getGroupId()) >= group.getMaxNum()) {
                throw BusinessException.badRequest("群成员已达上限，无法通过申请");
            }
        }
        int rows = groupUserMapper.updateCheckResult(applyId, checkResult, currentUserId);
        if (rows == 0) {
            throw BusinessException.conflict("该申请已被处理");
        }

        try {
            Map<String, Object> notice = new HashMap<>();
            notice.put("type", "group_apply_result");
            notice.put("groupId", group.getGroupId());
            notice.put("groupName", group.getGroupName());
            notice.put("checkResult", checkResult);
            notice.put("message", checkResult == 1 ? "入群申请已通过" : "入群申请已拒绝");
            wsSessionManager.sendToUser(apply.getUserId(), objectMapper.writeValueAsString(notice));
        } catch (JsonProcessingException e) {
            log.error("入群审核结果推送序列化失败: applyId={}, error={}", applyId, e.getMessage());
        }
    }

    @Override
    public List<GroupApplyVO> getUserApplies() {
        Long currentUserId = UserContext.getUserId();
        return groupUserMapper.selectApplyByUserId(currentUserId);
    }

    @Override
    public List<PendingApplyVO> getPendingApplies(Long groupId) {
        Long currentUserId = UserContext.getUserId();
        if (!permissionChecker.isOwnerOrAdmin(groupId, currentUserId)) {
            throw BusinessException.forbidden("无权限查看");
        }
        return groupUserMapper.selectPendingByGroupId(groupId);
    }

    @Override
    public List<UserGroupVO> getUserGroups() {
        Long currentUserId = UserContext.getUserId();
        List<BizGroupUser> userGroups = groupUserMapper.selectByUserId(currentUserId);
        if (CollectionUtils.isEmpty(userGroups)) {
            return Collections.emptyList();
        }
        List<UserGroupVO> result = new ArrayList<>();
        for (BizGroupUser gu : userGroups) {
            BizGroup group = groupMapper.selectById(gu.getGroupId());
            if (group == null) {
                continue;
            }
            UserGroupVO vo = new UserGroupVO();
            vo.setGroupId(group.getGroupId());
            vo.setGroupName(group.getGroupName());
            vo.setGroupAvatar(group.getGroupAvatar());
            vo.setRoleId(gu.getBizRoleId().intValue());
            vo.setNickname(gu.getNickname());
            vo.setNotDisturb(gu.getNotDisturb());
            vo.setIsTop(gu.getIsTop());
            if (GroupRoleConstants.ROLE_OWNER.equals(gu.getBizRoleId())) {
                vo.setRole("OWNER");
            } else if (GroupRoleConstants.ROLE_ADMIN.equals(gu.getBizRoleId())) {
                vo.setRole("ADMIN");
            } else {
                vo.setRole("MEMBER");
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public void updateMemberSettings(org.example.wechat.pojo.dto.GroupMemberSettingsDTO dto) {
        Long userId = UserContext.getUserId();
        if (dto.getGroupId() == null) {
            throw BusinessException.badRequest("群ID不可为空");
        }
        BizGroup group = groupMapper.selectById(dto.getGroupId());
        if (group == null) {
            throw BusinessException.notFound("群聊不存在");
        }
        BizGroupUser member = groupUserMapper.selectByMemId(dto.getGroupId(), userId);
        if (member == null || member.getIsDeleted() == 1) {
            throw BusinessException.forbidden("您不在该群聊中");
        }
        int rows = groupUserMapper.updateMemberSettings(dto, userId);
        if (rows != 1) {
            throw BusinessException.conflict("修改个人设置失败");
        }
    }

    @Override
    public List<RoleVO> getAllRoles() {
        List<BizRole> roles = roleMapper.selectAll();
        if (CollectionUtils.isEmpty(roles)) {
            return Collections.emptyList();
        }
        return roles.stream().map(role -> {
            RoleVO vo = new RoleVO();
            BeanUtils.copyProperties(role, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    private void assertActiveGroupMember(Long groupId, Long userId) {
        if (groupId == null) {
            throw BusinessException.badRequest("群ID不可为空");
        }
        BizGroupUser member = groupUserMapper.selectByMemId(groupId, userId);
        if (member == null || member.getIsDeleted() == 1) {
            throw BusinessException.forbidden("您不在该群聊中");
        }
    }

    private String buildDefaultGroupName(List<BizUser> users, int memberCount) {
        String name = users.stream()
                .filter(Objects::nonNull)
                .limit(3)
                .map(this::resolveDisplayName)
                .collect(Collectors.joining("、"));
        if (name.isBlank()) {
            name = "群聊";
        }
        if (memberCount > 3) {
            name = name + "等……";
        }
        return truncateGroupName(name);
    }

    private String resolveDisplayName(BizUser user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname().trim();
        }
        if (user.getUserName() != null && !user.getUserName().isBlank()) {
            return user.getUserName().trim();
        }
        return "用户";
    }

    private String truncateGroupName(String name) {
        if (name.length() <= MAX_GROUP_NAME_LENGTH) {
            return name;
        }
        return name.substring(0, MAX_GROUP_NAME_LENGTH);
    }
}
