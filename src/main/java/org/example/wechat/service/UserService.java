package org.example.wechat.service;

import org.example.wechat.pojo.dto.*;
import org.example.wechat.pojo.entity.*;
import org.example.wechat.pojo.vo.UserSearchVO;

import java.util.List;

public interface UserService {

    BizUser onUserLogin(UserLoginDTO userLoginDTO);

    BizUser onUserSignup(UserSignupDTO userSignupDTO);

    void onUserExit();

    void onUserForgetPwd(UserForgetPwdDTO userForgetPwdDTO);

    void onUserPassword(UserPasswordDTO userPasswordDTO);

    void onUserProfile(UserProfileDTO userProfileDTO);

    BizUser onUserGetInfo();

    List<BizCategory> OnCategoryList();

    void OnAddCategory(String categoryName);

    List<UserSearchVO> OnUserSearch(String keyWord);

    void OnRenameCategory(String name, Long categoryId);
}
