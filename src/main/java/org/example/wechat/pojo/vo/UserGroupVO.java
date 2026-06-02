package org.example.wechat.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户群组信息VO（含角色）")
public class UserGroupVO {

    @Schema(description = "群ID")
    private Long groupId;

    @Schema(description = "群名称")
    private String groupName;

    @Schema(description = "群头像")
    private String groupAvatar;

    @Schema(description = "用户角色：OWNER-群主，ADMIN-管理员，MEMBER-普通成员")
    private String role;

    @Schema(description = "角色ID（1-群主，2-管理员，3-普通成员）")
    private Integer roleId;

    @Schema(description = "在群中的备注名")
    private String nickname;

    @Schema(description = "免打扰")
    private Integer notDisturb;

    @Schema(description = "置顶")
    private Integer isTop;
}