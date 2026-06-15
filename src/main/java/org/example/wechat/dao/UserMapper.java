package org.example.wechat.dao;

import org.apache.ibatis.annotations.*;
import org.example.wechat.common.annotation.AutoFill;
import org.example.wechat.common.constants.OperationType;
import org.example.wechat.pojo.entity.*;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM biz_user WHERE sys_user_id = #{userId} AND is_deleted = 0")
    @ResultMap("BaseResultMap")
    BizUser getByUserId(Long userId);

    /** 不过滤 is_deleted，用于注销后仍需读取用户信息的场景 */
    @Select("SELECT * FROM biz_user WHERE sys_user_id = #{userId}")
    @ResultMap("BaseResultMap")
    BizUser getByUserIdAny(Long userId);

    @Select("SELECT * FROM biz_user WHERE user_name = #{userName} AND is_deleted = 0")
    @ResultMap("BaseResultMap")
    BizUser getByUsername(String userName);

    @Select("SELECT * FROM biz_user WHERE user_telephone = #{telephone} AND is_deleted = 0")
    @ResultMap("BaseResultMap")
    BizUser getByTelephone(String telephone);

    @AutoFill(OperationType.INSERT)
    int insert(BizUser user);

    @AutoFill(OperationType.UPDATE)
    void update(BizUser bizUser);

    @Select("SELECT * FROM biz_user WHERE is_deleted = 0 " +
            "AND sys_user_id != #{currentUserId} " +
            "AND (user_name LIKE CONCAT('%', #{keyword}, '%') " +
            "     OR user_nickname LIKE CONCAT('%', #{keyword}, '%') " +
            "     OR user_telephone LIKE CONCAT('%', #{keyword}, '%')) " +
            "LIMIT 20")
    @ResultMap("BaseResultMap")
    List<BizUser> searchUsers(@Param("keyword") String keyword, @Param("currentUserId") Long currentUserId);

    List<BizUser> selectBatchIds(List<Long> userIds);
}
