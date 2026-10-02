package com.tang.sky;

import com.tang.sky.common.*;
import com.tang.sky.config.JwtService;
import com.tang.sky.mapper.AccountMapper;
import com.tang.sky.pojo.AuthModels.*;
import com.tang.sky.service.AuthService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实MySQL事务测试，随机账号并回滚；不污染用户现有业务数据。 */
@SpringBootTest @AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE) @Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class AuthIntegrationTest {
    @Autowired AuthService auth;
    @Autowired AccountMapper accounts;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    Credentials input;
    @BeforeEach void setup() { input = new Credentials("t_"+UUID.randomUUID().toString().replace("-", "").substring(0,20),"LearningTest!29"); }
    @AfterEach void cleanup() { UserContext.clear(); }
    @Test void registerLoginAndProtectedResource() throws Exception {
        var user = auth.register(input);
        assertNotEquals(input.password(), accounts.findUser(input.username()).passwordHash());
        var login = auth.login(input,false);
        mvc.perform(get("/user/me").header("Authorization","Bearer "+login.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(user.id()))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        assertThrows(BusinessException.class, UserContext::require);
    }
    @Test void badAndMissingAccountsUseSameError() {
        auth.register(input);
        var wrong = assertThrows(BusinessException.class, () -> auth.login(new Credentials(input.username(),"WrongPass!29"),false));
        var missing = assertThrows(BusinessException.class, () -> auth.login(new Credentials("missing_"+input.username().substring(0,10),input.password()),false));
        assertEquals(wrong.getMessage(),missing.getMessage());
        assertThrows(BusinessException.class,()->auth.register(input));
    }
    @Test void rejectsMissingInvalidExpiredAndWrongRoleTokens() throws Exception {
        var user=auth.register(input);
        mvc.perform(get("/user/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/user/me").header("Authorization","Bearer tampered")).andExpect(status().isUnauthorized());
        mvc.perform(get("/user/me").header("Authorization","Bearer "+jwt.issue(user.id(),"USER",Instant.now().minusSeconds(10))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/me").header("Authorization","Bearer "+jwt.issue(user.id(),"USER")))
                .andExpect(status().isForbidden());
    }
    @Test void usersAreIsolatedAndContextIsCleared() throws Exception {
        var a=auth.register(input);
        var b=auth.register(new Credentials(input.username()+"b",input.password()));
        for (var user : java.util.List.of(a,b)) {
            mvc.perform(get("/user/me").header("Authorization","Bearer "+jwt.issue(user.id(),"USER")))
                    .andExpect(jsonPath("$.data.id").value(user.id()));
            assertThrows(BusinessException.class,UserContext::require);
        }
        mvc.perform(post("/user/auth/register").contentType("application/json").content("{\"username\":\"x\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void rejectsForgedIdentityAndAdminAnalyticsAccess() throws Exception {
        var user = auth.register(input);
        String token = jwt.issue(user.id(), "USER");
        mvc.perform(post("/user/ai/chat").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"message\":\"午餐\",\"userId\":999,\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/admin/ai/chat").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"message\":\"营收\",\"days\":7}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/user/auth/register").contentType("application/json")
                .content("{\"username\":\"forged_admin\",\"password\":\"FakeTest!29\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        assertNull(accounts.findUser("forged_admin"));
    }
}
