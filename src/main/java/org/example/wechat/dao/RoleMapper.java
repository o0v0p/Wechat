package org.example.wechat.dao;

import org.apache.ibatis.annotations.*;
import org.example.wechat.common.annotation.AutoFill;
import org.example.wechat.common.constants.OperationType;
import org.example.wechat.pojo.entity.BizRole;
import java.util.List;

@Mapper
public interface RoleMapper {

    // 获取所有角色
    @Select("SELECT * FROM biz_role ORDER BY role_id")
    List<BizRole> selectAll();

    // 根据ID获取角色
    @Select("SELECT * FROM biz_role WHERE role_id = #{roleId}")
    BizRole selectById(@Param("roleId") Long roleId);

    // 创建角色
    @AutoFill(OperationType.INSERT)
    @Insert("INSERT INTO biz_role (role_id, role_name, created_time, creator_id, updated_time, updater_id) " +
            "VALUES (#{roleId}, #{roleName}, #{createdTime}, #{creatorId}, #{updatedTime}, #{updaterId})")
    int insert(BizRole role);

    // 更新角色
    @AutoFill(OperationType.UPDATE)
    @Update("UPDATE biz_role SET role_name = #{roleName}, updated_time = #{updatedTime}, updater_id = #{updaterId} " +
            "WHERE role_id = #{roleId}")
    int update(BizRole role);

    // 删除角色
    @Delete("DELETE FROM biz_role WHERE role_id = #{roleId}")
    int deleteById(@Param("roleId") Long roleId);

    // 检查角色名称是否存在
    @Select("SELECT COUNT(*) > 0 FROM biz_role WHERE role_name = #{roleName}")
    boolean existsByRoleName(@Param("roleName") String roleName);
}