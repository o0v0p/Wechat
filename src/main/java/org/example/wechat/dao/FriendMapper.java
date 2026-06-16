package org.example.wechat.dao;

import org.apache.ibatis.annotations.*;
import org.example.wechat.common.annotation.AutoFill;
import org.example.wechat.common.constants.OperationType;
import org.example.wechat.pojo.entity.BizFriend;
import org.example.wechat.pojo.entity.BizFriendApply;
import org.example.wechat.pojo.vo.FriendApplyVO;
import org.example.wechat.pojo.vo.FriendListVO;

import java.util.List;

@Mapper
public interface FriendMapper {

    List<FriendListVO> getfriendList(@Param("userId") Long userId);

    /** 查询与用户是好友关系（biz_friend 中有记录）的所有对方用户ID（注销推送用） */
    @Select("SELECT sys_friend_id FROM biz_friend WHERE creator_id = #{userId}")
    List<Long> getFriendIdsByUserId(@Param("userId") Long userId);

    @AutoFill(OperationType.INSERT)
    @Insert("INSERT INTO biz_user_friend (" +
            "user_id, friend_id, status, source, nickname," +
            "apply_remark, created_time, updated_time, " +
            "creator_id, updater_id) " +
            "VALUES (" +
            "#{userId}, #{friendId}, #{status}, #{source}," +
            "#{nickname}, #{applyRemark}, #{createdTime}, #{updatedTime}, " +
            "#{creatorId}, #{updaterId})")
    @Options(useGeneratedKeys = true, keyProperty = "userFriendId", keyColumn = "user_friend_id")
    int addFriendApply(BizFriendApply bizFriendApply);

    @Insert("INSERT INTO biz_friend (" +
            "sys_friend_id, category_id, friend_name, nickname, " +
            "signature, not_disturb, is_top, " +
            "created_time, updated_time, creator_id, updater_id) " +
            "VALUES (" +
            "#{friendID}, #{categoryId}, #{friendName}, #{nickname}, " +
            "#{signature}, #{notDisturb}, #{isTop}, " +
            "#{createdTime}, #{updatedTime}, #{creatorId}, #{updaterId})")
    @Options(useGeneratedKeys = true, keyProperty = "friendID", keyColumn = "sys_friend_id")
    int addFriend(BizFriend bizFriend);

    @Select("SELECT " +
            "a.user_friend_id as userFriendId, " +
            "a.friend_id as friendId, " +
            "a.status, " +
            "a.source, " +
            "a.apply_remark as applyRemark, " +
            "a.created_time as createdTime, " +
            "a.updated_time as updatedTime, " +
            "u.user_nickname as friendName, " +
            "u.user_nickname as nickname, " +
            "u.user_avatar as avatar " +
            "FROM biz_user_friend a " +
            "LEFT JOIN biz_user u ON " +
            "  (a.user_id = #{userId} AND a.friend_id = u.sys_user_id) OR " +
            "  (a.friend_id = #{userId} AND a.user_id = u.sys_user_id) " +
            "WHERE (a.user_id = #{userId} OR a.friend_id = #{userId}) " +
            "  AND (u.is_deleted = 0 OR u.sys_user_id = #{userId})")
    List<FriendApplyVO> getApplyByUser(@Param("userId") Long userId);

    @Select("SELECT * FROM biz_user_friend WHERE user_friend_id = #{applyId}")
    BizFriendApply getByApplyId(@Param("applyId") Long applyId);

    @Select("SELECT * FROM biz_user_friend WHERE friend_id = #{friendId} AND user_id = #{userId} AND status = 1")
    BizFriendApply getByRelation(@Param("friendId") Long friendId, @Param("userId") Long userId);

    @Results({
            @Result(column = "sys_friend_id", property = "friendID"),
            @Result(column = "creator_id", property = "creatorId"),
            @Result(column = "updater_id", property = "updaterId"),
            @Result(column = "created_time", property = "createdTime"),
            @Result(column = "updated_time", property = "updatedTime")
    })
    @Select("SELECT * FROM biz_friend WHERE sys_friend_id = #{friendId} AND creator_id = #{userId}")
    BizFriend getByFriendId(@Param("friendId") Long friendId,@Param("userId") Long userId);

    @Select("SELECT CONCAT(LEAST(#{userId}, #{friendId}), '_', GREATEST(#{userId}, #{friendId}))")
    String getChatId(@Param("userId") Long userId, @Param("friendId") Long friendId);

    @Update("UPDATE biz_user_friend SET status = #{status}, updated_time = #{updatedTime}, updater_id = #{updaterId} WHERE user_friend_id = #{userFriendId}")
    @AutoFill(value = OperationType.UPDATE)
    int updateApply(BizFriendApply bizFriendApply);

    @Select("SELECT COUNT(*) FROM biz_friend WHERE " +
            "(creator_id = #{userId} AND sys_friend_id = #{friendId}) OR " +
            "(creator_id = #{friendId} AND sys_friend_id = #{userId})")
    int checkIsFriend(@Param("userId") Long userId, @Param("friendId") Long friendId);

    @Select("SELECT * FROM biz_user_friend WHERE " +
            "((user_id = #{userId} AND friend_id = #{friendId}) OR " +
            "(user_id = #{friendId} AND friend_id = #{userId})) " +
            "AND status = 0")
    BizFriendApply getPendingApply(@Param("userId") Long userId, @Param("friendId") Long friendId);

    @Update("UPDATE biz_friend SET category_id = " +
            "COALESCE((SELECT category_id FROM biz_category WHERE user_id = #{userId} AND category_name = #{categoryName} LIMIT 1), category_id) " +
            "WHERE creator_id = #{userId} AND sys_friend_id = #{friendId}")
    int moveCategory(@Param("userId") Long userId,
                     @Param("friendId") Long friendId,
                     @Param("categoryName") String categoryName);

    @Update("UPDATE biz_friend SET nickname = #{remark}, " +
            "updated_time = NOW(), updater_id = #{userId} " +
            "WHERE creator_id = #{userId} AND sys_friend_id = #{friendId}")
    int updateRemark(@Param("remark") String remark,
                     @Param("friendId") Long friendId,
                     @Param("userId") Long userId);

    @Update("<script>" +
            "UPDATE biz_friend SET " +
            "  <if test='dto.nickname != null'>nickname = #{dto.nickname}, </if>" +
            "  <if test='dto.notDisturb != null'>not_disturb = #{dto.notDisturb}, </if>" +
            "  <if test='dto.isTop != null'>is_top = #{dto.isTop}, </if>" +
            "  <if test='dto.categoryId != null'>category_id = #{dto.categoryId}, </if>" +
            "  updated_time = NOW(), updater_id = #{userId} " +
            "WHERE creator_id = #{userId} AND sys_friend_id = #{dto.friendId}" +
            "</script>")
    int updateFriendSettings(@Param("dto") org.example.wechat.pojo.dto.FriendSettingsDTO dto,
                             @Param("userId") Long userId);

    @Delete("DELETE FROM biz_friend WHERE " +
            "(creator_id = #{userId} AND sys_friend_id = #{friendId}) OR " +
            "(creator_id = #{friendId} AND sys_friend_id = #{userId})")
    int deleteFriend(@Param("userId") Long userId, @Param("friendId") Long friendId);

    @Select("<script>" +
            "SELECT friend_id FROM biz_user_friend " +
            "WHERE user_id = #{userId} AND status = #{status} " +
            "  AND friend_id IN <foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>" +
            " UNION " +
            "SELECT user_id FROM biz_user_friend " +
            "WHERE friend_id = #{userId} AND status = #{status} " +
            "  AND user_id IN <foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<Long> batchRelationIds(@Param("userId") Long userId,
                                @Param("ids") List<Long> ids,
                                @Param("status") int status);

    /** 批量查哪些用户是好友（基于 biz_friend） */
    @Select("<script>" +
            "SELECT sys_friend_id FROM biz_friend " +
            "WHERE creator_id = #{userId} " +
            "  AND sys_friend_id IN <foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<Long> batchFriendIds(@Param("userId") Long userId,
                              @Param("ids") List<Long> ids);
}
