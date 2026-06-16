package org.example.wechat.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_friend")
@Schema(description = "好友实体")
public class BizFriend {
    @TableField("sys_friend_id")
    private Long friendID;
    @TableField("category_id")
    private Long categoryId;
    @TableField("friend_name")
    private String friendName;      // 好友userName

    @TableField("nickname")
    private  String nickname;   // 对好友的备注
    @TableField("signature")
    private String signature;

    @TableField("not_disturb")      // 免打扰（0-否；1-是）
    private Integer notDisturb;
    @TableField("is_top")       // 置顶（0-否；1-是）
    private Integer isTop;

    @TableField("created_time")
    private LocalDateTime createdTime;
    @TableField("updated_time")
    private LocalDateTime  updatedTime;
    @TableField("creator_id")
    private Long   creatorId;
    @TableField("updater_id")
    private Long  updaterId;

}
