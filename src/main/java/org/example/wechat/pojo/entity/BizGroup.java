package org.example.wechat.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_group")
@Schema(description = "群聊实体")
public class BizGroup {
    private Long groupId;
    private String groupName;
    private Long ownerId;
    private Integer groupType;      //（1-公聊；0-私聊）
    private Integer maxNum;
    private String groupAvatar;
    private String description;
    private String groupNotice;
    private Long creatorId;
    private Long updaterId;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
