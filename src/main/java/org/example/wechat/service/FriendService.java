package org.example.wechat.service;

import org.example.wechat.pojo.dto.*;
import org.example.wechat.pojo.vo.FriendApplyVO;
import org.example.wechat.pojo.vo.FriendDetailVO;
import org.example.wechat.pojo.vo.FriendListVO;

import java.util.List;

public interface FriendService {

    List<FriendListVO> listFriends();

    void applyFriend(FriendApplyDTO friendApplyDTO);

    void handleApply(Long userFriendId, Integer status, String remark);

    List<FriendApplyVO> listFriendApplies();

    FriendDetailVO getFriendProfile(Long friendId);

    void moveCategory(String categoryName, Long friendId);

    void updateRemark(String remark, Long friendId);

    void deleteFriend(Long friendId);

    void updateFriendSettings(org.example.wechat.pojo.dto.FriendSettingsDTO dto);

}
