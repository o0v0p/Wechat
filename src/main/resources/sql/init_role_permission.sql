-- ============================================================
-- 群角色权限模块初始化数据
-- 对应常量类：
--   GroupRoleConstants       -> biz_role
--   GroupPermissionConstants -> biz_permission
--   权限分配规则             -> biz_role_permission
-- ============================================================

-- ----------------------------
-- biz_role 初始化（3 条）
-- role_id 与 GroupRoleConstants 保持一致
-- ----------------------------
INSERT INTO `biz_role` (`role_id`, `role_name`) VALUES
(1, '群主'),
(2, '管理员'),
(3, '普通成员');


-- ----------------------------
-- biz_permission 初始化（6 条）
-- permission 与 GroupPermissionConstants / GroupPermissionChecker 保持一致
-- ----------------------------
INSERT INTO `biz_permission` (`permission_id`, `permission`) VALUES
(1, '踢出成员'),
(2, '邀请成员'),
(3, '编辑群信息'),
(4, '审核申请'),
(5, '置顶消息'),
(6, '删除消息');


-- ----------------------------
-- biz_role_permission 初始化
-- 权限分配规则：
--   群主   (role_id=1)：拥有全部权限，但代码层直接放行，无需在此维护
--   管理员 (role_id=2)：踢出成员 / 邀请成员 / 审核申请 / 删除消息
--   普通成员(role_id=3)：邀请成员
-- role_permission_id 采用手动编号（生产环境可改用雪花ID）
-- ----------------------------
INSERT INTO `biz_role_permission` (`role_permission_id`, `role_id`, `permission_id`) VALUES
-- 管理员权限
(1, 2, 1),   -- 管理员 -> 踢出成员
(2, 2, 2),   -- 管理员 -> 邀请成员
(3, 2, 4),   -- 管理员 -> 审核申请
(4, 2, 6),   -- 管理员 -> 删除消息
-- 普通成员权限
(5, 3, 2);   -- 普通成员 -> 邀请成员
