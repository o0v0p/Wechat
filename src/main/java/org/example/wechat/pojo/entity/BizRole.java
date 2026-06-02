package org.example.wechat.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("biz_role")
@Schema(description = "群角色实体")
public class BizRole {

    @TableId("role_id")
    @Schema(description = "群角色ID")
    private Long roleId;

    @TableField("role_name")
    @Schema(description = "群角色名")
    private String roleName;

    @TableField("created_time")
    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @TableField("creator_id")
    @Schema(description = "创建者ID")
    private Long creatorId;

    @TableField("updated_time")
    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

    @TableField("updater_id")
    @Schema(description = "更新者ID")
    private Long updaterId;
}