package org.example.wechat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.Result;
import org.example.wechat.pojo.dto.GroupAddDTO;
import org.example.wechat.pojo.dto.GroupMemberSettingsDTO;
import org.example.wechat.pojo.dto.GroupUpdateDTO;
import org.example.wechat.pojo.entity.BizRole;
import org.example.wechat.pojo.vo.*;
import org.example.wechat.service.GroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/group")
@Slf4j
@Tag(name = "群聊相关接口")

public class GroupController {

    @Autowired
    private GroupService groupService;

    @PostMapping("/add")
    @Operation(summary = "创建群聊",description = "创建群聊接口")
    public Result<GroupListVO> addGroup(@Valid @RequestBody GroupAddDTO groupAddDTO){
        log.info("创建群聊");
        GroupListVO groupListVO = groupService.addGroup(groupAddDTO);
        return Result.success("创建群聊成功",groupListVO);
    }

    @GetMapping("/detail")
    @Operation(summary = "获取群信息", description = "获取群信息接口")
    public Result<GroupDetailVO> getGroupDetail(@RequestParam Long groupId) {
        GroupDetailVO vo = groupService.getGroupDetail(groupId);
        return Result.success("获取群信息成功",vo);
    }

    @PutMapping("/update")
    @Operation(summary = "修改群信息", description = "修改群信息接口")
    public Result<Void> updateGroup(@Valid @RequestBody GroupUpdateDTO dto) {
        groupService.updateGroup(dto);
        return Result.success("修改成功", null);
    }

    @PostMapping("/invite")
    @Operation(summary = "邀请群成员", description = "邀请群成员接口")
    public Result<Void> inviteMembers(@RequestParam Long groupId, @RequestParam List<Long> memberIds) {
        groupService.inviteMembers(groupId,memberIds);
        return Result.success("邀请成功", null);
    }

    @DeleteMapping("/exit/{groupId}")
    @Operation(summary = "退出群聊", description = "退出群聊接口")
    public Result<Void> exitGroup(@PathVariable Long groupId) {
        groupService.exitGroup(groupId);
        return Result.success("已退出群聊", null);
    }

    @PutMapping("/transfer")
    @Operation(summary = "转让群主", description = "转让群主接口")
    public Result<Void> transferOwner(@RequestParam Long groupId,@RequestParam Long ownerId) {
        groupService.transferOwner(groupId,ownerId);
        return Result.success("转让成功", null);
    }

    @DeleteMapping("/dismiss/{groupId}")
    @Operation(summary = "解散群聊", description = "解散群聊接口")
    public Result<Void> dismissGroup(@PathVariable Long groupId) {
        groupService.dismissGroup(groupId);
        return Result.success("群聊已解散",null);
    }

    @DeleteMapping("/remove")
    @Operation(summary = "移除群成员", description = "移除群成员接口")
    public Result<Void> removeMembers(@RequestParam Long groupId, @RequestParam List<Long> memberIds) {
        groupService.removeMembers(groupId,memberIds);
        return Result.success("移除成功",null);
    }

    @GetMapping("/members")
    @Operation(summary = "获取群成员列表", description = "获取群成员列表（含角色信息）")
    public Result<List<GroupMemberVO>> getMembers(@RequestParam Long groupId) {
        List<GroupMemberVO> list = groupService.getGroupMembers(groupId);
        return Result.success("获取群成员列表成功", list);
    }

    @PostMapping("/apply")
    @Operation(summary = "申请加群", description = "申请加群接口")
    public Result<Void> applyJoinGroup(@RequestParam Long groupId, @RequestParam(required = false) String applyRemark) {
        log.info("申请加群，groupId: {}", groupId);
        groupService.applyJoinGroup(groupId, applyRemark);
        return Result.success("申请已提交", null);
    }

    @PostMapping("/audit")
    @Operation(summary = "审核申请", description = "审核群申请接口（仅群主）")
    public Result<Void> auditApply(@RequestParam Long applyId, @RequestParam Integer checkResult) {
        log.info("审核申请，applyId: {}, result: {}", applyId, checkResult);
        groupService.auditApply(applyId, checkResult);
        String msg = checkResult == 1 ? "已通过申请" : "已拒绝申请";
        return Result.success(msg, null);
    }

    @GetMapping("/myapply")
    @Operation(summary = "获取我的群申请记录", description = "获取当前用户的群申请记录")
    public Result<List<GroupApplyVO>> getUserApplies() {
        List<GroupApplyVO> list = groupService.getUserApplies();
        return Result.success("获取成功", list);
    }

    @GetMapping("/pending")
    @Operation(summary = "获取群待审核申请", description = "获取群待审核申请列表（仅群主）")
    public Result<List<PendingApplyVO>> getPendingApplies(@RequestParam Long groupId) {
        List<PendingApplyVO> list = groupService.getPendingApplies(groupId);
        return Result.success("获取成功", list);
    }

    @GetMapping("/search")
    @Operation(summary = "搜索群组", description = "根据关键词搜索群组")
    public Result<List<GroupSearchVO>> searchGroups(@RequestParam String keyword) {
        List<GroupSearchVO> list = groupService.searchGroups(keyword);
        return Result.success("搜索成功", list);
    }

    @GetMapping("/my-groups")
    @Operation(summary = "获取我的群组列表", description = "获取当前用户加入的所有群组及角色")
    public Result<List<UserGroupVO>> getUserGroups() {
        List<UserGroupVO> list = groupService.getUserGroups();
        return Result.success("获取成功", list);
    }

    @PutMapping("/member/settings")
    @Operation(summary = "设置群成员个人设置", description = "设置群昵称、免打扰、置顶")
    public Result<Void> updateMemberSettings(@RequestBody GroupMemberSettingsDTO dto) {
        groupService.updateMemberSettings(dto);
        return Result.success("修改成功", null);
    }

    @PutMapping("/member/role")
    @Operation(summary = "设置成员角色", description = "设置群成员角色（仅群主）")
    public Result<Void> setMemberRole(@RequestParam Long groupId, @RequestParam Long userId, @RequestParam Long roleId) {
        groupService.setMemberRole(groupId, userId, roleId);
        return Result.success("设置角色成功", null);
    }

    @GetMapping("/roles")
    @Operation(summary = "获取所有角色", description = "获取系统所有群角色")
    public Result<List<RoleVO>> getAllRoles() {
        List<RoleVO> roles = groupService.getAllRoles();
        return Result.success("获取成功", roles);
    }

}
