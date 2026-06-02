package org.example.wechat.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BizBlacklist {
    private Long blacklistId;
    private Long bizUserId;
    private Long blackUserId;
    private LocalDateTime createdTime;
    private Long creatorId;
}
