package com.tang.sky.config;

import com.tang.sky.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/** 默认保护全部业务路径，只精确放行注册登录，避免将整个用户模块设为匿名。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor interceptor;
    public WebConfig(AuthInterceptor interceptor) { this.interceptor=interceptor; }
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/user/**", "/admin/**")
                .excludePathPatterns("/user/auth/register", "/user/auth/login", "/admin/auth/login");
    }
}
