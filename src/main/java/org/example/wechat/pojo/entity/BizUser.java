package org.example.wechat.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_user")
@Schema(description = "用户实体")
public class BizUser {
    @TableId(value = "sys_user_id", type = IdType.AUTO)
    private Long  userId;
    @TableField("user_name")
    private String userName;
    @TableField("user_nickname")
    private String nickname;
    @TableField("user_telephone")
    private String telephone;
    @TableField("user_password")
    private String password;

    @TableField("user_avatar")
    private String userAvatar;
    @TableField("user_email")
    private String userEmail;
    @TableField("user_signature")
    private String userSignature;
    @TableField("user_sex")
    private Integer userSex;

    @TableField("user_status")
    private Integer userStatus;
    @TableField("is_deleted")
    private Integer isDeleted;
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    @TableField("created_time")
    private LocalDateTime  createdTime;
    @TableField("updated_time")
    private LocalDateTime  updatedTime;
    @TableField("creator_id")
    private Long   creatorId;
    @TableField("updater_id")
    private Long  updaterId;

}
