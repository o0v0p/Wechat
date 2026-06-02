package org.example.wechat.service;

import org.example.wechat.pojo.dto.*;
import org.example.wechat.pojo.vo.FriendApplyVO;
import org.example.wechat.pojo.vo.FriendDetailVO;
import org.example.wechat.pojo.vo.FriendListVO;

import java.util.List;

public interface FriendService {

    List<FriendListVO> OnFriendList();

    void OnAddFriendApply(FriendApplyDTO friendApplyDTO);

    void OnHandleApply(Long userFriendId, Integer status, String remark);

    List<FriendApplyVO> OnFriendApply();

    FriendDetailVO OnFriendProfile(Long friendId);

    void OnMoveCategory(String categoryName, Long friendId);

    void OnUpdateRemark(String remark, Long friendId);

    void OnDeleteFriend(Long friendId);

}
