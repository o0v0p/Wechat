package org.example.wechat.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InfoHistoryVO {

    private Long infoId;
    private Long senderId;
    private String senderName;
    private String senderAvatar;

    private Long receiverId;
    private Integer receiverType;

    private String context;
    @Schema(description = "消息类型：1-文本，2-文件，3-图片，4-视频")
    private Integer infoType;
    private Integer infoStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;
}
