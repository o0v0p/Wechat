package org.example.wechat.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GroupDetailVO {
    private Long groupId;
    private String groupName;
    private String groupAvatar;
    private String description;
    private Long ownerId;
    private Integer memberCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;
    private List<GroupMemberVO> members;
}