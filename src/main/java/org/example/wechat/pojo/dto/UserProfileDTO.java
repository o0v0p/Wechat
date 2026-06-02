package org.example.wechat.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserProfileDTO {

    private String userName;
    private String nickname;
    private String userAvatar;
    private String userEmail;
    private String userSignature;
    private Integer userSex;

}
