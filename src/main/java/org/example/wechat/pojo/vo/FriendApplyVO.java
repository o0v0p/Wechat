package org.example.wechat.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Schema(description = "好友申请列表VO")
public class FriendApplyVO {

    @Schema(description = "申请记录ID")
    private Long userFriendId;

    @Schema(description = "被申请人ID")
    private Long friendId;

    @Schema(description = "被申请人昵称")
    private String friendName;

    @Schema(description = "被申请人头像")
    private String avatar;

    @Schema(description = "申请备注")
    private String applyRemark;

    @Schema(description = "被申请人昵称（与friendName一致，保证前端展示的是用户昵称而非备注）")
    private String nickname;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "申请状态：0-待处理，1-已同意，2-已拒绝")
    private Integer status;

    @Schema(description = "申请时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;

    @Schema(description = "处理时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedTime;

}
