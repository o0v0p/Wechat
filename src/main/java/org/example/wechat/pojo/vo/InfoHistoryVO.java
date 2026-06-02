package org.example.wechat.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
public class InfoHistoryVO {

    private Long infoId;
    private Long senderId;
    private Long receiverId;
    private String context;
    private Integer infoType;
    private Integer infoStatus;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;

}
