package com.tang.sky.interceptor;

import com.tang.sky.common.*;
import com.tang.sky.config.JwtService;
import com.tang.sky.service.AuthService;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/** JWT提供身份，后台路径额外检查ADMIN；不接受请求参数中的userId作为身份来源。 */
@Component
public class AuthInterceptor implements AsyncHandlerInterceptor {
    private final JwtService jwt;
    private final AuthService auth;
    public AuthInterceptor(JwtService jwt, AuthService auth) { this.jwt=jwt; this.auth=auth; }
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        var identity = jwt.parse(header.substring(7));
        // 使用MVC已经解析的路由，避免servletPath为空或URL编码形式影响角色判定。
        String route = String.valueOf(request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        if (route.startsWith("/admin/") && !identity.role().equals("ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (route.startsWith("/user/") && !identity.role().equals("USER")) throw new BusinessException(ErrorCode.FORBIDDEN);
        auth.verifyActive(identity);
        UserContext.set(identity);
        return true;
    }
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception e) { UserContext.clear(); }
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) { UserContext.clear(); }
}
