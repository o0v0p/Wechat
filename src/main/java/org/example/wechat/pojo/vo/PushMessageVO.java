package org.example.wechat.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PushMessageVO {
    private Long infoId;
    private Long senderId;
    private String senderName;    // 发送者昵称
    private String senderAvatar;  // 发送者头像
    private String context;
    private Integer infoType;
    private Integer receiverType; // 0单聊 1群聊
    private Long receiverId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private String createTime;
}
