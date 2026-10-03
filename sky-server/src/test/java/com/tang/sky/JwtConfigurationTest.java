package com.tang.sky;

import com.tang.sky.common.*;
import com.tang.sky.config.JwtService;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 测试密钥每次运行生成，源码不保存固定签名密钥。 */
class JwtConfigurationTest {
    private String secret() { return Base64.getEncoder().encodeToString(Jwts.SIG.HS256.key().build().getEncoded()); }

    @Test void missingOrWeakConfigurationFailsWithoutRevealingValue() {
        assertThrows(IllegalStateException.class,()->new JwtService(""));
        byte[] weak=new byte[16];new java.security.SecureRandom().nextBytes(weak);
        String value=Base64.getEncoder().encodeToString(weak);
        var error=assertThrows(IllegalStateException.class,()->new JwtService(value));
        assertFalse(error.getMessage().contains(value));
    }

    @Test void issuedTokenNeedsSameKeyValidIdentityAndFutureExpiry() {
        var jwt=new JwtService(secret());
        var identity=jwt.parse(jwt.issue(12,"USER"));
        assertEquals(12,identity.id());assertEquals("USER",identity.role());
        var other=new JwtService(secret());
        assertEquals(ErrorCode.UNAUTHORIZED,assertThrows(BusinessException.class,()->other.parse(jwt.issue(12,"USER"))).error());
        assertEquals(ErrorCode.UNAUTHORIZED,assertThrows(BusinessException.class,()->jwt.parse(jwt.issue(12,"USER",Instant.now().minusSeconds(1)))).error());
        assertEquals(ErrorCode.UNAUTHORIZED,assertThrows(BusinessException.class,()->jwt.parse(jwt.issue(12,"ROOT"))).error());
        assertEquals(ErrorCode.UNAUTHORIZED,assertThrows(BusinessException.class,()->jwt.parse(jwt.issue(0,"USER"))).error());
    }
}
