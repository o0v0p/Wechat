package org.example.wechat.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("biz_permission")
@Schema(description = "权限实体")
public class BizPermission {
    @Schema(description = "权限ID")
    private Long permissionId;
    @Schema(description = "权限编码")
    private String permission;
    @Schema(description = "权限描述")
    private String description;
}
