package org.example.wechat.pojo.dto;

import lombok.Data;

/**
 * 好友个人设置 DTO（备注、免打扰、置顶、分组）
 * 仅更新非 null 字段；categoryId 非 null 时同时移动分组
 */
@Data
public class FriendSettingsDTO {

    /** 好友的用户ID */
    private Long friendId;

    /** 备注；空字符串视为清空备注 */
    private String nickname;

    /** 免打扰（0-否，1-是） */
    private Integer notDisturb;

    /** 置顶（0-否，1-是） */
    private Integer isTop;

    /** 目标分组 ID；非 null 时移动好友到此分组 */
    private Long categoryId;
}
