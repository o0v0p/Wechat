package org.example.wechat.service;

import org.example.wechat.pojo.dto.*;
import org.example.wechat.pojo.entity.*;
import org.example.wechat.pojo.vo.UserSearchVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {

    BizUser login(UserLoginDTO userLoginDTO);

    void recordLoginLog(HttpServletRequest request, String telephone, Long userId, Integer loginResult, String returnMsg);

    BizUser signup(UserSignupDTO userSignupDTO);

    void sendAuthCode(String telephone);

    void logout();

    void deleteCurrentUser();

    void resetPassword(UserForgetPwdDTO userForgetPwdDTO);

    void checkAuthCode(String telephone, String code);

    void changePassword(UserPasswordDTO userPasswordDTO);

    void updateProfile(UserProfileDTO userProfileDTO);

    BizUser getCurrentUserInfo();

    List<BizCategory> listCategories();

    void addCategory(String categoryName);

    List<UserSearchVO> searchUsers(String keyWord);

    void renameCategory(String name, Long categoryId);

    void deleteCategory(Long categoryId);

    String uploadAvatar(MultipartFile file);
}
