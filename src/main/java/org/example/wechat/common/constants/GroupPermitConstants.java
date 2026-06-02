package org.example.wechat.common.constants;

/**
 * 群角色权限码常量
 * 与数据库 biz_permission 表中的 permission 字段保持一致
 */
public class GroupPermitConstants {

    /** 移除成员 */
    public static final String MEMBER_KICK    = "member:kick";

    /** 邀请成员 */
    public static final String MEMBER_INVITE  = "member:invite";

    /** 编辑群信息 */
    public static final String GROUP_EDIT     = "group:edit";

    /** 审核入群申请 */
    public static final String APPLY_AUDIT    = "apply:audit";

    /** 置顶/取消置顶消息 */
    public static final String MESSAGE_PIN    = "message:pin";

    /** 删除消息 */
    public static final String MESSAGE_DELETE = "message:delete";
}
