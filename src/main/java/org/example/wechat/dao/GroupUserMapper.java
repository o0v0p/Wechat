package org.example.wechat.dao;

import org.apache.ibatis.annotations.*;
import org.example.wechat.common.annotation.AutoFill;
import org.example.wechat.common.constants.OperationType;
import org.example.wechat.pojo.entity.BizGroupUser;
import org.example.wechat.pojo.vo.GroupApplyVO;
import org.example.wechat.pojo.vo.GroupMemberVO;
import org.example.wechat.pojo.vo.PendingApplyVO;

import java.util.List;

@Mapper
public interface GroupUserMapper {

    // 正式成员
    int batchInsert(@Param("list") List<BizGroupUser> list);

    List<BizGroupUser> selectByGroupId(@Param("groupId") Long groupId);

    /** 查询正式有效群成员：check_result=1 AND is_deleted=0 */
    BizGroupUser selectByMemId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** 查询待审核的自主入群申请：check_result=2 AND apply_type=0 AND is_deleted=0 */
    BizGroupUser selectPendingApply(@Param("groupId") Long groupId, @Param("userId") Long userId);

    int deleteByMem(@Param("groupId") Long groupId, @Param("userId") Long userId);

    int deleteByGroupId(@Param("groupId") Long groupId);

    int countByGroupId(@Param("groupId") Long groupId);

    @AutoFill(OperationType.UPDATE)
    int updateRole(@Param("groupId") Long groupId, @Param("userId") Long userId, @Param("roleId") Long roleId);

    @Select("SELECT * FROM biz_group_user WHERE user_id = #{userId} AND is_deleted = 0 AND check_result = 1")
    List<BizGroupUser> selectByUserId(@Param("userId") Long userId);

    /** 查询用户的所有正式群成员记录（注销时退出所有群用） */
    List<BizGroupUser> selectAllByUserId(@Param("userId") Long userId);

    /** 查询用户作为群主且群人数 > 1 的群聊（注销前校验用） */
    @Select("SELECT gu.* FROM biz_group_user gu " +
            "INNER JOIN biz_group g ON gu.group_id = g.group_id " +
            "WHERE gu.user_id = #{userId} AND gu.biz_role_id = #{ownerRoleId} " +
            "  AND gu.is_deleted = 0 AND gu.check_result = 1 " +
            "  AND (SELECT COUNT(*) FROM biz_group_user gu2 " +
            "       WHERE gu2.group_id = gu.group_id AND gu2.is_deleted = 0 AND gu2.check_result = 1) > 1")
    List<BizGroupUser> selectOwnerGroupsWithMultiMember(@Param("userId") Long userId,
                                                         @Param("ownerRoleId") Long ownerRoleId);

    int batchDeleteMem(@Param("groupId") Long groupId,
                       @Param("userIds") List<Long> userIds);

    @Update("UPDATE biz_group_user " +
            "SET biz_role_id = #{roleId}, updated_time = NOW(), updater_id = #{updaterId} " +
            "WHERE group_id = #{groupId} " +
            "  AND user_id = #{userId} " +
            "  AND is_deleted = 0 " +
            "  AND check_result = 1")
    int updateMemberRole(@Param("groupId") Long groupId, @Param("userId") Long userId, @Param("roleId") Long roleId, @Param("updaterId") Long updaterId);

    @Select("SELECT " +
            "    gu.user_id, " +
            "    COALESCE(gu.nickname, u.user_nickname) as nickname, " +
            "    u.user_avatar, " +
            "    gu.biz_role_id as roleId, " +
            "    r.role_name as roleName, " +
            "    gu.nickname as groupNickname " +
            "FROM biz_group_user gu " +
            "LEFT JOIN biz_user u ON gu.user_id = u.sys_user_id " +
            "LEFT JOIN biz_role r ON gu.biz_role_id = r.role_id " +
            "WHERE gu.group_id = #{groupId} AND gu.is_deleted = 0 AND gu.check_result = 1 AND u.is_deleted = 0")
    List<GroupMemberVO> selectGroupMembersWithRole(@Param("groupId") Long groupId);

    // 申请中
    @AutoFill(OperationType.INSERT)
    int insertApply(BizGroupUser apply);

    @Select("SELECT * FROM biz_group_user WHERE group_mem_id = #{groupMemId}")
    BizGroupUser selectByGroupMemId(@Param("groupMemId") Long groupMemId);

    List<GroupApplyVO> selectApplyByUserId(@Param("userId") Long userId);

    List<PendingApplyVO> selectPendingByGroupId(@Param("groupId") Long groupId);

    @Update("UPDATE biz_group_user " +
            "SET check_result = #{checkResult}, check_by = #{checkBy}, " +
            "    created_time = CASE WHEN #{checkResult} = 1 THEN NOW() ELSE created_time END, " +
            "    updated_time = NOW(), updater_id = #{checkBy} " +
            "WHERE group_mem_id = #{groupMemId} " +
            "  AND check_result = 2 " +
            "  AND is_deleted = 0")
    int updateCheckResult(@Param("groupMemId") Long groupMemId,
                          @Param("checkResult") Integer checkResult,
                          @Param("checkBy") Long checkBy);

    @Delete("DELETE FROM biz_group_user " +
            "WHERE group_id = #{groupId} AND user_id = #{userId} AND check_result = 2 AND apply_type = 0")
    int clearMemApply(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
