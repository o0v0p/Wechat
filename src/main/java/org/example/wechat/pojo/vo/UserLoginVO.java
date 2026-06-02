package org.example.wechat.pojo.vo;

import lombok.*;

@Data
@Builder
public class UserLoginVO {

    private String token;
    private UserInfoVO userInfo;

}