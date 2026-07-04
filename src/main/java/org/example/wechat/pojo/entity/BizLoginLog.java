package org.example.wechat.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_login_log")
public class BizLoginLog {

    @TableId(value = "login_id", type = IdType.AUTO)
    private Long loginId;

    @TableField("user_id")
    private Long userId;

    @TableField("user_ip")
    private String userIp;

    @TableField("user_address")
    private String userAddress;

    @TableField("user_device")
    private String userDevice;

    @TableField("interface_id")
    private Long interfaceId;

    @TableField("login_result")
    private Integer loginResult;

    @TableField("return_msg")
    private String returnMsg;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("creator_id")
    private Long creatorId;
}
