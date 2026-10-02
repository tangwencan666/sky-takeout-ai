package com.tang.sky.config;

import com.tang.sky.common.*;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 签名、防篡改、过期与issuer检查由成熟JWT库完成；角色只能由服务端登录流程赋予。 */
@Component
public class JwtService {
    private final SecretKey key;
    public JwtService(@Value("${JWT_SECRET:}") String secret) {
        // 本地未配置时生成进程内随机密钥，重启后旧Token失效；部署时必须由环境提供稳定密钥。
        key = secret.isBlank() ? Jwts.SIG.HS256.key().build()
                : Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }
    public String issue(long id, String role) { return issue(id, role, Instant.now().plusSeconds(7200)); }
    public String issue(long id, String role, Instant expiresAt) {
        return Jwts.builder().issuer("sky-takeout-ai").subject(Long.toString(id)).claim("role", role)
                .issuedAt(new Date()).expiration(Date.from(expiresAt)).signWith(key).compact();
    }
    public UserContext.Identity parse(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key).requireIssuer("sky-takeout-ai").build().parseSignedClaims(token).getPayload();
            long id = Long.parseLong(claims.getSubject());
            String role = claims.get("role", String.class);
            if (id <= 0 || claims.getExpiration() == null || !Set.of("USER", "ADMIN").contains(role)) throw new IllegalArgumentException();
            return new UserContext.Identity(id, role);
        } catch (JwtException | IllegalArgumentException e) { throw new BusinessException(ErrorCode.UNAUTHORIZED); }
    }
}
