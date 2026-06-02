package org.example.wechat.pojo.vo;

import lombok.Data;

@Data
public class GroupSearchVO {
    private Long groupId;
    private String groupName;
    private String groupAvatar;
    private String description;
    private Integer memberCount;
}