package org.example.wechat.dao;

import org.apache.ibatis.annotations.*;
import org.example.wechat.common.annotation.AutoFill;
import org.example.wechat.common.constants.OperationType;
import org.example.wechat.pojo.entity.BizBlacklist;

import java.util.List;

@Mapper
public interface BlacklistMapper {

    @AutoFill(OperationType.INSERT)
    @Insert("INSERT INTO biz_blacklist(blacklist_id, biz_user_id, black_user_id, created_time, creator_id) " +
            "VALUES(#{blacklistId}, #{bizUserId}, #{blackUserId}, #{createdTime}, #{creatorId})")
    int insert(BizBlacklist blacklist);

    @Select("SELECT * FROM biz_blacklist WHERE biz_user_id = #{bizUserId} AND black_user_id = #{blackUserId}")
    BizBlacklist findByUser(@Param("bizUserId") Long bizUserId, @Param("blackUserId") Long blackUserId);

    @Delete("DELETE FROM biz_blacklist WHERE biz_user_id = #{bizUserId} AND black_user_id = #{blackUserId}")
    int delete(@Param("bizUserId") Long bizUserId, @Param("blackUserId") Long blackUserId);

    @Select("SELECT black_user_id FROM biz_blacklist WHERE biz_user_id = #{bizUserId}")
    List<Long> findAllBlackIds(@Param("bizUserId") Long bizUserId);
}
