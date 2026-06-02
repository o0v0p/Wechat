package org.example.wechat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.Result;
import org.example.wechat.common.util.UserContext;
import org.example.wechat.dao.BizInfoMapper;
import org.example.wechat.pojo.entity.BizInfo;
import org.example.wechat.pojo.vo.InfoHistoryVO;
import org.example.wechat.pojo.vo.SessionListVO;
import org.example.wechat.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat")
@Slf4j
@Tag(name = "聊天相关接口")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ChatController {
    @Autowired
    private ChatService chatService;

    @PostMapping("/send")
    @Operation(summary = "发送消息", description = "发送消息接口")
    public Result<InfoHistoryVO> OnSendInfo(@RequestParam String content,
                                            @RequestParam Long receiveId,
                                            @RequestParam Integer InfoType,
                                            @RequestParam Integer otherType) {
        log.info("发送消息");
        InfoHistoryVO infoHistoryVO = chatService.OnSendInfo(content, receiveId, InfoType, otherType);
        return Result.success("消息发送成功！", infoHistoryVO);
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
}
