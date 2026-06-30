package org.example.wechat.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "群成员个人设置DTO")
public class GroupMemberSettingsDTO {

    @Schema(description = "群ID")
    private Long groupId;

    @Schema(description = "群昵称（空字符串表示清空）")
    private String nickname;

    @Schema(description = "免打扰（0-关闭；1-开启）")
    private Integer notDisturb;

    @Schema(description = "置顶（0-关闭；1-开启）")
    private Integer isTop;
}
