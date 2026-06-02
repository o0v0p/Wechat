package org.example.wechat.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GroupApplyVO {
    private Long groupMemId;
    private Long groupId;
    private String groupName;
    private Long userId;
    private String userName;
    private String userAvatar;
    private Integer applyType;
    private String applyRemark;
    private Integer checkResult;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;
}
