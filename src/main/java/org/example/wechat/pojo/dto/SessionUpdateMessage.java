package org.example.wechat.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class SessionUpdateMessage {
    private String type;
    private Integer sessionType;
    private String lastMessage;
    private LocalDateTime lastTime;
    private Long infoId;
    private Long senderId;
    private Integer infoStatus;
    private Long targetId;
}
