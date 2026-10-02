package com.tang.sky.service.impl;

import com.tang.sky.common.*;
import com.tang.sky.config.JwtService;
import com.tang.sky.mapper.AccountMapper;
import com.tang.sky.pojo.AuthModels.*;
import com.tang.sky.service.AuthService;
import java.nio.charset.StandardCharsets;
import org.slf4j.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BCrypt带随机盐；重复用户名依赖数据库唯一约束兜底，避免并发注册绕过先查后写。 */
@Service
public class AuthServiceImpl implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private final AccountMapper mapper;
    private final JwtService jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final String dummyHash = encoder.encode(UUIDHolder.VALUE);
    private static class UUIDHolder { static final String VALUE = java.util.UUID.randomUUID().toString(); }
    public AuthServiceImpl(AccountMapper mapper, JwtService jwt) { this.mapper = mapper; this.jwt = jwt; }
    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw new BusinessException(ErrorCode.BAD_REQUEST, "密码UTF-8长度不能超过72字节");
    }
    @Transactional
    public UserView register(Credentials input) {
        validatePassword(input.password());
        try { mapper.insertUser(input.username(), encoder.encode(input.password())); }
        catch (DuplicateKeyException e) { throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在"); }
        Account account = mapper.findUser(input.username());
        return new UserView(account.id(), account.username(), "USER");
    }
    public LoginView login(Credentials input, boolean admin) {
        validatePassword(input.password());
        Account account = admin ? mapper.findEmployee(input.username()) : mapper.findUser(input.username());
        // 不存在账号仍做一次哈希校验，统一失败消息，减少用户名探测信息。
        boolean matches = encoder.matches(input.password(), account == null ? dummyHash : account.passwordHash());
        if (account == null || !matches || account.status() != 1) throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        String role = admin ? "ADMIN" : "USER";
        log.info("Login success accountId={} role={}", account.id(), role);
        return new LoginView(jwt.issue(account.id(), role), new UserView(account.id(), account.username(), role));
    }
    public void verifyActive(UserContext.Identity identity) {
        Account account = "ADMIN".equals(identity.role()) ? mapper.employeeById(identity.id()) : mapper.userById(identity.id());
        if (account == null || account.status() != 1) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    public UserView current() {
        var identity = UserContext.require();
        Account account = "ADMIN".equals(identity.role()) ? mapper.employeeById(identity.id()) : mapper.userById(identity.id());
        if (account == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return new UserView(account.id(), account.username(), identity.role());
    }
}
