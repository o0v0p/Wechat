package org.example.wechat.dao;

import org.apache.ibatis.annotations.*;
import org.example.wechat.common.annotation.AutoFill;
import org.example.wechat.common.constants.OperationType;
import org.example.wechat.pojo.entity.BizRolePermission;
import java.util.List;

@Mapper
public interface RolePermissionMapper {

    // 获取角色的所有权限
    @Select("SELECT permission_id FROM biz_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermitByRole(@Param("roleId") Long roleId);

    // 添加角色权限
    @AutoFill(OperationType.INSERT)
    @Insert("INSERT INTO biz_role_permission (role_permission_id, role_id, permission_id, created_time, creator_id) " +
            "VALUES (#{rolePermissionId}, #{roleId}, #{permissionId}, #{createdTime}, #{creatorId})")
    int insert(BizRolePermission rolePermission);

    // 删除角色的所有权限
    @Delete("DELETE FROM biz_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
    
}