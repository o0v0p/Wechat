package org.example.wechat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.Result;
import org.example.wechat.common.util.OssUtils;
import org.example.wechat.pojo.vo.InfoHistoryVO;
import org.example.wechat.pojo.vo.SessionListVO;
import org.example.wechat.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/chat")
@Slf4j
@Tag(name = "聊天相关接口")

public class ChatController {
    @Autowired
    private ChatService chatService;

    @Autowired
    private OssUtils ossUtils;

    @PostMapping("/send")
    @Operation(summary = "发送消息", description = "发送消息接口，infoStatus：0-正常；2-引用；3-发送失败。撤回/删除请调用 /chat/status")
    public Result<InfoHistoryVO> OnSendInfo(@RequestParam String context,
                                            @RequestParam Long receiveId,
                                            @RequestParam Integer InfoType,
                                            @RequestParam Integer otherType,
                                            @RequestParam(required = false) Integer infoStatus,
                                            @RequestParam(required = false, name = "InfoStatus") Integer InfoStatus,
                                            @RequestParam(required = false) Integer status) {
        Integer finalStatus = chooseInfoStatus(infoStatus, InfoStatus, status, 0);
        log.info("发送消息 - receiveId: {}, InfoType: {}, otherType: {}, infoStatus: {}", receiveId, InfoType, otherType, finalStatus);
        InfoHistoryVO infoHistoryVO = chatService.OnSendInfo(context, receiveId, InfoType, otherType, finalStatus);
        if (infoHistoryVO.getInfoStatus() != null && infoHistoryVO.getInfoStatus() == 3) {
            return Result.success("消息发送失败，已保存失败标记", infoHistoryVO);
        }
        return Result.success("消息发送成功！", infoHistoryVO);
    }

    @PutMapping("/status")
    @Operation(summary = "修改消息状态", description = "用于撤回、引用标记、发送失败标记或删除。infoStatus：1-撤回；2-引用；3-发送失败；4-删除")
    public Result<InfoHistoryVO> updateInfoStatus(@RequestParam Long infoId,
                                                  @RequestParam(required = false) Integer infoStatus,
                                                  @RequestParam(required = false) Integer status) {
        Integer finalStatus = chooseInfoStatus(infoStatus, null, status, null);
        log.info("修改消息状态 - infoId: {}, infoStatus: {}", infoId, finalStatus);
        InfoHistoryVO infoHistoryVO = chatService.updateInfoStatus(infoId, finalStatus);
        if (infoHistoryVO.getInfoStatus() != null && infoHistoryVO.getInfoStatus() == 4) {
            return Result.success("消息删除成功", infoHistoryVO);
        }
        return Result.success("消息状态修改成功", infoHistoryVO);
    }

    @DeleteMapping("/message")
    @Operation(summary = "删除自己发送的消息", description = "等价于 /chat/status?infoId=xxx&infoStatus=4；删除后发送方刷新历史不可见，接收方仍可见")
    public Result<InfoHistoryVO> deleteMessage(@RequestParam Long infoId) {
        log.info("删除消息 - infoId: {}", infoId);
        InfoHistoryVO infoHistoryVO = chatService.updateInfoStatus(infoId, 4);
        return Result.success("消息删除成功", infoHistoryVO);
    }


    @GetMapping("/history")
    @Operation(summary = "获取历史消息")
    public Result<List<InfoHistoryVO>> OnGetHistory(
            @RequestParam Long otherId,
            @RequestParam(required = false) Long lastId,
            @RequestParam Integer sessionType) {  // 添加 sessionType 参数
        log.info("获取历史消息 - otherId: {}, lastId: {}, sessionType: {}", otherId, lastId, sessionType);
        List<InfoHistoryVO> historyVOS = chatService.OnGetHistory(otherId, lastId,sessionType);
        return Result.success("获取历史消息成功", historyVOS);
    }

    @GetMapping("/sessions")
    @Operation(summary = "获取会话列表", description = "获取所有会话列表（单聊+群聊）")
    public Result<List<SessionListVO>> getSessionList() {
        log.info("获取会话列表");
        List<SessionListVO> sessionList = chatService.getSessionList();
        return Result.success("获取会话列表成功", sessionList);
    }

    @PutMapping("/read")
    @Operation(summary = "标记消息已读")
    public Result<Void> markMessagesAsRead(
            @RequestParam Long targetId,
            @RequestParam Integer sessionType) {

        log.info("标记消息已读 - targetId: {}, sessionType: {}", targetId, sessionType);
        chatService.markMessagesAsRead(targetId, sessionType);
        return Result.success("标记已读成功");
    }

    @PostMapping("/upload")
    @Operation(summary = "上传多媒体文件")
    public Result<String> uploadFile(@RequestParam("file") MultipartFile file) {
        log.info("上传多媒体文件: {}", file);
        String url = ossUtils.uploadFile(file, "Info");
        return Result.success("上传多媒体文件成功", url);
    }


    private Integer chooseInfoStatus(Integer infoStatus, Integer InfoStatus, Integer status, Integer defaultValue) {
        if (infoStatus != null) {
            return infoStatus;
        }
        if (InfoStatus != null) {
            return InfoStatus;
        }
        if (status != null) {
            return status;
        }
        return defaultValue;
    }
}
