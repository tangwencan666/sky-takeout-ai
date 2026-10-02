package com.tang.sky.service;

import com.tang.sky.pojo.AuthModels.*;
import com.tang.sky.common.UserContext;

/** 认证规则集中在Service，HTTP入口与后续工具都不能自行选择或伪造身份。 */
public interface AuthService {
    UserView register(Credentials input);
    LoginView login(Credentials input, boolean admin);
    UserView current();
    void verifyActive(UserContext.Identity identity);
}
