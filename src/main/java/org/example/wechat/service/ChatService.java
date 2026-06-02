package org.example.wechat.service;

import org.example.wechat.pojo.vo.InfoHistoryVO;
import org.example.wechat.pojo.vo.SessionListVO;

import java.util.List;

public interface ChatService {
    InfoHistoryVO OnSendInfo(String content, Long receiveId, Integer type, Integer otherType);

    List<InfoHistoryVO> OnGetHistory(Long otherId, Long LastId,Integer receiverType);

    List<SessionListVO> getSessionList();

    void markMessagesAsRead(Long targetId, Integer sessionType);

}
