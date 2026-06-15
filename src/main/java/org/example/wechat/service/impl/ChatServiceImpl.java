package org.example.wechat.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.constants.InfoStatusConstants;
import org.example.wechat.common.constants.ReceiverTypeConstant;
import org.example.wechat.common.exception.BusinessException;
import org.example.wechat.common.util.UserContext;
import org.example.wechat.common.util.WsSessionManager;
import org.example.wechat.dao.*;
import org.example.wechat.pojo.entity.*;
import org.example.wechat.pojo.vo.FriendListVO;
import org.example.wechat.pojo.vo.InfoHistoryVO;
import org.example.wechat.pojo.vo.SessionListVO;
import org.example.wechat.service.ChatService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    @Autowired
    private BizInfoMapper bizInfoMapper;

    @Autowired
    private WsSessionManager sessionManager;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private GroupMapper groupMapper;

    @Autowired
    private BlacklistMapper blacklistMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GroupUserMapper groupUserMapper;

    /** 历史消息分页大小 */
    private static final Integer PAGE_SIZE = 20;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InfoHistoryVO OnSendInfo(String content, Long receiveId, Integer type, Integer otherType, Integer infoStatus) {
        Long userId = UserContext.getUserId();
        validateSendParams(content, receiveId, type, otherType, infoStatus);

        Integer finalStatus = infoStatus == null ? InfoStatusConstants.NORMAL : infoStatus;
        if (InfoStatusConstants.REVOKE.equals(finalStatus) || InfoStatusConstants.DELETE.equals(finalStatus)) {
            throw BusinessException.forbidden("撤回或删除消息请调用 /chat/status 接口");
        }

        try {
            // 前端主动传 3 时，表示只保存一条“发送失败”的消息记录，不推送给接收方。
            if (!InfoStatusConstants.SEND_FAIL.equals(finalStatus)) {
                checkSendPermission(userId, receiveId, otherType);
            }

            InfoHistoryVO vo = saveInfo(content, receiveId, type, otherType, finalStatus);
            pushMessage(vo, vo.getContext(), receiveId, otherType, finalStatus);
            return vo;
        } catch (BusinessException e) {
            // 关系被删除、对方注销、退群、群解散、黑名单等导致无法发送时，仍落库一条失败消息，刷新历史也能看见红色感叹号。
            log.warn("发送失败，保存失败消息标记 - senderId: {}, receiveId: {}, otherType: {}, reason: {}",
                    userId, receiveId, otherType, e.getMessage());
            InfoHistoryVO failVO = saveInfo(content, receiveId, type, otherType, InfoStatusConstants.SEND_FAIL);
            pushMessage(failVO, failVO.getContext(), receiveId, otherType, InfoStatusConstants.SEND_FAIL);
            return failVO;
        }
    }

    private void validateSendParams(String content, Long receiveId, Integer type, Integer otherType, Integer infoStatus) {
        if (content == null || content.isBlank()) {
            throw BusinessException.forbidden("消息内容不能为空");
        }
        if (content.length() > 2000) {
            throw BusinessException.forbidden("消息内容不能超过 2000 个字符");
        }
        if (receiveId == null) {
            throw BusinessException.forbidden("接收对象不能为空");
        }
        if (type == null) {
            throw BusinessException.forbidden("消息类型不能为空");
        }
        if (!Objects.equals(otherType, ReceiverTypeConstant.RECTYPE_PRIVATE)
                && !Objects.equals(otherType, ReceiverTypeConstant.RECTYPE_PUBLIC)) {
            throw BusinessException.forbidden("会话类型不正确");
        }
        if (infoStatus != null
                && !InfoStatusConstants.NORMAL.equals(infoStatus)
                && !InfoStatusConstants.QUOTE.equals(infoStatus)
                && !InfoStatusConstants.SEND_FAIL.equals(infoStatus)
                && !InfoStatusConstants.REVOKE.equals(infoStatus)
                && !InfoStatusConstants.DELETE.equals(infoStatus)) {
            throw BusinessException.forbidden("消息状态不正确");
        }
    }

    private void checkSendPermission(Long userId, Long receiveId, Integer otherType) {
        // 群聊
        if (ReceiverTypeConstant.RECTYPE_PUBLIC == otherType) {
            BizGroup group = groupMapper.selectById(receiveId);
            if (group == null) {
                throw BusinessException.notFound("群聊已解散/不存在");
            }
            BizGroupUser groupUser = groupUserMapper.selectByMemId(receiveId, userId);
            if (groupUser == null || groupUser.getIsDeleted() == 1) {
                throw BusinessException.forbidden("您不在该群聊中");
            }
        }
        // 私聊
        else if (ReceiverTypeConstant.RECTYPE_PRIVATE == otherType) {
            BizUser targetUser = userMapper.getByUserId(receiveId);
            if (targetUser == null) {
                throw BusinessException.notFound("对方用户不存在");
            }
            if (targetUser.getIsDeleted() == 1) {
                throw BusinessException.forbidden("对方账号已被注销或禁用");
            }
            int isFriend = friendMapper.checkIsFriend(userId, receiveId);
            if (isFriend <= 0 && !userId.equals(receiveId)) {
                throw BusinessException.forbidden("关系权限不足，无法发送消息");
            }
            BizBlacklist blacklist = blacklistMapper.findByUser(receiveId, userId);
            if (blacklist != null) {
                throw new BusinessException("对方已将您拉黑");
            }
        }
    }

    private InfoHistoryVO saveInfo(String content, Long receiveId, Integer type, Integer otherType, Integer infoStatus) {
        Long userId = UserContext.getUserId();
        String finalContent = normalizeQuoteContent(content, infoStatus);
        BizInfo bizInfo = BizInfo.builder()
                .context(finalContent)
                .senderId(userId)
                .receiverId(receiveId)
                .infoType(type)
                .infoStatus(infoStatus)
                .isRead(0)
                .receiverType(otherType)
                .build();
        bizInfoMapper.sendInfo(bizInfo);
        return buildInfoHistoryVO(bizInfo);
    }

    private InfoHistoryVO buildInfoHistoryVO(BizInfo bizInfo) {
        InfoHistoryVO vo = new InfoHistoryVO();
        BeanUtils.copyProperties(bizInfo, vo);
        BizUser sender = userMapper.getByUserId(bizInfo.getSenderId());
        fillSenderInfo(vo, sender);

        if (InfoStatusConstants.REVOKE.equals(vo.getInfoStatus())) {
            vo.setContext(vo.getSenderName() + " 撤回了一条消息");
        } else if (InfoStatusConstants.QUOTE.equals(vo.getInfoStatus())) {
            vo.setContext(normalizeQuoteContent(vo.getContext(), vo.getInfoStatus()));
        }
        return vo;
    }

    /**
     * 规范化引用消息 context，解决“引用消息的引用”把整段 JSON 当成 quote.context 展示的问题。
     * 前端仍可继续把引用结构放在 context 中，后端会把 quote.context 修正为可直接展示的纯文本。
     */
    private String normalizeQuoteContent(String content, Integer infoStatus) {
        if (!InfoStatusConstants.QUOTE.equals(infoStatus) || content == null || content.isBlank()) {
            return content;
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            if (!(root instanceof ObjectNode rootObj)) {
                return content;
            }
            JsonNode quoteNode = rootObj.get("quote");
            if (!(quoteNode instanceof ObjectNode quoteObj)) {
                return content;
            }

            // 优先用 quote.infoId 回查原消息，避免前端引用“引用消息”时把原 context 的 JSON 原样塞进去。
            Long quoteInfoId = readLong(quoteObj.get("infoId"));
            if (quoteInfoId != null) {
                BizInfo quoteInfo = bizInfoMapper.selectByInfoId(quoteInfoId);
                if (quoteInfo != null) {
                    quoteObj.put("context", extractDisplayText(quoteInfo.getContext(), quoteInfo.getInfoStatus()));
                    return objectMapper.writeValueAsString(rootObj);
                }
            }

            // 兼容前端已经传入 quote.context 的情况：如果它本身是 JSON，则只取 text，不再嵌套整段 JSON。
            JsonNode quoteContextNode = quoteObj.get("context");
            if (quoteContextNode != null && quoteContextNode.isTextual()) {
                quoteObj.put("context", extractDisplayText(quoteContextNode.asText(), null));
            }
            return objectMapper.writeValueAsString(rootObj);
        } catch (Exception e) {
            return content;
        }
    }

    private Long readLong(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String extractDisplayText(String rawContext, Integer infoStatus) {
        if (rawContext == null || rawContext.isBlank()) {
            return rawContext;
        }
        if (InfoStatusConstants.REVOKE.equals(infoStatus)) {
            return "消息已撤回";
        }
        try {
            JsonNode node = objectMapper.readTree(rawContext);
            JsonNode textNode = node.get("text");
            if (textNode != null && textNode.isTextual()) {
                return textNode.asText();
            }
            JsonNode contextNode = node.get("context");
            if (contextNode != null && contextNode.isTextual()) {
                return contextNode.asText();
            }
        } catch (Exception ignored) {
            // 普通文本不是 JSON，直接返回原文。
        }
        return rawContext;
    }

    private void fillSenderInfo(InfoHistoryVO vo, BizUser sender) {
        if (sender == null) {
            vo.setSenderName("未知用户");
            vo.setSenderAvatar(null);
            return;
        }
        String senderName = sender.getNickname();
        if (senderName == null || senderName.isBlank()) {
            senderName = sender.getUserName();
        }
        if (senderName == null || senderName.isBlank()) {
            senderName = "未知用户";
        }
        if (sender.getIsDeleted() != null && sender.getIsDeleted() == 1) {
            senderName = senderName + "（已注销）";
        }
        vo.setSenderName(senderName);
        vo.setSenderAvatar(sender.getUserAvatar());
    }

    private void pushMessage(InfoHistoryVO vo, String content, Long receiveId, Integer otherType, Integer infoStatus) {
        Long userId = UserContext.getUserId();
        try {
            Map<String, Object> wsMsg = new HashMap<>();
            wsMsg.put("type", "message");
            wsMsg.put("data", vo);
            String pushJson = objectMapper.writeValueAsString(wsMsg);

            // 接收方会话更新
            Map<String, Object> receiverSessionUpdate = new HashMap<>();
            receiverSessionUpdate.put("type", "session_update");
            receiverSessionUpdate.put("sessionType", otherType);
            receiverSessionUpdate.put("lastMessage", content);
            receiverSessionUpdate.put("lastTime", vo.getCreatedTime());
            receiverSessionUpdate.put("infoId", vo.getInfoId());
            receiverSessionUpdate.put("senderId", vo.getSenderId());
            receiverSessionUpdate.put("infoStatus", infoStatus);
            receiverSessionUpdate.put("targetId",
                    ReceiverTypeConstant.RECTYPE_PRIVATE == otherType ? userId : receiveId);

            // 发送方会话更新
            Map<String, Object> senderSessionUpdate = new HashMap<>();
            senderSessionUpdate.put("type", "session_update");
            senderSessionUpdate.put("sessionType", otherType);
            senderSessionUpdate.put("lastMessage", content);
            senderSessionUpdate.put("lastTime", vo.getCreatedTime());
            senderSessionUpdate.put("infoId", vo.getInfoId());
            senderSessionUpdate.put("senderId", vo.getSenderId());
            senderSessionUpdate.put("infoStatus", infoStatus);
            senderSessionUpdate.put("targetId", receiveId);

            String receiverSessionUpdateJson = objectMapper.writeValueAsString(receiverSessionUpdate);
            String senderSessionUpdateJson = objectMapper.writeValueAsString(senderSessionUpdate);

            // 发送失败消息只推给发送者自己，避免接收方看到“别人发送失败”的消息。
            if (InfoStatusConstants.SEND_FAIL.equals(infoStatus)) {
                sessionManager.sendToUser(userId, pushJson);
                sessionManager.sendToUser(userId, senderSessionUpdateJson);
                return;
            }

            if (ReceiverTypeConstant.RECTYPE_PRIVATE == otherType) {
                // 推给接收方
                sessionManager.sendToUser(receiveId, pushJson);
                sessionManager.sendToUser(receiveId, receiverSessionUpdateJson);

                // 也推给发送方自己，保证发送方聊天框和会话列表同步
                sessionManager.sendToUser(userId, pushJson);
                sessionManager.sendToUser(userId, senderSessionUpdateJson);

            } else if (ReceiverTypeConstant.RECTYPE_PUBLIC == otherType) {
                List<BizGroupUser> members = groupUserMapper.selectByGroupId(receiveId);
                for (BizGroupUser member : members) {
                    if (member.getIsDeleted() == 0) {
                        sessionManager.sendToUser(member.getUserId(), pushJson);
                        sessionManager.sendToUser(member.getUserId(), senderSessionUpdateJson);
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<InfoHistoryVO> OnGetHistory(Long otherId, Long lastId, Integer receiverType) {
        Long userId = UserContext.getUserId();
        LocalDateTime joinTime = null;
        if (ReceiverTypeConstant.RECTYPE_PUBLIC == receiverType) {
            BizGroupUser groupUser = groupUserMapper.selectByMemId(otherId, userId);
            if (groupUser == null || groupUser.getIsDeleted() == 1) {
                throw BusinessException.forbidden("您不在该群聊中");
            }
            joinTime = groupUser.getCreatedTime();
        }
        if (ReceiverTypeConstant.RECTYPE_PRIVATE == receiverType) {
            BizUser targetUser = userMapper.getByUserId(otherId);
            if (targetUser == null) {
                throw BusinessException.notFound("对方用户不存在");
            }
            int isFriend = friendMapper.checkIsFriend(userId, otherId);
            if (isFriend <= 0 && !userId.equals(otherId)) {
                throw BusinessException.forbidden("关系权限不足，无法查看历史消息");
            }
        }
        List<BizInfo> bizInfos = bizInfoMapper.getHistory(userId, otherId, lastId, PAGE_SIZE, receiverType, joinTime);
        List<InfoHistoryVO> vos = new ArrayList<>();
        for (BizInfo bizInfo : bizInfos) {
            vos.add(buildInfoHistoryVO(bizInfo));
        }
        return vos;
    }

    @Override
    public List<SessionListVO> getSessionList() {
        Long userId = UserContext.getUserId();

        List<SessionListVO> singleSessions = getSingleChatSessions(userId);
        List<SessionListVO> groupSessions  = getGroupChatSessions(userId);

        Map<String, SessionListVO> sessionMap = new LinkedHashMap<>();

        for (SessionListVO session : singleSessions) {
            String key = session.getSessionType() + "_" + session.getTargetId();
            sessionMap.putIfAbsent(key, session);
        }

        for (SessionListVO session : groupSessions) {
            String key = session.getSessionType() + "_" + session.getTargetId();
            sessionMap.putIfAbsent(key, session);
        }

        List<SessionListVO> allSessions = new ArrayList<>(sessionMap.values());

        allSessions.sort((a, b) -> {
            if (!a.getIsTop().equals(b.getIsTop())) {
                return b.getIsTop() - a.getIsTop();
            }
            if (a.getLastTime() == null && b.getLastTime() == null) return 0;
            if (a.getLastTime() == null) return 1;
            if (b.getLastTime() == null) return -1;
            return b.getLastTime().compareTo(a.getLastTime());
        });

        return allSessions;
    }

    private List<SessionListVO> getSingleChatSessions(Long userId) {
        List<FriendListVO> friends = friendMapper.getfriendList(userId);
        if (CollectionUtils.isEmpty(friends)) {
            return Collections.emptyList();
        }

        List<BizInfo> latestMessages = bizInfoMapper.getSessionLatestMsg(userId);
        Map<Long, BizInfo> latestMsgMap = new HashMap<>();

        if (!CollectionUtils.isEmpty(latestMessages)) {
            for (BizInfo msg : latestMessages) {
                Long otherId = msg.getSenderId().equals(userId)
                        ? msg.getReceiverId()
                        : msg.getSenderId();

                latestMsgMap.put(otherId, msg);
            }
        }
        List<SessionListVO> sessions = new ArrayList<>();
        Set<Long> addedFriendIds = new HashSet<>();
        for (FriendListVO friend : friends) {
            Long friendId = friend.getFriendID();
            if (friendId == null || !addedFriendIds.add(friendId)) {
                continue;
            }
            SessionListVO vo = new SessionListVO();
            vo.setTargetId(friendId);
            vo.setSessionType(ReceiverTypeConstant.RECTYPE_PRIVATE);
            vo.setSessionName(
                    friend.getNickname() != null && !friend.getNickname().isBlank()
                            ? friend.getNickname()
                            : friend.getFriendName()
            );
            vo.setSessionAvatar(friend.getAvatar());

            BizFriend friendSetting = friendMapper.getByFriendId(friendId, userId);
            if (friendSetting != null) {
                vo.setNotDisturb(friendSetting.getNotDisturb() != null ? friendSetting.getNotDisturb() : 0);
                vo.setIsTop(friendSetting.getIsTop() != null ? friendSetting.getIsTop() : 0);
            } else {
                vo.setNotDisturb(0);
                vo.setIsTop(0);
            }

            BizInfo latestMsg = latestMsgMap.get(friendId);
            if (latestMsg != null) {
                vo.setLastMessage(buildSessionLastMessage(latestMsg));
                vo.setLastSenderId(latestMsg.getSenderId());
                vo.setLastInfoStatus(latestMsg.getInfoStatus());
                vo.setLastTime(latestMsg.getCreatedTime());

                Integer unread = bizInfoMapper.countUnreadMessages(userId, friendId);
                vo.setUnreadCount(unread != null ? unread : 0);
            } else {
                vo.setLastMessage("[暂无历史消息]");
                vo.setLastSenderId(null);
                vo.setLastInfoStatus(null);
                vo.setLastTime(null);
                vo.setUnreadCount(0);
            }

            sessions.add(vo);
        }

        return sessions;
    }

    private List<SessionListVO> getGroupChatSessions(Long userId) {
        List<BizGroupUser> userGroups = groupUserMapper.selectByUserId(userId);
        if (CollectionUtils.isEmpty(userGroups)) {
            return Collections.emptyList();
        }
        List<SessionListVO> sessions = new ArrayList<>();
        Set<Long> addedGroupIds = new HashSet<>();
        for (BizGroupUser gu : userGroups) {
            if (gu.getGroupId() == null || !addedGroupIds.add(gu.getGroupId())) {
                continue;
            }
            BizGroup group = groupMapper.selectById(gu.getGroupId());
            if (group == null) {
                log.warn("群聊不存在，跳过该群会话: groupId={}, userId={}", gu.getGroupId(), userId);
                continue;
            }
            LocalDateTime joinTime = gu.getCreatedTime();
            BizInfo latestMsg = bizInfoMapper.getLatestGroupMessageForUser(group.getGroupId(), userId, joinTime);
            SessionListVO vo = new SessionListVO();
            vo.setTargetId(group.getGroupId());
            vo.setSessionType(ReceiverTypeConstant.RECTYPE_PUBLIC);
            vo.setSessionName(group.getGroupName());
            vo.setSessionAvatar(group.getGroupAvatar());
            vo.setNotDisturb(gu.getNotDisturb() != null ? gu.getNotDisturb() : 0);
            vo.setIsTop(gu.getIsTop() != null ? gu.getIsTop() : 0);

            if (latestMsg != null) {
                vo.setLastMessage(buildSessionLastMessage(latestMsg));
                vo.setLastSenderId(latestMsg.getSenderId());
                vo.setLastInfoStatus(latestMsg.getInfoStatus());
                vo.setLastTime(latestMsg.getCreatedTime());
                String key = userId + ":" + group.getGroupId();
                Long lastReadId = sessionManager.getGroupLastRead(key);
                int unread;
                if (lastReadId == null) {
                    unread = bizInfoMapper.countGroupMessages(group.getGroupId(), userId, joinTime);
                } else {
                    unread = bizInfoMapper.countGroupMessagesAfter(group.getGroupId(), userId, lastReadId, joinTime);
                }
                vo.setUnreadCount(unread);
            } else {
                vo.setLastMessage("[暂无历史消息]");
                vo.setLastSenderId(null);
                vo.setLastInfoStatus(null);
                vo.setLastTime(null);
                vo.setUnreadCount(0);
            }
            sessions.add(vo);
        }
        return sessions;
    }


    private String buildSessionLastMessage(BizInfo latestMsg) {
        if (latestMsg == null) {
            return "[暂无历史消息]";
        }
        if (InfoStatusConstants.REVOKE.equals(latestMsg.getInfoStatus())) {
            BizUser sender = userMapper.getByUserId(latestMsg.getSenderId());
            String senderName = "对方";
            if (sender != null) {
                senderName = sender.getNickname();
                if (senderName == null || senderName.isBlank()) {
                    senderName = sender.getUserName();
                }
                if (senderName == null || senderName.isBlank()) {
                    senderName = "对方";
                }
            }
            return senderName + " 撤回了一条消息";
        }
        if (InfoStatusConstants.QUOTE.equals(latestMsg.getInfoStatus())) {
            return extractDisplayText(latestMsg.getContext(), latestMsg.getInfoStatus());
        }
        return latestMsg.getContext();
    }

    @Override
    @Transactional
    public void markMessagesAsRead(Long targetId, Integer sessionType) {
        Long userId = UserContext.getUserId();
        if (ReceiverTypeConstant.RECTYPE_PRIVATE == sessionType) {
            bizInfoMapper.markMessagesAsRead(userId, targetId);
        } else if (ReceiverTypeConstant.RECTYPE_PUBLIC == sessionType) {
            BizGroupUser groupUser = groupUserMapper.selectByMemId(targetId, userId);
            if (groupUser == null || groupUser.getIsDeleted() == 1) {
                throw BusinessException.forbidden("您不在该群聊中");
            }
            BizInfo latestMsg = bizInfoMapper.getLatestGroupMessageForUser(targetId, userId, groupUser.getCreatedTime());
            if (latestMsg != null) {
                String key = userId + ":" + targetId;
                sessionManager.updateGroupLastRead(key, latestMsg.getInfoId());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InfoHistoryVO updateInfoStatus(Long infoId, Integer infoStatus) {
        Long userId = UserContext.getUserId();
        if (infoId == null) {
            throw BusinessException.forbidden("消息ID不能为空");
        }
        if (!InfoStatusConstants.REVOKE.equals(infoStatus)
                && !InfoStatusConstants.QUOTE.equals(infoStatus)
                && !InfoStatusConstants.SEND_FAIL.equals(infoStatus)
                && !InfoStatusConstants.DELETE.equals(infoStatus)) {
            throw BusinessException.forbidden("只允许标记撤回、引用、发送失败或删除");
        }
        BizInfo oldInfo = bizInfoMapper.selectByInfoId(infoId);
        if (oldInfo == null) {
            throw BusinessException.notFound("消息不存在");
        }
        if (!userId.equals(oldInfo.getSenderId())) {
            throw BusinessException.forbidden("只能修改自己发送的消息");
        }
        int rows = bizInfoMapper.updateInfoStatus(infoId, infoStatus, userId);
        if (rows <= 0) {
            throw BusinessException.forbidden("消息状态修改失败");
        }
        BizInfo newInfo = bizInfoMapper.selectByInfoId(infoId);
        InfoHistoryVO vo = buildInfoHistoryVO(newInfo);
        pushStatusUpdate(vo);
        return vo;
    }

    private void pushStatusUpdate(InfoHistoryVO vo) {
        try {
            String action = resolveStatusAction(vo.getInfoStatus());

            // 发送失败、删除状态只通知发送者自己；撤回状态必须通知单聊双方或群内所有成员，保证对方页面实时更新为灰字提示。
            if (InfoStatusConstants.SEND_FAIL.equals(vo.getInfoStatus())
                    || InfoStatusConstants.DELETE.equals(vo.getInfoStatus())) {
                pushStatusAndSessionRefresh(vo.getSenderId(), vo, action, vo.getReceiverId());
                return;
            }

            if (ReceiverTypeConstant.RECTYPE_PRIVATE == vo.getReceiverType()) {
                // 发送者视角：targetId 是对方
                pushStatusAndSessionRefresh(vo.getSenderId(), vo, action, vo.getReceiverId());
                // 接收者视角：targetId 是发送者
                if (!vo.getSenderId().equals(vo.getReceiverId())) {
                    pushStatusAndSessionRefresh(vo.getReceiverId(), vo, action, vo.getSenderId());
                }
            } else if (ReceiverTypeConstant.RECTYPE_PUBLIC == vo.getReceiverType()) {
                List<BizGroupUser> members = groupUserMapper.selectByGroupId(vo.getReceiverId());
                for (BizGroupUser member : members) {
                    if (member.getIsDeleted() == 0) {
                        // 群聊所有成员视角：targetId 都是群ID
                        pushStatusAndSessionRefresh(member.getUserId(), vo, action, vo.getReceiverId());
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.error("消息状态推送失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 同时推送：
     * 1. message_status_update：让当前聊天窗口按 infoId 立即更新该消息状态；
     * 2. session_refresh：让会话列表刷新，避免撤回的是最后一条消息时列表仍显示旧内容。
     */
    private void pushStatusAndSessionRefresh(Long userId, InfoHistoryVO vo, String action, Long targetId)
            throws JsonProcessingException {
        Map<String, Object> statusMsg = new HashMap<>();
        statusMsg.put("type", "message_status_update");
        statusMsg.put("action", action);
        statusMsg.put("infoId", vo.getInfoId());
        statusMsg.put("infoStatus", vo.getInfoStatus());
        statusMsg.put("senderId", vo.getSenderId());
        statusMsg.put("receiverId", vo.getReceiverId());
        statusMsg.put("receiverType", vo.getReceiverType());
        statusMsg.put("targetId", targetId);
        statusMsg.put("data", vo);
        sessionManager.sendToUser(userId, objectMapper.writeValueAsString(statusMsg));

        Map<String, Object> sessionRefresh = new HashMap<>();
        sessionRefresh.put("type", "session_refresh");
        sessionRefresh.put("action", action);
        sessionRefresh.put("infoId", vo.getInfoId());
        sessionRefresh.put("infoStatus", vo.getInfoStatus());
        sessionRefresh.put("senderId", vo.getSenderId());
        sessionRefresh.put("receiverId", vo.getReceiverId());
        sessionRefresh.put("sessionType", vo.getReceiverType());
        sessionRefresh.put("targetId", targetId);
        sessionRefresh.put("message", "消息状态已变化，请刷新当前会话和会话列表");
        sessionManager.sendToUser(userId, objectMapper.writeValueAsString(sessionRefresh));
    }

    private String resolveStatusAction(Integer infoStatus) {
        if (InfoStatusConstants.REVOKE.equals(infoStatus)) {
            return "revoke";
        }
        if (InfoStatusConstants.QUOTE.equals(infoStatus)) {
            return "quote";
        }
        if (InfoStatusConstants.SEND_FAIL.equals(infoStatus)) {
            return "send_fail";
        }
        if (InfoStatusConstants.DELETE.equals(infoStatus)) {
            return "delete";
        }
        return "status_update";
    }
}
