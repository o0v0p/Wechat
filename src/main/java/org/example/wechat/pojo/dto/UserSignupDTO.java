package org.example.wechat.pojo.dto;

import lombok.*;

@Data
public class UserSignupDTO {

    private String userName;
    private String nickname;
    private String password;
    private String telephone;
    private String authCode;

}
