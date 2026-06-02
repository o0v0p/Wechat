package org.example.wechat.common.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.constants.GroupPermitConstants;
import org.example.wechat.common.constants.GroupRoleConstants;
import org.example.wechat.common.exception.BusinessException;
import org.example.wechat.dao.GroupMapper;
import org.example.wechat.dao.GroupUserMapper;
import org.example.wechat.dao.PermissionMapper;
import org.example.wechat.dao.RolePermissionMapper;
import org.example.wechat.pojo.entity.BizGroup;
import org.example.wechat.pojo.entity.BizGroupUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class BizPermitChecker {

    @Autowired
    private GroupUserMapper groupUserMapper;

    @Autowired
    private GroupMapper groupMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    /**
     * 权限码 -> permission_id 的本地缓存。
     * 应用启动后从数据库加载一次，运行期不变（系统级权限不会动态增减）。
     */
    private final Map<String, Long> permissionMap = new HashMap<>();

    /**
     * Spring 容器初始化完毕、所有 Bean 注入完成后执行，
     * 将 biz_permission 表的权限数据加载到内存缓存。
     */
    @PostConstruct
    public void initPermissionMap() {
        String[] permCodes = {
                GroupPermitConstants.MEMBER_KICK,
                GroupPermitConstants.MEMBER_INVITE,
                GroupPermitConstants.GROUP_EDIT,
                GroupPermitConstants.APPLY_AUDIT,
                GroupPermitConstants.MESSAGE_PIN,
                GroupPermitConstants.MESSAGE_DELETE
        };
        for (String code : permCodes) {
            Long id = permissionMapper.getPermissionIdByCode(code);
            if (id != null) {
                permissionMap.put(code, id);
                log.info("权限缓存加载：{} -> {}", code, id);
            } else {
                log.warn("权限码 [{}] 在数据库中未找到，请检查 biz_permission 初始化数据", code);
            }
        }
    }

    /**
     * 检查用户在指定群中是否拥有某权限
     *
     * @param groupId        群ID
     * @param userId         用户ID
     * @param permissionCode 权限码（见 {@link GroupPermitConstants}）
     */
    public boolean hasPermission(Long groupId, Long userId, String permissionCode) {
        BizGroupUser gu = groupUserMapper.selectByMemId(groupId, userId);
        if (gu == null || gu.getIsDeleted() == 1) {
            return false;
        }
        // 群主拥有所有权限
        if (GroupRoleConstants.ROLE_OWNER.equals(gu.getBizRoleId())) {
            return true;
        }
        Long permissionId = permissionMap.get(permissionCode);
        if (permissionId == null) {
            log.warn("未识别的权限码 [{}]，拒绝访问", permissionCode);
            return false;
        }
        List<Long> rolePermissions = rolePermissionMapper.selectPermitByRole(gu.getBizRoleId());
        return rolePermissions != null && rolePermissions.contains(permissionId);
    }

    /**
     * 检查用户是否是群主
     */
    public boolean isOwner(Long groupId, Long userId) {
        BizGroup group = groupMapper.selectById(groupId);
        return group != null && group.getOwnerId().equals(userId);
    }

    /**
     * 检查用户是否是群主或管理员
     */
    public boolean isOwnerOrAdmin(Long groupId, Long userId) {
        BizGroupUser gu = groupUserMapper.selectByMemId(groupId, userId);
        if (gu == null || gu.getIsDeleted() == 1) {
            return false;
        }
        return GroupRoleConstants.ROLE_OWNER.equals(gu.getBizRoleId())
                || GroupRoleConstants.ROLE_ADMIN.equals(gu.getBizRoleId());
    }

    /**
     * 检查操作者是否可以对目标成员执行操作（踢人、改角色等）
     * <ul>
     *   <li>不能操作自己</li>
     *   <li>群主可操作任何人</li>
     *   <li>管理员只能操作普通成员</li>
     * </ul>
     */
    public boolean canOperateMember(Long groupId, Long operatorId, Long targetId) {
        if (operatorId.equals(targetId)) {
            return false;
        }
        BizGroupUser operator = groupUserMapper.selectByMemId(groupId, operatorId);
        BizGroupUser target   = groupUserMapper.selectByMemId(groupId, targetId);
        if (operator == null || target == null || target.getIsDeleted() == 1) {
            return false;
        }
        Long operatorRole = operator.getBizRoleId();
        Long targetRole   = target.getBizRoleId();
        if (GroupRoleConstants.ROLE_OWNER.equals(operatorRole)) {
            return true;
        }
        return GroupRoleConstants.ROLE_ADMIN.equals(operatorRole)
                && GroupRoleConstants.ROLE_MEMBER.equals(targetRole);
    }

    /**
     * 断言用户拥有权限，否则抛出禁止访问异常
     */
    public void checkPermission(Long groupId, Long userId, String permissionCode, String message) {
        if (!hasPermission(groupId, userId, permissionCode)) {
            throw BusinessException.forbidden(message);
        }
    }

    /**
     * 断言操作者可操作目标成员，否则抛出禁止访问异常
     */
    public void checkCanOperateMember(Long groupId, Long operatorId, Long targetId, String message) {
        if (!canOperateMember(groupId, operatorId, targetId)) {
            throw BusinessException.forbidden(message);
        }
    }
}
