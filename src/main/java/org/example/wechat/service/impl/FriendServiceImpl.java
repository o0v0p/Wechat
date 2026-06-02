package org.example.wechat.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.exception.BusinessException;
import org.example.wechat.common.util.BiRecordUtils;
import org.example.wechat.common.util.UserContext;
import org.example.wechat.dao.BlacklistMapper;
import org.example.wechat.dao.CategoryMapper;
import org.example.wechat.dao.FriendMapper;
import org.example.wechat.dao.UserMapper;
import org.example.wechat.pojo.dto.FriendApplyDTO;
import org.example.wechat.pojo.entity.BizBlacklist;
import org.example.wechat.pojo.entity.BizFriend;
import org.example.wechat.pojo.entity.BizUser;
import org.example.wechat.pojo.entity.BizFriendApply;
import org.example.wechat.pojo.vo.FriendApplyVO;
import org.example.wechat.pojo.vo.FriendDetailVO;
import org.example.wechat.pojo.vo.FriendListVO;
import org.example.wechat.service.FriendService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class FriendServiceImpl implements FriendService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private BlacklistMapper blacklistMapper;

    @Override
    public List<FriendListVO> OnFriendList() {
        Long userId = UserContext.getUserId();
        List<FriendListVO> friendList = friendMapper.getfriendList(userId);
        if (friendList == null || friendList.isEmpty()) {
            return Collections.emptyList();
        }
        return friendList;
    }

    @Override
    public void OnAddFriendApply(FriendApplyDTO friendApplyDTO){

        Long userId = UserContext.getUserId();
        Long friendId = friendApplyDTO.getFriendId();
        if (userId.equals(friendId) || friendId == null) {
            throw BusinessException.badRequest("好友ID不可为用户ID或NULL");
        }
        BizUser targetUser = userMapper.getByUserId(friendId);
        if (targetUser == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (targetUser.getIsDeleted() == 1) {
            throw BusinessException.forbidden("对方账号已被注销或禁用");
        }
        if (friendMapper.checkIsFriend(userId, friendId) > 0) {
            throw BusinessException.conflict("已经是好友关系");
        }
        BizBlacklist blacklist = blacklistMapper.findByUser(friendId, userId);
        if (blacklist != null) {
            throw new BusinessException("对方已将您拉黑，无法添加好友");
        }
        BizFriendApply pendingApply = friendMapper.getPendingApply(userId, friendId);
        if (pendingApply != null) {
            throw BusinessException.conflict("已发送过好友申请...");
        }
        BizFriendApply reverseApply = friendMapper.getPendingApply(friendId, userId);
        if (reverseApply != null) {
            throw BusinessException.conflict("对方已向你发送好友申请...");
        }

        BizFriendApply bizFriendApply = new BizFriendApply();
        BeanUtils.copyProperties(friendApplyDTO, bizFriendApply);
        bizFriendApply.setUserId(UserContext.getUserId());
        bizFriendApply.setStatus(0);
        friendMapper.addFriendApply(bizFriendApply);

    }

    @Override
    @Transactional
    public void OnHandleApply(Long userFriendId,Integer status,String remark){
        Long userId = UserContext.getUserId();
        BizFriendApply receiveApply = friendMapper.getByApplyId(userFriendId);
        if (receiveApply == null) {
            throw BusinessException.notFound("申请记录不存在");
        }
        if (!receiveApply.getFriendId().equals(userId)) {
            throw BusinessException.forbidden("无权处理此申请");
        }
        if (receiveApply.getStatus() != 0) {
            throw BusinessException.conflict("该申请已被处理");
        }
        if (status == 1) {  // 同意

            BizUser currentUser = userMapper.getByUserId(userId);
            BizUser targetUser = userMapper.getByUserId(receiveApply.getUserId());
            if (currentUser == null || targetUser == null) {
                throw BusinessException.notFound("用户信息不存在");
            }
            Long defaultCategory = 1L;           // TODO : 设默认分组
            BiRecordUtils.BiRecordPair pair = BiRecordUtils.createFullBiRecord(receiveApply, userId, currentUser, targetUser, defaultCategory,remark);
            BizFriendApply forwardRecord = pair.getForward();
            BizFriendApply reverseRecord = pair.getReverse();
            BizFriend forwardFriend = pair.getForwardFriend();
            BizFriend reverseFriend = pair.getReverseFriend();
            friendMapper.updateApply(forwardRecord);
            friendMapper.addFriendApply(reverseRecord);
            friendMapper.addFriend(forwardFriend);
            friendMapper.addFriend(reverseFriend);

        } else if (status == 2) {  // 拒绝
            BizFriendApply rejectedRecord = BiRecordUtils.createFromRejectedApply(receiveApply);
            friendMapper.updateApply(rejectedRecord);
        } else {
            throw BusinessException.badRequest("无效的操作状态");
        }
    }

    @Override
    @Transactional
    public List<FriendApplyVO> OnFriendApply(){
        Long userId = UserContext.getUserId();
        List<FriendApplyVO> result = friendMapper.getApplyByUser(userId);
        return result != null ? result : new ArrayList<>();
    }

    @Override
    @Transactional
    public FriendDetailVO OnFriendProfile(Long friendId){

        Long userId = UserContext.getUserId();
        BizUser bizUser = userMapper.getByUserId(friendId);
        if(bizUser == null){
            throw BusinessException.notFound("该用户不存在");
        }
        if (friendMapper.checkIsFriend(userId, friendId) <= 0) {
            throw BusinessException.forbidden("TA与你还不是好友");
        }
        FriendDetailVO friendDetailVO =  new FriendDetailVO();
        BeanUtils.copyProperties(bizUser,friendDetailVO);
        BizFriend bizFriend = friendMapper.getByFriendId(friendId,userId);
        BeanUtils.copyProperties(bizFriend,friendDetailVO);
        friendDetailVO.setChatId(friendMapper.getChatId(userId,friendId));
        return friendDetailVO;

    }

    @Override
    @Transactional
    public void OnMoveCategory(String categoryName, Long friendId){
        if(categoryName == null || friendId == null)
            throw BusinessException.badRequest("移动分组名与ID不可为空");
        Long userId = UserContext.getUserId();
        if(!categoryMapper.existsByName(userId, categoryName))
            throw BusinessException.notFound("分组不存在：" + categoryName);
        if(friendMapper.checkIsFriend(userId, friendId) <= 0) {
            throw BusinessException.notFound("好友关系不存在");
        }
        int rows = friendMapper.moveCategory(userId,friendId,categoryName);;
        if (rows != 1) {
            throw BusinessException.conflict("移动失败，请重试");
        }
    }

    @Override
    public void OnUpdateRemark(String remark, Long friendId){
        if(remark == null || friendId == null)
            throw BusinessException.badRequest("新备注与好友ID不可为空");
        Long userId = UserContext.getUserId();
        int rows = friendMapper.updateRemark(remark,friendId,userId);
        if (rows != 1) {
            throw BusinessException.conflict("修改失败，请重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void OnDeleteFriend(Long friendId){
        if(friendId == null)
            throw BusinessException.badRequest("好友ID不可为空");
        Long userId = UserContext.getUserId();
        if (userId.equals(friendId)) {
            throw BusinessException.badRequest("不能删除自己");
        }
        if(friendMapper.checkIsFriend(userId,friendId) <= 0)
            throw BusinessException.notFound("好友关系不存在");
        BizFriend forwardFriend = friendMapper.getByFriendId(userId, friendId);
        BizFriend reverseFriend = friendMapper.getByFriendId(friendId, userId);
        BizFriendApply forwardApply=friendMapper.getByRelation(userId, friendId);
        BizFriendApply reverseApply=friendMapper.getByRelation(friendId, userId);
        BiRecordUtils.processDelete(forwardFriend, reverseFriend, forwardApply, reverseApply, friendMapper, friendMapper, userId);
    }

}
