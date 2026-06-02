package org.example.wechat.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "群角色VO")
public class RoleVO {

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "权限ID列表")
    private List<Long> permissionIds;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
}