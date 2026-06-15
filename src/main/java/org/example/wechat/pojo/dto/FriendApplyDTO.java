package org.example.wechat.pojo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

@Data
public class FriendApplyDTO {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long friendId;
    private String source;
    private String applyRemark;
    private String nickname;

}