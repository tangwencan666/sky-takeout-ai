package com.tang.sky;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tang.sky.common.UserContext;
import com.tang.sky.mapper.AccountMapper;
import com.tang.sky.pojo.AuthModels.Credentials;
import com.tang.sky.service.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 穿过JWT拦截器到真实数据库验证分类API，管理员与用户视图各有明确语义。 */
@SpringBootTest @AutoConfigureMockMvc(print=MockMvcPrint.NONE) @Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class CatalogApiIntegrationTest {
    @Autowired AuthService auth;
    @Autowired AccountMapper accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @AfterEach void clear() { UserContext.clear(); }

    @Test void adminCanManageDisabledCategoryAndUserCannotSeeIt() throws Exception {
        String name="cat_"+UUID.randomUUID().toString().replace("-", "").substring(0,16);
        var credentials=new Credentials(name,"CategoryTest!29");auth.register(credentials);
        jdbc.update("INSERT INTO employee(username,password_hash) VALUES(?,?)",name,accounts.findUser(name).passwordHash());
        String admin="Bearer "+auth.login(credentials,true).token();String user="Bearer "+auth.login(credentials,false).token();
        var created=mvc.perform(post("/admin/categories").header("Authorization",admin).contentType("application/json")
                .content(json.writeValueAsString(new com.tang.sky.pojo.CatalogModels.CategoryInput(name,1,0))))
                .andExpect(status().isOk()).andReturn();
        long id=json.readTree(created.getResponse().getContentAsString()).get("data").asLong();
        mvc.perform(put("/admin/categories/"+id+"/status?status=0").header("Authorization",admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/categories/page").param("keyword",name).param("type","1").param("size","1").header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.records[0].status").value(0));
        mvc.perform(get("/admin/categories").header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.id == "+id+")]").isNotEmpty());
        mvc.perform(get("/user/categories").header("Authorization",user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.id == "+id+")]").isEmpty());
        mvc.perform(put("/admin/categories/"+id+"/status?status=1").header("Authorization",admin)).andExpect(status().isOk());
        mvc.perform(get("/user/categories").header("Authorization",user)).andExpect(jsonPath("$.data[?(@.id == "+id+")]").isNotEmpty());
        mvc.perform(delete("/admin/categories/"+id).header("Authorization",admin)).andExpect(status().isOk());
        mvc.perform(post("/admin/dishes").header("Authorization",admin).contentType("application/json")
                .content("{\"categoryId\":1,\"name\":\"错误图片\",\"price\":1.00,\"description\":\"\",\"image\":\"javascript:alert(1)\",\"flavors\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
