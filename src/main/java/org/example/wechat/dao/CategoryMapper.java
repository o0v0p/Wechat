package org.example.wechat.dao;

import org.apache.ibatis.annotations.*;
import org.example.wechat.common.annotation.AutoFill;
import org.example.wechat.common.constants.OperationType;
import org.example.wechat.pojo.entity.BizCategory;
import org.example.wechat.pojo.entity.BizUser;

import java.util.List;

@Mapper
public interface CategoryMapper {

    @Select("SELECT * FROM biz_category WHERE user_id = #{userId} ")
    List<BizCategory> getByUserId(@Param("userId")Long userId);

    @AutoFill(OperationType.INSERT)
    @Insert("INSERT INTO biz_category (user_id, category_name, created_time, updated_time, creator_id, updater_id) " +
            "VALUES (#{userId}, #{categoryName}, #{createdTime}, #{updatedTime}, #{creatorId}, #{updaterId})")
    @Options(useGeneratedKeys = true, keyProperty = "categoryId")
    void insertCategory(BizCategory bizCategory);

    @Select("SELECT COUNT(*) > 0 FROM biz_category WHERE user_id = #{userId} AND category_name = #{categoryName}")
    boolean existsByName(@Param("userId") Long userId, @Param("categoryName") String categoryName);

    @AutoFill(OperationType.UPDATE)
    @Update("UPDATE biz_category SET category_name = #{categoryName}, " +
            "updated_time = #{updatedTime}, updater_id = #{updaterId} " +
            "WHERE category_id = #{categoryId} AND user_id = #{userId}")
    int updateCategory(BizCategory category);

    @Select("SELECT * FROM biz_category WHERE category_id = #{categoryId}")
    BizCategory getById(@Param("categoryId") Long categoryId);
}
