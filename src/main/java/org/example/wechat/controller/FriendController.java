package org.example.wechat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.Result;
import org.example.wechat.common.util.JwtUtils;
import org.example.wechat.pojo.dto.FriendApplyDTO;
import org.example.wechat.pojo.entity.BizCategory;
import org.example.wechat.pojo.vo.*;
import org.example.wechat.service.FriendService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;


@RestController
@RequestMapping("/friend")
@Slf4j
@Tag(name = "好友相关接口")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class FriendController {
    @Autowired
    private FriendService friendService;

    @GetMapping("/list")
    @Operation(summary = "获取好友列表", description = "获取好友列表接口")
    public Result<List<FriendListVO>> OnFriendList(){

        log.info("获取好友列表");
        List<FriendListVO> friendListVO = friendService.OnFriendList();
        return Result.success("获取好友列表成功",friendListVO);
    }

    @PostMapping("/apply")
    @Operation(summary = "申请添加好友", description = "申请添加好友接口")
    public Result<Void> OnAddFriend(@RequestBody FriendApplyDTO friendApplyDTO){

        log.info("申请添加好友");
        friendService.OnAddFriendApply(friendApplyDTO);
        return Result.success();
    }

    @PutMapping("/handleApply")
    @Operation(summary = "处理好友申请", description = "处理好友申请接口")
    public Result<Void> OnHandleFriend(@RequestParam Long userFriendId,@RequestParam Integer status,@RequestParam(required = false) String remark){

        log.info("处理好友申请");
        friendService.OnHandleApply(userFriendId,status,remark);
        if (status == 1) {
            return Result.success("已同意好友申请");
        } else{
            return Result.success("已拒绝好友申请");
        }
    }

    @GetMapping("/showApply")
    @Operation(summary = "获取好友申请列表", description = "获取好友申请列表接口")
    public Result<List<FriendApplyVO>> OnHandleFriend(){

        log.info("获取好友申请列表");
        List<FriendApplyVO> friendApplyVO = friendService.OnFriendApply();
        return Result.success("获取好友申请列表成功",friendApplyVO);
    }

    @GetMapping("/Profile")
    @Operation(summary = "获取好友详情信息", description = "获取好友详情信息接口")
    public Result<FriendDetailVO> OnFriendProfile(@RequestParam Long FriendId){

        log.info("获取好友详情信息");
        FriendDetailVO friendDetailVO = friendService.OnFriendProfile(FriendId);
        return Result.success("获取好友详情信息成功",friendDetailVO);

    }

    @PutMapping("/moveCategory")
    @Operation(summary = "移动好友分组", description = "移动好友分组")
    public Result<Void> OnMoveCategory(@RequestParam String categoryName,@RequestParam Long friendId){
        log.info("移动好友分组");
        friendService.OnMoveCategory(categoryName,friendId);
        return Result.success("移动好友分组成功");
    }

    @PutMapping("/remark")
    @Operation(summary = "修改好友备注", description = "修改好友备注")
    public Result<Void> OnUpdateRemark(@RequestParam String remark,@RequestParam Long friendId){
        log.info("修改好友备注");
        friendService.OnUpdateRemark(remark,friendId);
        return Result.success("修改好友备注成功");
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除好友", description = "删除好友")
    public Result<Void> OnUpdateRemark(@RequestParam Long friendId){
        log.info("删除好友");
        friendService.OnDeleteFriend(friendId);
        return Result.success("删除好友成功");
    }

}
