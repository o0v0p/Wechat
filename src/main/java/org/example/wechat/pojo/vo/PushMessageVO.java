package org.example.wechat.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import org.example.wechat.common.constants.InfoTypeConstants;

@Data
@Builder
public class PushMessageVO {
    private Long infoId;
    private Long senderId;
    private String senderName;    // 发送者昵称
    private String senderAvatar;  // 发送者头像
    private String context;
    @Schema(description = "消息类型：" + InfoTypeConstants.TEXT + "-文本，" + InfoTypeConstants.FILE + "-文件，" + InfoTypeConstants.IMAGE + "-图片，" + InfoTypeConstants.VIDEO + "-视频")
    private Integer infoType;
    private Integer receiverType; // 0单聊 1群聊
    private Long receiverId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private String createTime;
}
