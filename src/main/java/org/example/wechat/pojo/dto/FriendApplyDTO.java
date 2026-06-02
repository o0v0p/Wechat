package org.example.wechat.pojo.dto;

import lombok.Data;

@Data
public class FriendApplyDTO {

    private Long friendId;
    private String source;
    private String applyRemark;
    private String nickname;

}