package org.example.wechat.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupMemberVO {
    private Long userId;
    private String nickname;
    private String userAvatar;
    private Integer roleId;
    private String roleName;
    private String groupNickname;
}