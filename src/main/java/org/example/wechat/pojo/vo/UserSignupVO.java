package org.example.wechat.pojo.vo;

import lombok.*;

@Data
@Builder
public class UserSignupVO {

    private Long userId;
    private String userName;
    private String nickname;

}
