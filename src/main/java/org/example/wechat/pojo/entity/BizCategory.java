package org.example.wechat.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BizCategory {

    @TableField("category_id")
    private Long categoryId;
    @TableField("user_id")
    private Long userId;
    @TableField("category_name")
    private String categoryName;

    @TableField("created_time")
    private LocalDateTime createdTime;
    @TableField("updated_time")
    private LocalDateTime updatedTime;
    @TableField("creator_id")
    private Long creatorId;
    @TableField("updater_id")
    private Long updaterId;

}
