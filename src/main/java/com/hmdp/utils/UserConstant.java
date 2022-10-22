package com.hmdp.utils;

import com.hmdp.entity.User;

/**
 * @author Dhx_
 * @className UserContant
 * @description TODO
 * @date 2022/10/8 19:46
 */
public class UserConstant {

    /**
     * 验证码
     */
    public static final String VERIFICATION_CODE="login:code:";

    /**
     * 登录用户
     */
    public static final String USER_LOGIN_STATUS="userLoginStatus";

    /**
     * 默认用户名为 user_ + RandomUtil.randomString(10);
     */
    public static final String DEFAULT_USER_NAME_PREFIX = "user_";
}
