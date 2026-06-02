package org.example.wechat.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "搜索用户结果VO")
public class UserSearchVO {

    @Schema(description = "用户ID")
    private Long  userId;

    @Schema(description = "微信号（用户名）")
    private String userName;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像URL")
    private String userAvatar;

    @Schema(description = "性别（0-未知；1-男；2-女）")
    private Integer userSex;

    @Schema(description = "个性签名（初始化'暂无签名'）")
    private String userSignature;

    @Schema(description = "好友状态：0-不是好友，1-已是好友，2-申请中")
    private Integer friendStatus;

}