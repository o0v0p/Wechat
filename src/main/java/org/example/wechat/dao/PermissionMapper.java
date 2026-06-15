package org.example.wechat.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PermissionMapper {

    /**
     * 根据权限码查询权限ID
     *
     * @param permissionCode 权限名称（如 "踢出成员"）
     * @return permission_id，若不存在返回 null
     */
    @Select("SELECT permission_id FROM biz_permission WHERE permission = #{permissionCode}")
    Long getPermissionIdByCode(@Param("permissionCode") String permissionCode);
}
