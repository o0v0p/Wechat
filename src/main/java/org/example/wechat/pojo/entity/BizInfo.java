package org.example.wechat.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("biz_info")
@Schema(description = "聊天消息实体")
public class BizInfo {
    private Long infoId;
    private Long senderId;
    private Long receiverId;
    private Integer receiverType;   // 聊天类型（0-单聊；1-群聊）
    private String context;
    private Integer infoStatus;   // 消息状态（0-正常；1-撤销；2-引用；3-发送失败）
    private Integer infoType;    // (1-文本；2-文件；3-图片；4-视频)
    private Integer isRead;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private Long creatorId;
    private Long updaterId;
}
