package org.example.wechat.controller;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.Result;
import org.example.wechat.common.util.JwtUtils;
import org.example.wechat.common.util.UserContext;
import org.example.wechat.pojo.dto.*;
import org.example.wechat.pojo.entity.BizCategory;
import org.example.wechat.pojo.entity.BizUser;
import org.example.wechat.pojo.vo.*;
import org.example.wechat.service.UserService;
import org.example.wechat.service.TokenService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;


@RestController
@RequestMapping("/user")
@Slf4j
@Tag(name = "用户相关接口")

public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private TokenService tokenService;

    @PostMapping("/avatar")
    @Operation(summary = "上传用户头像", description = "上传用户头像接口")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        log.info("上传用户头像");
        String avatarUrl = userService.uploadAvatar(file);
        return Result.success("头像上传成功", avatarUrl);
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户登录接口")
    public Result<UserLoginVO> onUserLogin(@RequestBody UserLoginDTO userLoginDTO) {
        log.info("用户登录:{}",userLoginDTO);
        BizUser bizUser = userService.onUserLogin(userLoginDTO);
        String token = jwtUtils.generateToken(bizUser.getUserName(), bizUser.getUserId());
        UserInfoVO userInfoVO = new UserInfoVO();
        BeanUtils.copyProperties(bizUser,userInfoVO);
        UserLoginVO userLoginVO = UserLoginVO.builder()
                .token(token)
                .userInfo(userInfoVO)
                .build();
        return Result.success("用户登录成功",userLoginVO);
    }

    @PostMapping("/signup")
    @Operation(summary = "用户注册", description = "用户注册接口")
    public Result<UserSignupVO> onUserSignup(@RequestBody UserSignupDTO userSignupDTO) {

        log.info("用户注册:{}",userSignupDTO);
        BizUser bizUser = userService.onUserSignup(userSignupDTO);
        UserSignupVO userSignupVO = UserSignupVO.builder()
                .userId(bizUser.getUserId())
                .userName(bizUser.getUserName())
                .nickname(bizUser.getNickname())
                .build();

        return Result.success("用户注册成功",userSignupVO);

    }

    @PostMapping({"/code"})
    @Operation(summary = "发送验证码")
    public Result<?> sendAuthCode(@RequestParam String telephone) {
        log.info("发送验证码: telephone={}", telephone);
        userService.sendAuthCode(telephone);
        return Result.success("验证码发送成功");
    }

    @PostMapping("/logout")
    @Operation(summary = "用户退出登录", description = "退出当前登录状态（不注销账号）")
    public Result<Void> onUserExit(HttpServletRequest request) {
        log.info("用户退出登录");
        String token = extractToken(request);
        userService.onUserLogout();
        tokenService.invalidateToken(token);
        return Result.success("退出成功");
    }

    @PostMapping({ "/delete"})
    @Operation(summary = "注销账号", description = "永久注销当前账号，注销前需已转让所有群主身份")
    public Result<Void> onUserDel(HttpServletRequest request) {
        log.info("用户注销账号");
        Long userId = UserContext.getUserId();
        String token = extractToken(request);
        userService.onUserDel();
        tokenService.invalidateToken(token);
        tokenService.invalidateAllTokensForUser(userId);
        return Result.success("账号注销成功");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "重置密码（忘记密码）", description = "忘记密码接口")
    public Result<Void> onUserForgetPwd(@RequestBody UserForgetPwdDTO userForgetPwdDTO) {

        log.info("忘记密码:{}",userForgetPwdDTO);
        userService.onUserForgetPwd(userForgetPwdDTO);
        return Result.success();

    }

    @PostMapping("/password")
    @Operation(summary = "修改密码", description = "修改密码接口")
    public Result<Void> onUserPassword(@RequestBody UserPasswordDTO userPasswordDTO, HttpServletRequest request) {

        log.info("修改密码:{}",userPasswordDTO);
        Long userId = UserContext.getUserId();
        String token = extractToken(request);
        userService.onUserPassword(userPasswordDTO);
        tokenService.invalidateToken(token);
        tokenService.invalidateAllTokensForUser(userId);

        return Result.success("密码修改成功，请重新登录");

    }

    @PutMapping("/profile")
    @Operation(summary = "修改个人资料", description = "修改个人资料接口")
    public Result<Void> onUserPassword(@RequestBody UserProfileDTO userProfileDTO) {
        // TODO : 后期不提供修改username功能，改为换绑手机号
        log.info("修改个人资料:{}",userProfileDTO);
        userService.onUserProfile(userProfileDTO);

        return Result.success();

    }

    @GetMapping("/info")
    @Operation(summary = "获取用户资料", description = "获取用户资料接口")
    public Result<UserInfoVO> getUserInfo() {

        log.info("获取用户资料");
        BizUser bizUser = userService.onUserGetInfo();
        UserInfoVO userInfoVO = new UserInfoVO();
        BeanUtils.copyProperties(bizUser,userInfoVO);
        return Result.success("获取用户信息成功",userInfoVO);
    }

    @GetMapping("/search")
    @Operation(summary = "搜索用户", description = "根据微信号/手机号/昵称搜索用户")
    public Result<List<UserSearchVO>> OnSearchFriend(@RequestParam String keyWord){

        log.info("搜索用户:{}",keyWord);
        List<UserSearchVO> userSearchVO  = userService.OnUserSearch(keyWord);
        return Result.success("搜索用户成功",userSearchVO);

    }

    @GetMapping("/category")
    @Operation(summary = "获取类别列表", description = "获取类别列表接口")
    public Result<List<CategoryVO>> OnCategoryList(){

        log.info("获取类别列表");
        List<BizCategory> categoryList = userService.OnCategoryList();
        List<CategoryVO> categoryVOList = new ArrayList<>();
        for (BizCategory category : categoryList) {
            CategoryVO categoryVO = new CategoryVO();
            BeanUtils.copyProperties(category, categoryVO);
            categoryVOList.add(categoryVO);
        }
        return Result.success("获取好友类别列表成功",categoryVOList);
    }

    @PostMapping("/addCategory")
    @Operation(summary = "增加好友类别", description = "增加类别接口")
    public Result<Void> OnAddCategory(@RequestParam String categoryName){

        log.info("增加好友类别");
        userService.OnAddCategory(categoryName);
        return Result.success("增加好友类别成功");
    }

    @PostMapping("/renameCategory")
    @Operation(summary = "重命名好友类别", description = "重命名类别接口")
    public Result<Void> OnRenameCategory(@RequestParam String name,@RequestParam Long categoryId){

        log.info("重命名好友类别");
        userService.OnRenameCategory(name,categoryId);
        return Result.success("重命名好友类别成功");
    }


    private String extractToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!StringUtils.hasText(token)) {
            token = request.getHeader("token");
        }
        if (!StringUtils.hasText(token)) {
            token = request.getParameter("token");
        }
        return token;
    }
}
