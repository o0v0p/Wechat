package org.example.wechat.pojo.vo;

import com.fasterxml.jackson.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class UserInfoVO {

    private Long userId;
    private String userName;
    private String nickname;
    private String userAvatar;
    private String telephone;

    private String userEmail;
    private String userSignature;
    private Integer userSex;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastLoginTime;

}
