package org.example.wechat.pojo.dto;

import lombok.Data;

@Data
public class UserForgetPwdDTO {

    private String telephone;
    private String newPassword;
    private String authCode;

}
