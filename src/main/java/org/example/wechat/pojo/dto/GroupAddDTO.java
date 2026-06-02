package org.example.wechat.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GroupAddDTO {

    @NotBlank(message = "群名称不能为空")
    @Size(max = 50, message = "群名称长度不能超过50个字符")
    private String groupName;

    @NotNull(message = "成员列表不能为空")
    @Size(min = 1, message = "至少需要一个群成员")
    private List<Long> memberIds;

    @Size(max = 255, message = "群头像URL长度不能超过255个字符")
    private String groupAvatar;

    @Size(max = 200, message = "群描述长度不能超过200个字符")
    private String description;

}
