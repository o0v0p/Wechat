package org.example.wechat.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Schema(description = "会话列表VO")
public class SessionListVO {
    @Schema(description = "目标ID（单聊为对方用户ID，群聊为群ID）")
    private Long targetId;

    @Schema(description = "会话类型（0-单聊，1-群聊）")
    private Integer sessionType;

    @Schema(description = "会话名称")
    private String sessionName;

    @Schema(description = "会话头像")
    private String sessionAvatar;

    @Schema(description = "最后一条消息内容")
    private String lastMessage;

    @Schema(description = "最后消息时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastTime;

    @Schema(description = "未读消息数")
    private Integer unreadCount;

    @Schema(description = "免打扰（0-否，1-是）")
    private Integer notDisturb;

    @Schema(description = "置顶（0-否，1-是）")
    private Integer isTop;
}