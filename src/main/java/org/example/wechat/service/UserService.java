package org.example.wechat.service;

import org.example.wechat.pojo.dto.*;
import org.example.wechat.pojo.entity.*;
import org.example.wechat.pojo.vo.UserSearchVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {

    BizUser onUserLogin(UserLoginDTO userLoginDTO);

    BizUser onUserSignup(UserSignupDTO userSignupDTO);

    void sendAuthCode(String telephone);

    void onUserLogout();

    void onUserDel();

    void onUserForgetPwd(UserForgetPwdDTO userForgetPwdDTO);

    void checkAuthCode(String telephone, String code);

    void onUserPassword(UserPasswordDTO userPasswordDTO);

    void onUserProfile(UserProfileDTO userProfileDTO);

    BizUser onUserGetInfo();

    List<BizCategory> OnCategoryList();

    void OnAddCategory(String categoryName);

    List<UserSearchVO> OnUserSearch(String keyWord);

    void OnRenameCategory(String name, Long categoryId);

    String uploadAvatar(MultipartFile file);
}
