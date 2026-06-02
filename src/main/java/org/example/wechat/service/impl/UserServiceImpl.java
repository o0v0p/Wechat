package org.example.wechat.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.exception.BusinessException;
import org.example.wechat.common.util.SnowIdWorker;
import org.example.wechat.common.util.UserContext;
import org.example.wechat.config.AppConfig;
import org.example.wechat.dao.CategoryMapper;
import org.example.wechat.dao.FriendMapper;
import org.example.wechat.dao.UserMapper;
import org.example.wechat.pojo.dto.*;
import org.example.wechat.pojo.entity.BizCategory;
import org.example.wechat.pojo.entity.BizUser;
import org.example.wechat.pojo.entity.BizFriendApply;
import org.example.wechat.pojo.vo.UserSearchVO;
import org.example.wechat.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SnowIdWorker snowIdWorker;

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Override

    public BizUser onUserLogin(UserLoginDTO userLoginDTO){
        String telephone = userLoginDTO.getTelephone();
        String password =  userLoginDTO.getPassword();
        if(!telephone.matches("^1[3-9]\\d{9}$")){
            throw BusinessException.badRequest("请输入有效的手机号码");
        }
        BizUser bizUser = userMapper.getByTelephone(telephone);
        if(bizUser == null){
            throw BusinessException.notFound("用户不存在");
        }
        if (!passwordEncoder.matches(password, bizUser.getPassword())) {
            throw BusinessException.badRequest("密码错误");
        }
        if (bizUser.getIsDeleted() == 1) {
            throw BusinessException.forbidden("账号已注销/被禁用");
        }
        UserContext.setUserId(bizUser.getUserId());
        UserContext.setUserName(bizUser.getUserName());
        bizUser.setUserStatus(1);
        bizUser.setLastLoginTime(LocalDateTime.now());
        userMapper.update(bizUser);

        //TODO:记录登录日志到 `biz_login_log` 表
        return bizUser;

    }

    @Override
    @Transactional
    public BizUser onUserSignup(UserSignupDTO userSignupDTO){

        String username = userSignupDTO.getUserName();
        String phone = userSignupDTO.getTelephone();
        String password =  userSignupDTO.getPassword();

        if(username == null){
            throw BusinessException.badRequest("用户名不可为空");
        }
        if(!phone.matches("^1[3-9]\\d{9}$")){
            throw BusinessException.badRequest("请输入有效的手机号码");
        }
        if(password == null || password.length() < 6 || password.length() > 16){
            throw BusinessException.badRequest("请输入6~16位的非空密码");
        }
        // TODO: 验证码校验
        if(!userSignupDTO.getAuthCode().equals("123456")){
            throw BusinessException.badRequest("验证码错误");
        }
        if(userMapper.getByUsername(username) != null){
            throw BusinessException.conflict("用户名已存在");
        }
        if(userMapper.getByTelephone(phone) != null){
            throw BusinessException.conflict("手机号已存在");
        }
        Long userId = snowIdWorker.nextId();
        // BCrypt 加密
        String encodedPassword = passwordEncoder.encode(password);

        BizUser bizUser = new BizUser();
        BeanUtils.copyProperties(userSignupDTO,bizUser);

        // TODO: createTime,updateTime,createId,updateId注解
        bizUser.setUserId(userId);
        bizUser.setUserName(username);
        bizUser.setPassword(encodedPassword);
        bizUser.setUserAvatar(appConfig.getDefaultPath());
        bizUser.setUserEmail(null);
        bizUser.setUserSignature(null);
        bizUser.setUserSex(null);
        bizUser.setUserStatus(0);
        bizUser.setIsDeleted(0);
        bizUser.setLastLoginTime(null);

        userMapper.insert(bizUser);
        return bizUser;
    }

    @Override
    @Transactional
    public void onUserExit(){

        Long userId = UserContext.getUserId();
        BizUser bizUser = userMapper.getByUserId(userId);
        if (bizUser == null) {
            throw BusinessException.notFound("用户不存在");
        }
        bizUser.setUserId(userId);
        bizUser.setUserStatus(0);
        userMapper.update(bizUser);
        UserContext.remove();

    }

    @Override
    @Transactional
    public void onUserForgetPwd(UserForgetPwdDTO userForgetPwdDTO){

        String telephone = userForgetPwdDTO.getTelephone();
        String newPassword =  userForgetPwdDTO.getNewPassword();

        if(!telephone.matches("^1[3-9]\\d{9}$")){
            throw BusinessException.badRequest("请输入有效的手机号码");
        }
        if(newPassword == null || newPassword.length() < 6 || newPassword.length() > 16){
            throw BusinessException.badRequest("请输入6~16位的非空密码");
        }
        BizUser bizUser = userMapper.getByTelephone(telephone);
        if(bizUser == null){
            throw BusinessException.notFound("用户不存在");
        }
        if (bizUser.getIsDeleted() == 1) {
            throw BusinessException.forbidden("账号已注销/被禁用");
        }
        // TODO: 验证码校验
        if(!userForgetPwdDTO.getAuthCode().equals("123456")){
            throw BusinessException.badRequest("验证码错误");
        }
        bizUser.setPassword(passwordEncoder.encode(newPassword));
        userMapper.update(bizUser);

    }

    @Override
    @Transactional
    public void onUserPassword(UserPasswordDTO userPasswordDTO){

        Long userId = UserContext.getUserId();
        BizUser bizUser = userMapper.getByUserId(userId);
        if (bizUser == null) {
            throw BusinessException.notFound("用户不存在");
        }
        String oldPassword = userPasswordDTO.getOldPassword();
        String newPassword =  userPasswordDTO.getNewPassword();
        if(newPassword == null || newPassword.length() < 6 || newPassword.length() > 16 || newPassword.equals(oldPassword)){
            throw BusinessException.badRequest("请输入6~16位的非空新密码");
        }
        if (!passwordEncoder.matches(oldPassword, bizUser.getPassword())) {
            throw BusinessException.badRequest("旧密码错误");
        }
        bizUser.setPassword(passwordEncoder.encode(newPassword));
        userMapper.update(bizUser);

    }

    @Override
    @Transactional
    public void onUserProfile(UserProfileDTO userProfileDTO) {
        Long userId = UserContext.getUserId();
        BizUser bizUser = userMapper.getByUserId(userId);
        if (bizUser == null) {
            throw 	BusinessException.notFound("用户不存在");
        }
        String newUsername = userProfileDTO.getUserName();
        if (newUsername != null && !newUsername.equals(bizUser.getUserName())) {
            BizUser existingUser = userMapper.getByUsername(newUsername);
            if (existingUser != null && !existingUser.getUserId().equals(userId)) {
                throw BusinessException.conflict("用户名已被其他用户使用");
            }
        }
        BizUser updateUser = new BizUser();
        updateUser.setUserId(userId);
        if (userProfileDTO.getUserName() != null) {
            updateUser.setUserName(userProfileDTO.getUserName());
        }
        if (userProfileDTO.getNickname() != null) {
            updateUser.setNickname(userProfileDTO.getNickname());
        }
        if (userProfileDTO.getUserAvatar() != null) {
            updateUser.setUserAvatar(userProfileDTO.getUserAvatar());
        }
        if (userProfileDTO.getUserEmail() != null) {
            updateUser.setUserEmail(userProfileDTO.getUserEmail());
        }
        if (userProfileDTO.getUserSignature() != null) {
            updateUser.setUserSignature(userProfileDTO.getUserSignature());
        }
        if (userProfileDTO.getUserSex() != null) {
            updateUser.setUserSex(userProfileDTO.getUserSex());
        }
        userMapper.update(updateUser);
    }

    @Override
    @Transactional
    public BizUser onUserGetInfo(){
        Long userId = UserContext.getUserId();
        BizUser bizuser = userMapper.getByUserId(userId);
        if (bizuser == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return bizuser;
    }

    @Override
    @Transactional
    public List<UserSearchVO> OnUserSearch(String keyWord){
        if (keyWord == null || keyWord.trim().isEmpty()) {
            throw BusinessException.badRequest("搜索关键词不能为空");
        }
        Long userId = UserContext.getUserId();
        List<BizUser> UserList = userMapper.searchUsers(keyWord,userId);
        List<UserSearchVO> resultVO = new ArrayList<>();
        for (BizUser user : UserList) {
            UserSearchVO vo = new UserSearchVO();
            BeanUtils.copyProperties(user, vo);
            int isFriend = friendMapper.checkIsFriend(userId, user.getUserId());
            if (isFriend > 0) {
                vo.setFriendStatus(1);
            } else {
                BizFriendApply apply = friendMapper.getPendingApply(userId, user.getUserId());
                vo.setFriendStatus(apply != null ? 2 : 0);
            }
            resultVO.add(vo);
        }
        return resultVO;
    }

    @Override
    public List<BizCategory> OnCategoryList(){
        Long userId = UserContext.getUserId();
        if (userId == null || userId <= 0) {
            return Collections.emptyList();
        }
        return categoryMapper.getByUserId(userId);

    }

    @Override
    public void OnAddCategory(String categoryName){
        Long userId = UserContext.getUserId();
        if (userId == null || categoryName == null ||userId <= 0) {
            throw BusinessException.badRequest("组别名与用户ID不可为空");
        }
        if (categoryMapper.existsByName(userId, categoryName)) {
            throw BusinessException.conflict("分类名称已存在：" + categoryName);
        }
        BizCategory bizCategory = new BizCategory();
        bizCategory.setCategoryName(categoryName);
        bizCategory.setUserId(userId);
        categoryMapper.insertCategory(bizCategory);

    }

    @Override
    @Transactional
    public void OnRenameCategory(String name, Long categoryId){
        if (name == null || name.trim().isEmpty()) {
            throw BusinessException.badRequest("分类名称不能为空");
        }
        Long userId = UserContext.getUserId();
        BizCategory oldCategory = categoryMapper.getById(categoryId);
        if (oldCategory == null) {
            throw BusinessException.notFound("分类不存在");
        }
        if (!oldCategory.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权修改此分类");
        }
        if (categoryMapper.existsByName(userId, name)) {
            throw BusinessException.conflict("分类名称已存在：" + name);
        }
        BizCategory category = new BizCategory();
        category.setCategoryId(categoryId);
        category.setCategoryName(name);
        category.setUserId(userId);
        int rows = categoryMapper.updateCategory(category);
        if (rows != 1) {
            throw BusinessException.conflict("修改失败，请重试");
        }
    }
}
