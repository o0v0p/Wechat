package org.example.wechat.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.constants.GroupRoleConstants;
import org.example.wechat.common.exception.BusinessException;
import org.example.wechat.common.util.BiRecordUtils;
import org.example.wechat.common.util.OssUtils;
import org.example.wechat.common.util.SnowIdWorker;
import org.example.wechat.common.util.UserContext;
import org.example.wechat.common.util.WsSessionManager;
import org.example.wechat.config.AppConfig;
import org.example.wechat.dao.CategoryMapper;
import org.example.wechat.dao.FriendMapper;
import org.example.wechat.dao.GroupMapper;
import org.example.wechat.dao.GroupUserMapper;
import org.example.wechat.dao.UserMapper;
import org.example.wechat.pojo.dto.*;
import org.example.wechat.pojo.entity.BizCategory;
import org.example.wechat.pojo.entity.BizFriend;
import org.example.wechat.pojo.entity.BizFriendApply;
import org.example.wechat.pojo.entity.BizGroup;
import org.example.wechat.pojo.entity.BizGroupUser;
import org.example.wechat.pojo.entity.BizUser;
import org.example.wechat.pojo.vo.UserSearchVO;
import org.example.wechat.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private OssUtils ossUtils;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SnowIdWorker snowIdWorker;

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private GroupUserMapper groupUserMapper;

    @Autowired
    private GroupMapper groupMapper;

    @Autowired
    private WsSessionManager wsSessionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final long AUTH_CODE_TTL_MINUTES = 5;
    private static final long AUTH_CODE_RATE_SECONDS = 60;
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;
    private static final String AUTH_CODE_KEY_PREFIX = "user:auth:code:";
    private static final String AUTH_CODE_RATE_PREFIX = "verify:rate:";
    private static final String DEFAULT_CATEGORY_NAME = "\u6211\u7684\u597d\u53cb";

    @Override
    @Transactional
    public String uploadAvatar(MultipartFile file) {
        Long userId = UserContext.getUserId();
        BizUser bizUser = userMapper.getByUserId(userId);
        if (bizUser == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw BusinessException.badRequest("只能上传图片文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.badRequest("图片大小不能超过2MB");
        }
        String avatarUrl = ossUtils.uploadFile(file, "avatar");
        BizUser updateUser = new BizUser();
        updateUser.setUserId(userId);
        updateUser.setUserAvatar(avatarUrl);
        userMapper.update(updateUser);
        String oldAvatar = bizUser.getUserAvatar();
        if (oldAvatar != null
                && !oldAvatar.isBlank()
                && !oldAvatar.equals(appConfig.getDefaultPath())) {
            ossUtils.deleteFile(oldAvatar);
        }
        return avatarUrl;
    }

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
    public BizUser onUserSignup(UserSignupDTO userSignupDTO) {
        String username = userSignupDTO.getUserName();
        String phone = userSignupDTO.getTelephone();
        String password = userSignupDTO.getPassword();
        if (username == null || username.trim().isEmpty()) {
            throw BusinessException.badRequest("用户名不可为空");
        }
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw BusinessException.badRequest("请输入有效的手机号码");
        }
        if (password == null || password.length() < 6 || password.length() > 16) {
            throw BusinessException.badRequest("请输入6~16位的非空密码");
        }
        if (userMapper.getByUsername(username) != null) {
            throw BusinessException.conflict("用户名已存在");
        }
        if (userMapper.getByTelephone(phone) != null) {
            throw BusinessException.conflict("手机号已存在");
        }
        checkAuthCode(phone, userSignupDTO.getAuthCode());
        Long userId = snowIdWorker.nextId();
        String encodedPassword = passwordEncoder.encode(password);
        BizUser bizUser = new BizUser();
        BeanUtils.copyProperties(userSignupDTO, bizUser);
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
        BizCategory defaultCategory = new BizCategory();
        defaultCategory.setUserId(userId);
        defaultCategory.setCategoryName(DEFAULT_CATEGORY_NAME);
        categoryMapper.insertCategory(defaultCategory);
        return bizUser;
    }

    @Override
    public void onUserLogout() {
        Long userId = UserContext.getUserId();
        BizUser bizUser = userMapper.getByUserId(userId);
        if (bizUser != null) {
            BizUser updateUser = new BizUser();
            updateUser.setUserId(userId);
            updateUser.setUserStatus(0);
            userMapper.update(updateUser);
        }
        UserContext.remove();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onUserDel(){
        Long userId = UserContext.getUserId();
        // 使用 getByUserIdAny 避免因 is_deleted 过滤导致查不到
        BizUser bizUser = userMapper.getByUserIdAny(userId);
        if (bizUser == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (bizUser.getIsDeleted() != null && bizUser.getIsDeleted() == 1) {
            throw BusinessException.forbidden("账号已注销");
        }

        // ── 1. 校验：是否仍有多人群的群主身份 ──
        List<BizGroupUser> ownerGroups = groupUserMapper.selectOwnerGroupsWithMultiMember(
                userId, GroupRoleConstants.ROLE_OWNER);
        if (ownerGroups != null && !ownerGroups.isEmpty()) {
            // 收集群名列表提示用户
            StringBuilder groupNames = new StringBuilder();
            for (BizGroupUser gu : ownerGroups) {
                BizGroup g = groupMapper.selectById(gu.getGroupId());
                if (g != null) {
                    if (groupNames.length() > 0) groupNames.append("、");
                    groupNames.append("「").append(g.getGroupName()).append("」");
                }
            }
            throw BusinessException.badRequest(
                    "注销前请先转让以下群聊的群主身份：" + groupNames);
        }

        String originalNickname = bizUser.getNickname() != null ? bizUser.getNickname() : "用户";

        // ── 2. 更新用户信息：标记注销、解绑手机、重置头像、追加昵称后缀 ──
        BizUser updateUser = new BizUser();
        updateUser.setUserId(userId);
        updateUser.setIsDeleted(1);
        updateUser.setUserStatus(0);
        // 占位符格式：#DEL + userId末9位，总长≤13字符，满足 varchar(13) 唯一约束，注销后可重新注册同手机号
        String uid = String.valueOf(userId);
        String deactivatedPhone = "#DEL" + uid.substring(uid.length() - 9);
        updateUser.setTelephone(deactivatedPhone);
        updateUser.setUserAvatar(appConfig.getDefaultPath());
        updateUser.setNickname(originalNickname + "（已注销）");
        userMapper.update(updateUser);

        // ── 3. 删除所有好友关系，并推送 friend_deleted 给对方（复用 OnDeleteFriend 逻辑） ──
        List<Long> friendIds = friendMapper.getFriendIdsByUserId(userId);
        if (friendIds != null) {
            for (Long friendId : friendIds) {
                BizFriend forwardFriend = friendMapper.getByFriendId(userId, friendId);
                BizFriend reverseFriend = friendMapper.getByFriendId(friendId, userId);
                BizFriendApply forwardApply = friendMapper.getByRelation(userId, friendId);
                BizFriendApply reverseApply = friendMapper.getByRelation(friendId, userId);
                BiRecordUtils.processDelete(forwardFriend, reverseFriend, forwardApply, reverseApply,
                        friendMapper, friendMapper, userId);

                try {
                    Map<String, Object> notice = new HashMap<>();
                    notice.put("type", "friend_deleted");
                    notice.put("friendId", userId);
                    notice.put("message", originalNickname + "（已注销）已注销账号，好友关系已解除");
                    wsSessionManager.sendToUser(friendId, objectMapper.writeValueAsString(notice));
                } catch (JsonProcessingException e) {
                    log.error("注销-好友删除通知序列化失败: userId={}, friendId={}, error={}", userId, friendId, e.getMessage());
                }
            }
        }

        // ── 4. 退出所有群聊，并推送 group_member_exit 给其他群成员（复用 exitGroup 逻辑） ──
        List<BizGroupUser> allGroups = groupUserMapper.selectAllByUserId(userId);
        if (allGroups != null) {
            for (BizGroupUser gu : allGroups) {
                Long groupId = gu.getGroupId();
                BizGroup group = groupMapper.selectById(groupId);
                if (group == null) continue;

                // 如果是单人群主（已在步骤1阻断了多人群主），保持当前硬删除解散逻辑，本轮不改删除策略
                if (group.getOwnerId().equals(userId)) {
                    List<BizGroupUser> members = groupUserMapper.selectByGroupId(groupId);
                    groupUserMapper.deleteByGroupId(groupId);
                    groupMapper.deleteById(groupId);
                    for (BizGroupUser member : members) {
                        if (!member.getUserId().equals(userId)) {
                            try {
                                Map<String, Object> notice = new HashMap<>();
                                notice.put("type", "group_dismissed");
                                notice.put("groupId", groupId);
                                notice.put("message", "群聊已被解散");
                                wsSessionManager.sendToUser(member.getUserId(), objectMapper.writeValueAsString(notice));
                            } catch (JsonProcessingException e) {
                                log.error("注销-群解散通知序列化失败: userId={}, groupId={}, error={}", userId, groupId, e.getMessage());
                            }
                        }
                    }
                } else {
                    // 普通成员：保持当前硬删除退群逻辑，本轮不改删除策略
                    List<BizGroupUser> members = groupUserMapper.selectByGroupId(groupId);
                    groupUserMapper.deleteByMem(groupId, userId);
                    for (BizGroupUser member : members) {
                        if (!member.getUserId().equals(userId)) {
                            try {
                                Map<String, Object> notice = new HashMap<>();
                                notice.put("type", "group_member_exit");
                                notice.put("groupId", groupId);
                                notice.put("userId", userId);
                                notice.put("nickname", originalNickname + "（已注销）");
                                notice.put("message", originalNickname + "（已注销）已退出群聊");
                                wsSessionManager.sendToUser(member.getUserId(), objectMapper.writeValueAsString(notice));
                            } catch (JsonProcessingException e) {
                                log.error("注销-退群通知序列化失败: userId={}, groupId={}, error={}", userId, groupId, e.getMessage());
                            }
                        }
                    }
                }
            }
        }

        UserContext.remove();
    }

    @Override
    @Transactional
    public void onUserForgetPwd(UserForgetPwdDTO userForgetPwdDTO) {
        String telephone = userForgetPwdDTO.getTelephone();
        String newPassword = userForgetPwdDTO.getNewPassword();
        if (telephone == null || !telephone.matches("^1[3-9]\\d{9}$")) {
            throw BusinessException.badRequest("请输入有效的手机号码");
        }
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 16) {
            throw BusinessException.badRequest("请输入6~16位的非空密码");
        }
        BizUser bizUser = userMapper.getByTelephone(telephone);
        if (bizUser == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (bizUser.getIsDeleted() != null && bizUser.getIsDeleted() == 1) {
            throw BusinessException.forbidden("账号已注销/被禁用");
        }
        checkAuthCode(telephone, userForgetPwdDTO.getAuthCode());
        bizUser.setPassword(passwordEncoder.encode(newPassword));
        userMapper.update(bizUser);
    }

    @Override
    public void sendAuthCode(String telephone) {
        if (telephone == null || !telephone.matches("^1[3-9]\\d{9}$")) {
            throw BusinessException.badRequest("请输入有效的手机号码");
        }
        String rateKey = AUTH_CODE_RATE_PREFIX + telephone;
        Boolean allowed = redisTemplate.opsForValue()
                .setIfAbsent(rateKey, "1", AUTH_CODE_RATE_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(allowed)) {
            throw BusinessException.badRequest("验证码发送过于频繁，请稍后再试");
        }
        String authCode = String.format("%06d", new Random().nextInt(1_000_000));
        String codeKey = AUTH_CODE_KEY_PREFIX + telephone;
        redisTemplate.opsForValue().set(
                codeKey,
                authCode,
                AUTH_CODE_TTL_MINUTES,
                TimeUnit.MINUTES
        );
        log.info("验证码发送成功: telephone={}, redisKey={}, authCode={}, 有效期={}分钟",
                telephone, codeKey, authCode, AUTH_CODE_TTL_MINUTES);
    }

    @Override
    public void checkAuthCode(String telephone, String code) {
        if (telephone == null || telephone.trim().isEmpty()) {
            throw BusinessException.badRequest("手机号不能为空");
        }
        if (code == null || code.trim().isEmpty()) {
            throw BusinessException.badRequest("验证码不能为空");
        }
        telephone = telephone.trim();
        code = code.trim();
        String key = AUTH_CODE_KEY_PREFIX + telephone;
        Object redisValue = redisTemplate.opsForValue().get(key);
        String redisCode = redisValue == null ? null : String.valueOf(redisValue);
        if (redisCode == null) {
            throw BusinessException.badRequest("验证码不存在或已过期");
        }
        if (!redisCode.equals(code)) {
            throw BusinessException.badRequest("验证码错误");
        }
        redisTemplate.delete(key);
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
        List<BizUser> userList = userMapper.searchUsers(keyWord, userId);
        if (userList.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> candidateIds = userList.stream()
                .map(BizUser::getUserId)
                .collect(java.util.stream.Collectors.toList());
        Set<Long> friendSet  = new java.util.HashSet<>(friendMapper.batchFriendIds(userId, candidateIds));
        Set<Long> pendingSet = new java.util.HashSet<>(friendMapper.batchRelationIds(userId, candidateIds, 0));
        List<UserSearchVO> resultVO = new ArrayList<>();
        for (BizUser user : userList) {
            UserSearchVO vo = new UserSearchVO();
            BeanUtils.copyProperties(user, vo);
            if (friendSet.contains(user.getUserId())) {
                vo.setFriendStatus(1);
            } else if (pendingSet.contains(user.getUserId())) {
                vo.setFriendStatus(2);
            } else {
                vo.setFriendStatus(0);
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
        if (DEFAULT_CATEGORY_NAME.equals(oldCategory.getCategoryName())) {
            throw BusinessException.forbidden("默认分组不允许重命名");
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void OnDeleteCategory(Long categoryId) {
        if (categoryId == null) {
            throw BusinessException.badRequest("分组ID不能为空");
        }
        Long userId = UserContext.getUserId();
        BizCategory category = categoryMapper.getById(categoryId);
        if (category == null) {
            throw BusinessException.notFound("分组不存在");
        }
        if (!category.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权删除此分组");
        }
        if (DEFAULT_CATEGORY_NAME.equals(category.getCategoryName())) {
            throw BusinessException.forbidden("默认分组不允许删除");
        }

        Long defaultCategoryId = getOrCreateDefaultCategoryId(userId);
        categoryMapper.moveFriendsToCategory(userId, categoryId, defaultCategoryId);
        int rows = categoryMapper.deleteCategory(userId, categoryId);
        if (rows != 1) {
            throw BusinessException.conflict("删除失败，请重试");
        }
    }

    private Long getOrCreateDefaultCategoryId(Long userId) {
        List<BizCategory> categories = categoryMapper.getByUserId(userId);
        if (categories != null) {
            for (BizCategory category : categories) {
                if (DEFAULT_CATEGORY_NAME.equals(category.getCategoryName())) {
                    return category.getCategoryId();
                }
            }
        }
        BizCategory defaultCategory = new BizCategory();
        defaultCategory.setUserId(userId);
        defaultCategory.setCategoryName(DEFAULT_CATEGORY_NAME);
        categoryMapper.insertCategory(defaultCategory);
        return defaultCategory.getCategoryId();
    }
}
