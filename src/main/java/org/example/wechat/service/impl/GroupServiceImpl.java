package org.example.wechat.service.impl;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.constants.GroupRoleConstants;
import org.example.wechat.common.constants.GroupPermitConstants;
import org.example.wechat.common.constants.GroupTypeConstants;
import org.example.wechat.common.exception.BusinessException;
import org.example.wechat.common.util.BizPermitChecker;
import org.example.wechat.common.util.UserContext;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GroupServiceImpl implements GroupService {

    @Autowired
    private GroupMapper groupMapper;

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

    @Override
    public List<GroupSearchVO> searchGroups(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return groupMapper.selectByKey("%" + keyword + "%");
    }

    @Override
    public GroupListVO OnAddGroup(GroupAddDTO groupAddDTO) {
        BizGroup bizGroup = new BizGroup();
        BeanUtils.copyProperties(groupAddDTO, bizGroup);
        bizGroup.setMaxNum(DEFAULT_MAX_NUM);
        bizGroup.setGroupType(GroupTypeConstants.GROUPTYPE_PUBLIC);
        Long userId = UserContext.getUserId();
        bizGroup.setOwnerId(userId);
        int MemCount = 1 + (int) groupAddDTO.getMemberIds().stream()
                .filter(id -> !id.equals(userId)).distinct().count();
        if (MemCount > DEFAULT_MAX_NUM) {
            throw BusinessException.badRequest("创建群时成员数不能超过 " + DEFAULT_MAX_NUM + " 人");
        }
        int result = groupMapper.insert(bizGroup);
        if (result <= 0) {
            throw BusinessException.conflict("创建群组失败");
        }
        List<BizGroupUser> membersToInsert = new ArrayList<>();
        // 群主
        membersToInsert.add(BizGroupUser.builder()
                .groupId(bizGroup.getGroupId()).userId(userId).bizRoleId(GroupRoleConstants.ROLE_OWNER)
                .applyType(0).checkResult(1).notDisturb(0).isTop(0).isDeleted(0)
                .build());
        // 成员
        groupAddDTO.getMemberIds().stream()
                .filter(id -> !id.equals(userId)).distinct()
                .forEach(memberId -> membersToInsert.add(BizGroupUser.builder()
                        .groupId(bizGroup.getGroupId()).userId(memberId).bizRoleId(GroupRoleConstants.ROLE_MEMBER)
                        .applyType(1).inviteBy(userId).checkResult(1).notDisturb(0).isTop(0).isDeleted(0)
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
        if (!GroupRoleConstants.ROLE_OWNER.equals(currentUser.getBizRoleId())) {
            throw BusinessException.forbidden("非群主-无权限设置成员角色");
        }
        BizGroupUser targetUser = groupUserMapper.selectByMemId(groupId, userId);
        if (targetUser == null || targetUser.getIsDeleted() == 1) {
            throw BusinessException.notFound("目标用户不在群中");
        }
        groupUserMapper.updateMemberRole(groupId, userId, roleId);
    }

    @Override
    public List<GroupMemberVO> getGroupMembers(Long groupId) {
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
        if (!permissionChecker.isOwner(groupId, currentUserId)) {
            throw BusinessException.forbidden("只有群主可以解散群聊");
        }
        groupUserMapper.deleteByGroupId(groupId);
        int result = groupMapper.deleteById(groupId);
        if (result <= 0) {
            throw BusinessException.conflict("解散群聊失败");
        }
    }

    @Override
    public void transferOwner(Long groupId, Long ownerId) {
        Long currentUserId = UserContext.getUserId();
        BizGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("群聊不存在");
        }
        if (!group.getOwnerId().equals(currentUserId)) {
            throw BusinessException.forbidden("只有群主可以转让群聊");
        }
        BizGroupUser newOwner = groupUserMapper.selectByMemId(groupId, ownerId);
        if (newOwner == null || newOwner.getIsDeleted() == 1) {
            throw BusinessException.notFound("新群主不在群聊中或已退出");
        }
        group.setOwnerId(ownerId);
        int updateGroupResult = groupMapper.updateById(group);
        if (updateGroupResult <= 0) {
            throw BusinessException.conflict("更新群主失败");
        }
        int oldOwnerUpdate = groupUserMapper.updateMemberRole(groupId, currentUserId, GroupRoleConstants.ROLE_MEMBER);
        if (oldOwnerUpdate <= 0) {
            throw BusinessException.conflict("更新原群主角色失败");
        }
        int newOwnerUpdate = groupUserMapper.updateMemberRole(groupId, ownerId, GroupRoleConstants.ROLE_OWNER);
        if (newOwnerUpdate <= 0) {
            throw BusinessException.conflict("更新新群主角色失败");
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
            int memberCount = groupUserMapper.countMem(groupId);
            if (memberCount <= 1) {
                dismissGroup(groupId);
                return;
            } else {
                throw BusinessException.badRequest("群主不能直接退出群聊，请先转让群主或解散群聊");
            }
        }
        int result = groupUserMapper.deleteByMem(groupId, currentUserId);
        if (result <= 0) {
            throw BusinessException.conflict("退出群聊失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inviteMembers(Long groupId, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw BusinessException.badRequest("请选择要邀请的成员");
        }
        BizGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("群聊不存在");
        }
        Long curUserId = UserContext.getUserId();
        permissionChecker.checkPermission(groupId, curUserId,
                GroupPermitConstants.MEMBER_INVITE, "无权限邀请成员");
        int currentCount = groupUserMapper.countMem(groupId);
        if (currentCount + memberIds.size() > group.getMaxNum()) {
            throw BusinessException.badRequest("邀请人数超过群成员上限（最多" + group.getMaxNum() + "人）");
        }
        List<Long> uniqueMemberIds = memberIds.stream()
                .distinct()
                .filter(id -> !id.equals(curUserId))
                .toList();
        if (uniqueMemberIds.isEmpty()) {
            throw BusinessException.badRequest("没有有效的邀请成员");
        }
        for (Long userId : uniqueMemberIds) {
            BizUser targetUser = userMapper.getByUserId(userId);
            if (targetUser == null) {
                throw BusinessException.badRequest("用户 " + userId + " 不存在，邀请失败");
            }
            BizGroupUser exist = groupUserMapper.selectByMemId(groupId, userId);
            if (exist != null && exist.getIsDeleted() == 0) {
                throw BusinessException.badRequest("用户 " + userId + " 已在群中，邀请失败");
            }
            BizBlacklist blacklist = blacklistMapper.findByUser(userId, curUserId);
            if (blacklist != null) {
                throw new BusinessException("该用户已将您拉黑，无法邀请进群");
            }
        }
        for (Long userId : uniqueMemberIds) {
            // 清理该用户对该群尚待处理的自主申请
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
                        .build())
                .toList();
        groupUserMapper.batchInsert(invitedUsers);
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
            throw BusinessException.forbidden("只有群主或管理员才有审核权限");
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
    }

    @Override
    public GroupDetailVO getGroupDetail(Long groupId) {
        BizGroup bizGroup = groupMapper.selectById(groupId);
        if (bizGroup == null) {
            throw BusinessException.notFound("群聊不存在");
        }
        GroupDetailVO vo = new GroupDetailVO();
        BeanUtils.copyProperties(bizGroup, vo);
        vo.setMemberCount(groupUserMapper.countMem(groupId));
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
        BizGroupUser existMember = groupUserMapper.selectByMemId(groupId, currentUserId);
        if (existMember != null && existMember.getIsDeleted() == 0 && existMember.getCheckResult() == 1) {
            throw BusinessException.conflict("您已有待处理的申请 / 已经是群成员");
        }
        LocalDateTime now = LocalDateTime.now();
        BizGroupUser applyRecord = BizGroupUser.builder()
                .groupId(groupId)
                .userId(currentUserId)
                .nickname(null)
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
    }

    @Override
    @Transactional
    public void auditApply(Long applyId, Integer checkResult) {
        if(checkResult != 0 || checkResult != 1) {
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
        groupUserMapper.updateCheckResult(applyId, checkResult, currentUserId);
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
}
