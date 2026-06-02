package org.example.wechat.dao;

import org.apache.ibatis.annotations.*;
import org.example.wechat.common.annotation.AutoFill;
import org.example.wechat.common.constants.OperationType;
import org.example.wechat.pojo.entity.BizInfo;
import java.util.List;

@Mapper
public interface BizInfoMapper {

    // 发送消息
    @AutoFill(OperationType.INSERT)
    @Insert("INSERT INTO biz_info (info_id, sender_id, receiver_id, receiver_type, context, info_status, info_type, is_read, created_time, updated_time, creator_id, updater_id) " +
            "VALUES (#{infoId}, #{senderId}, #{receiverId}, #{receiverType}, #{context}, #{infoStatus}, #{infoType}, #{isRead}, " +
            "#{createdTime}, #{updatedTime}, #{creatorId}, #{updaterId})")
    @Options(useGeneratedKeys = true, keyProperty = "infoId")
    void sendInfo(BizInfo bizInfo);

    List<BizInfo> getHistory(@Param("userId") Long userId,
                             @Param("otherId") Long otherId,
                             @Param("lastInfoId") Long lastInfoId,
                             @Param("pageSize") Integer pageSize,
                             @Param("receiverType") Integer receiverType);

    // 获取每个会话的最新一条消息
    @Select("SELECT t1.* FROM biz_info t1 " +
            "INNER JOIN ( " +
            "    SELECT CASE WHEN sender_id = #{userId} THEN receiver_id ELSE sender_id END AS other_id, " +
            "           MAX(created_time) AS max_time " +
            "    FROM biz_info " +
            "    WHERE (sender_id = #{userId} OR receiver_id = #{userId}) " +
            "      AND receiver_type = 0 " +
            "    GROUP BY other_id " +
            ") t2 ON ((t1.sender_id = #{userId} AND t1.receiver_id = t2.other_id) " +
            "        OR (t1.receiver_id = #{userId} AND t1.sender_id = t2.other_id)) " +
            "    AND t1.created_time = t2.max_time " +
            "ORDER BY t1.created_time DESC")
    List<BizInfo> getSessionLatestMsg(@Param("userId") Long userId);

    // 统计单聊未读消息数
    @Select("SELECT COUNT(*) FROM biz_info " +
            "WHERE sender_id = #{otherId} " +
            "  AND receiver_id = #{userId} " +
            "  AND receiver_type = 0 " +
            "  AND info_status = 0 " +
            "  AND is_read = 0")
    Integer countUnreadMessages(@Param("userId") Long userId,
                                @Param("otherId") Long otherId);

    // 获取群聊最新消息
    @Select("SELECT * FROM biz_info " +
            "WHERE receiver_id = #{groupId} " +
            "  AND receiver_type = 1 " +
            "  AND info_status = 0 " +
            "ORDER BY created_time DESC LIMIT 1")
    BizInfo getLatestGroupMessage(@Param("groupId") Long groupId);

    // 标记单聊消息为已读
    @Update("UPDATE biz_info SET is_read = 1, updated_time = NOW() " +
            "WHERE sender_id = #{otherId} " +
            "  AND receiver_id = #{userId} " +
            "  AND receiver_type = 0 " +
            "  AND is_read = 0")
    int markMessagesAsRead(@Param("userId") Long userId,
                           @Param("otherId") Long otherId);

    // 标记群聊消息为已读
    @Update("UPDATE biz_info SET is_read = 1, updated_time = NOW() " +
            "WHERE receiver_id = #{groupId} " +
            "  AND receiver_type = 1 " +
            "  AND is_read = 0 " +
            "  AND sender_id != #{userId}")
    int markGroupMessagesAsRead(@Param("groupId") Long groupId,
                                @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM biz_info " +
            "WHERE receiver_id = #{groupId} AND receiver_type = 1 " +
            "AND info_status = 0 AND sender_id != #{userId}")
    Integer countGroupMessages(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM biz_info " +
            "WHERE receiver_id = #{groupId} AND receiver_type = 1 " +
            "AND info_status = 0 AND sender_id != #{userId} " +
            "AND info_id > #{lastReadId}")
    Integer countGroupMessagesAfter(@Param("groupId") Long groupId,
                                    @Param("userId") Long userId,
                                    @Param("lastReadId") Long lastReadId);
}