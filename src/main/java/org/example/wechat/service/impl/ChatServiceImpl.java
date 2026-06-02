package org.example.wechat.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.Result;
import org.example.wechat.common.constants.InfoStatusConstants;
import org.example.wechat.common.constants.InfoTypeConstants;
import org.example.wechat.common.constants.ReceiverTypeConstant;
import org.example.wechat.common.exception.BusinessException;
import org.example.wechat.common.util.UserContext;
import org.example.wechat.common.util.WsSessionManager;
import org.example.wechat.dao.*;
import org.example.wechat.pojo.entity.*;
import org.example.wechat.pojo.vo.InfoHistoryVO;
import org.example.wechat.pojo.vo.PushMessageVO;
import org.example.wechat.pojo.vo.SessionListVO;
import org.example.wechat.service.ChatService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public InfoHistoryVO OnSendInfo(String content, Long receiveId, Integer type, Integer otherType) {
        Long userId = UserContext.getUserId();
        if (content == null || content.isBlank()) {
            throw BusinessException.forbidden("消息内容不能为空");
        }
        if (content.length() > 2000) {
            throw BusinessException.forbidden("消息内容不能超过 2000 个字符");
        }
        // 群聊
        if (ReceiverTypeConstant.RECTYPE_PUBLIC == otherType) {
            BizGroup group = groupMapper.selectById(receiveId);
            if (group == null) {
                throw BusinessException.notFound("群聊已解散");
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
            if (userId.equals(receiveId)) {
                throw BusinessException.badRequest("不能给自己发送消息");
            }
            int isFriend = friendMapper.checkIsFriend(userId, receiveId);
            if (isFriend <= 0) {
                throw BusinessException.forbidden("您不是对方的好友，无法发送消息");
            }
            BizBlacklist blacklist = blacklistMapper.findByUser(receiveId, userId);
            if (blacklist != null) {
                throw new BusinessException("对方已将您拉黑");
            }
        }

        BizInfo bizInfo = BizInfo.builder()
                .context(content)
                .senderId(userId)
                .receiverId(receiveId)
                .infoType(type)
                .infoStatus(InfoStatusConstants.NORMAL)
                .isRead(0)
                .receiverType(otherType)
                .build();
        bizInfoMapper.sendInfo(bizInfo);

        BizUser sender = userMapper.getByUserId(userId);
        PushMessageVO pushVO = PushMessageVO.builder()
                .infoId(bizInfo.getInfoId())
                .senderId(userId)
                .senderName(sender != null ? sender.getNickname() : "")
                .senderAvatar(sender != null ? sender.getUserAvatar() : "")
                .context(content)
                .infoType(type)
                .receiverType(otherType)
                .receiverId(receiveId)
                .createTime(bizInfo.getCreatedTime().toString())
                .build();

        try {
            String pushJson = objectMapper.writeValueAsString(pushVO);
            if (ReceiverTypeConstant.RECTYPE_PRIVATE == otherType) {
                sessionManager.sendToUser(receiveId, pushJson);
            } else if (ReceiverTypeConstant.RECTYPE_PUBLIC == otherType) {
                List<BizGroupUser> members = groupUserMapper.selectByGroupId(receiveId);
                for (BizGroupUser member : members) {
                    if (!member.getUserId().equals(userId) && member.getIsDeleted() == 0) {
                        sessionManager.sendToUser(member.getUserId(), pushJson);
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败: {}", e.getMessage(), e);
        }

        InfoHistoryVO vo = new InfoHistoryVO();
        BeanUtils.copyProperties(bizInfo, vo);
        return vo;
    }

    @Override
    public List<InfoHistoryVO> OnGetHistory(Long otherId, Long lastId, Integer receiverType) {
        Long userId = UserContext.getUserId();
        if (ReceiverTypeConstant.RECTYPE_PUBLIC == receiverType) {
            BizGroupUser groupUser = groupUserMapper.selectByMemId(otherId, userId);
            if (groupUser == null || groupUser.getIsDeleted() == 1) {
                throw BusinessException.forbidden("您不在该群聊中");
            }
        }
        if (ReceiverTypeConstant.RECTYPE_PRIVATE == receiverType) {
            int isFriend = friendMapper.checkIsFriend(userId, otherId);
            if (isFriend <= 0) {
                throw BusinessException.forbidden("您不是对方的好友，无法查看历史消息");
            }
        }

        List<BizInfo> bizInfos = bizInfoMapper.getHistory(userId, otherId, lastId, PAGE_SIZE, receiverType);
        List<InfoHistoryVO> vos = new ArrayList<>();
        for (BizInfo bizInfo : bizInfos) {
            InfoHistoryVO vo = new InfoHistoryVO();
            BeanUtils.copyProperties(bizInfo, vo);
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public List<SessionListVO> getSessionList() {
        Long userId = UserContext.getUserId();
        List<SessionListVO> singleSessions = getSingleChatSessions(userId);
        List<SessionListVO> groupSessions  = getGroupChatSessions(userId);
        List<SessionListVO> allSessions = new ArrayList<>();
        allSessions.addAll(singleSessions);
        allSessions.addAll(groupSessions);

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
        List<BizInfo> latestMessages = bizInfoMapper.getSessionLatestMsg(userId);
        if (CollectionUtils.isEmpty(latestMessages)) {
            return Collections.emptyList();
        }
        List<SessionListVO> sessions = new ArrayList<>();
        for (BizInfo msg : latestMessages) {
            Long otherId = msg.getSenderId().equals(userId) ? msg.getReceiverId() : msg.getSenderId();
            SessionListVO vo = new SessionListVO();
            vo.setTargetId(otherId);
            vo.setSessionType(ReceiverTypeConstant.RECTYPE_PRIVATE);

            BizUser otherUser = userMapper.getByUserId(otherId);
            if (otherUser != null) {
                vo.setSessionName(otherUser.getNickname());
                vo.setSessionAvatar(otherUser.getUserAvatar());
            }

            BizFriend friend = friendMapper.getByFriendId(otherId, userId);
            if (friend != null) {
                if (friend.getNickname() != null) {
                    vo.setSessionName(friend.getNickname());
                }
                vo.setNotDisturb(friend.getNotDisturb() != null ? friend.getNotDisturb() : 0);
                vo.setIsTop(friend.getIsTop() != null ? friend.getIsTop() : 0);
            } else {
                vo.setNotDisturb(0);
                vo.setIsTop(0);
            }

            vo.setLastMessage(msg.getContext());
            vo.setLastTime(msg.getCreatedTime());
            Integer unread = bizInfoMapper.countUnreadMessages(userId, otherId);
            vo.setUnreadCount(unread != null ? unread : 0);
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
        for (BizGroupUser gu : userGroups) {
            BizGroup group = groupMapper.selectById(gu.getGroupId());
            if (group == null) {
                continue; // 群已解散（物理删除）
            }
            SessionListVO vo = new SessionListVO();
            vo.setTargetId(group.getGroupId());
            vo.setSessionType(ReceiverTypeConstant.RECTYPE_PUBLIC);
            vo.setSessionName(group.getGroupName());
            vo.setSessionAvatar(group.getGroupAvatar());
            vo.setNotDisturb(gu.getNotDisturb() != null ? gu.getNotDisturb() : 0);
            vo.setIsTop(gu.getIsTop() != null ? gu.getIsTop() : 0);

            BizInfo latestMsg = bizInfoMapper.getLatestGroupMessage(group.getGroupId());
            if (latestMsg != null) {
                vo.setLastMessage(latestMsg.getContext());
                vo.setLastTime(latestMsg.getCreatedTime());
                String key = userId + ":" + group.getGroupId();
                Long lastReadId = sessionManager.getGroupLastRead(key);
                int unread;
                if (lastReadId == null) {
                    unread = bizInfoMapper.countGroupMessages(group.getGroupId(), userId);
                } else {
                    unread = bizInfoMapper.countGroupMessagesAfter(group.getGroupId(), userId, lastReadId);
                }
                vo.setUnreadCount(unread);
            } else {
                vo.setLastMessage("");
                vo.setLastTime(null);
                vo.setUnreadCount(0);
            }
            sessions.add(vo);
        }
        return sessions;
    }

    @Override
    @Transactional
    public void markMessagesAsRead(Long targetId, Integer sessionType) {
        Long userId = UserContext.getUserId();
        if (ReceiverTypeConstant.RECTYPE_PRIVATE == sessionType) {
            bizInfoMapper.markMessagesAsRead(userId, targetId);
        } else if (ReceiverTypeConstant.RECTYPE_PUBLIC == sessionType) {
            BizInfo latestMsg = bizInfoMapper.getLatestGroupMessage(targetId);
            if (latestMsg != null) {
                String key = userId + ":" + targetId;
                sessionManager.updateGroupLastRead(key, latestMsg.getInfoId());
            }
        }
    }
}
