package org.example.wechat.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GroupUpdateDTO{

    @NotNull(message = "群ID不能为空")
    private Long groupId;
    private String groupName;
    private String groupAvatar;
    private String description;      // 群描述
    private String groupNotice;      // 群公告

}