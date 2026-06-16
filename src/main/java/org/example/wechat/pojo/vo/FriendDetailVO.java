package org.example.wechat.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "好友详情资料页")
public class FriendDetailVO {

    @Schema(description = "微信号")
    private String userName;

    @Schema(description = "微信昵称/备注昵称")
    private String nickname;

    @Schema(description = "头像")
    private String userAvatar;

    @Schema(description = "性别（0:未知；1:男；2:女）")
    private Integer userSex;

    @Schema(description = "邮箱")
    private String userEmail;

    @Schema(description = "个性签名")
    private String userSignature;

    @Schema(description = "所属分组ID")
    private Long categoryId;

    @Schema(description = "添加时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;

    @Schema(description = "聊天ID")
    private String chatId;

}
