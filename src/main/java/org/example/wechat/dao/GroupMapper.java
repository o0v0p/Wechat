package org.example.wechat.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.wechat.common.annotation.AutoFill;
import org.example.wechat.common.constants.OperationType;
import org.example.wechat.pojo.entity.BizGroup;
import org.example.wechat.pojo.vo.GroupSearchVO;

import java.util.List;

@Mapper
public interface GroupMapper {
    @Select("SELECT COUNT(*) > 0 FROM biz_group WHERE group_name = #{groupName}")
    boolean selectByName(@Param("groupName") String groupName);

    int insert(BizGroup bizGroup);

    @Select("SELECT * FROM biz_group WHERE group_id = #{groupId}")
    BizGroup selectById(@Param("groupId") Long groupId);

    /**
     * 行锁查询：用于群成员数量上限校验等并发敏感场景。
     * 仅在 @Transactional 事务内生效，要求数据库表使用 InnoDB。
     */
    @Select("SELECT * FROM biz_group WHERE group_id = #{groupId} FOR UPDATE")
    BizGroup selectByIdForUpdate(@Param("groupId") Long groupId);

    int deleteById(@Param("groupId") Long groupId);

    @AutoFill(OperationType.UPDATE)
    int updateById(BizGroup group);

    List<GroupSearchVO> selectByKey(String s);


}
