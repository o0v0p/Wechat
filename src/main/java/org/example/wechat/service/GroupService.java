package org.example.wechat.service;

import jakarta.validation.Valid;
import org.example.wechat.pojo.dto.GroupAddDTO;
import org.example.wechat.pojo.dto.GroupUpdateDTO;
import org.example.wechat.pojo.vo.*;

import java.util.List;

public interface GroupService {
    GroupListVO addGroup(GroupAddDTO groupAddDTO);

    List<GroupMemberVO> getGroupMembers(Long groupId);

    void removeMembers(Long groupId, List<Long> memberIds);

    void dismissGroup(Long groupId);

    void transferOwner(Long groupId, Long ownerId);

    void exitGroup(Long groupId);

    void inviteMembers(Long groupId, List<Long> memberIds);

    void updateGroup(@Valid GroupUpdateDTO dto);

    GroupDetailVO getGroupDetail(Long groupId);

    void applyJoinGroup(Long groupId, String applyRemark);

    void auditApply(Long applyId, Integer checkResult);

    List<GroupApplyVO> getUserApplies();

    List<PendingApplyVO> getPendingApplies(Long groupId);

    List<GroupSearchVO> searchGroups(String keyword);

    List<UserGroupVO> getUserGroups();

    void setMemberRole(Long groupId, Long userId, Long roleId);

    List<RoleVO> getAllRoles();

    void updateMemberSettings(org.example.wechat.pojo.dto.GroupMemberSettingsDTO dto);

}
