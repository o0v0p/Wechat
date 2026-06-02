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
@TableName("biz_role_permission")
@Schema(description = "角色权限关联实体")
public class BizRolePermission {

    @TableId("role_permission_id")
    @Schema(description = "群角色与权限的ID")
    private Long rolePermissionId;

    @TableField("role_id")
    @Schema(description = "群角色ID")
    private Long roleId;

    @TableField("permission_id")
    @Schema(description = "权限ID")
    private Long permissionId;

    @TableField("created_time")
    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @TableField("creator_id")
    @Schema(description = "创建者ID")
    private Long creatorId;
}