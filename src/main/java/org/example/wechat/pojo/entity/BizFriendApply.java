package org.example.wechat.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_user_friend")
@Schema(description = "好友申请实体")
public class BizFriendApply {
    @TableId(value = "user_friend_id", type = IdType.AUTO)
    private Long userFriendId;
    @TableField("user_id")
    private Long userId;
    @TableField("friend_id")
    private Long friendId;

    @TableField("status")
    private Integer status;     //（0-待处理，1-已是好友，2-已拒绝，3-已删除
    @TableField("apply_remark")
    private String applyRemark;
    @TableField("nickname")
    private String nickname;
    @TableField("source")
    private String source;


    @TableField("created_time")
    private LocalDateTime createdTime;
    @TableField("updated_time")
    private LocalDateTime  updatedTime;
    @TableField("creator_id")
    private Long   creatorId;
    @TableField("updater_id")
    private Long  updaterId;
}
