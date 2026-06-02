package org.example.wechat.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BizGroupUser {
    private Long groupMemId;
    private Long groupId;
    private Long userId;
    private String nickname;        //在群中备注名
    private Long bizRoleId;
    private Integer notDisturb;
    private Integer isTop;
    private Integer isDeleted;
    private Integer applyType;      //加群类型（0-用户申请；1-成员邀请）
    private Long inviteBy;
    private String applyRemark;
    private Long checkBy;
    private Integer checkResult;        //审核结果（0-拒绝；1-通过；2-待处理）
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private Long creatorId;
    private Long updaterId;
}
