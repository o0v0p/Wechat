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
            "#{friendID}, #{categoryID}, #{friendName}, #{nickname}, " +
            "#{signature}, #{notDisturb}, #{isTop}, " +
            "#{createdTime}, #{updatedTime}, #{creatorId}, #{updaterId})")
    @Options(useGeneratedKeys = true, keyProperty = "friendID", keyColumn = "sys_friend_id")
    int addFriend(BizFriend bizFriend);

    @Select("SELECT a.*, " +
            "u.user_name as friendName, " +
            "u.user_avatar as avatar " +
            "FROM biz_user_friend a " +
            "LEFT JOIN biz_user u ON " +
            "  (a.user_id = #{userId} AND a.friend_id = u.sys_user_id) OR " +
            "  (a.friend_id = #{userId} AND a.user_id = u.sys_user_id) " +
            "WHERE a.user_id = #{userId} OR a.friend_id = #{userId}")
    List<FriendApplyVO> getApplyByUser(@Param("userId") Long userId);

    @Select("SELECT * FROM biz_user_friend WHERE user_friend_id = #{applyId}")
    BizFriendApply getByApplyId(@Param("applyId") Long applyId);

    @Select("SELECT * FROM biz_user_friend WHERE friend_id = #{friendId} AND user_id = #{userId}")
    BizFriendApply getByRelation(@Param("friendId") Long friendId, @Param("userId") Long userId);

    @Select("SELECT * FROM biz_friend WHERE sys_friend_id = #{friendId} AND creator_id = #{userId}")
    BizFriend getByFriendId(@Param("friendId") Long friendId,@Param("userId") Long userId);

    @Select("SELECT CONCAT(LEAST(#{userId}, #{friendId}), '_', GREATEST(#{userId}, #{friendId}))")
    String getChatId(@Param("userId") Long userId, @Param("friendId") Long friendId);

    @Update("UPDATE biz_user_friend SET status = #{status}, updated_time = #{updatedTime}, updater_id = #{updaterId} WHERE user_friend_id = #{userFriendId}")
    @AutoFill(value = OperationType.UPDATE)
    int updateApply(BizFriendApply bizFriendApply);

    @Select("SELECT COUNT(*) FROM biz_user_friend WHERE " +
            "((user_id = #{userId} AND friend_id = #{friendId}) OR " +
            "(user_id = #{friendId} AND friend_id = #{userId})) " +
            "AND status = 1")
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

    @Delete("DELETE FROM biz_friend WHERE " +
            "(creator_id = #{userId} AND sys_friend_id = #{friendId}) OR " +
            "(creator_id = #{friendId} AND sys_friend_id = #{userId})")
    int deleteFriend(@Param("userId") Long userId, @Param("friendId") Long friendId);
}
