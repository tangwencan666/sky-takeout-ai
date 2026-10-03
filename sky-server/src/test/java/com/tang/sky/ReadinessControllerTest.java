package com.tang.sky;

import com.tang.sky.controller.ReadinessController;
import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 不连接真实数据库：在MVC边界注入连接结果，校验假就绪与敏感错误回显的回归。 */
class ReadinessControllerTest {
    private DataSource source;
    private Connection connection;
    private Statement statement;
    private MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        source=mock(DataSource.class);connection=mock(Connection.class);statement=mock(Statement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("sky_takeout_ai");
        when(connection.createStatement()).thenReturn(statement);
        mvc=MockMvcBuilders.standaloneSetup(new ReadinessController(source)).build();
    }

    @Test void readyRequiresActualConnectionAndSuccessfulQuery() throws Exception {
        var rows=mock(ResultSet.class);when(statement.executeQuery("SELECT 1")).thenReturn(rows);
        when(rows.next()).thenReturn(true);when(rows.getInt(1)).thenReturn(1);
        mvc.perform(get("/health/ready")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ready"))
                .andExpect(jsonPath("$.data.database").value("sky_takeout_ai"));
        verify(source).getConnection();verify(statement).setQueryTimeout(3);verify(statement).executeQuery("SELECT 1");
        verify(rows).close();verify(statement).close();verify(connection).close();
    }

    @Test void authenticationFailureIsUnavailableWithoutDriverDiagnostic() throws Exception {
        when(source.getConnection()).thenThrow(new SQLInvalidAuthorizationSpecException("private-diagnostic-marker","28000"));
        mvc.perform(get("/health/ready")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("数据库暂不可用，请检查数据库服务与连接配置"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(content().string(not(containsString("private-diagnostic-marker"))));
        verify(connection,never()).createStatement();
    }

    @Test void anotherCatalogIsRejectedBeforeAnySql() throws Exception {
        when(connection.getCatalog()).thenReturn("unrelated_catalog");
        mvc.perform(get("/health/ready")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(content().string(not(containsString("unrelated_catalog"))));
        verify(connection,never()).createStatement();verify(connection).close();
    }

    @Test void connectionWithoutWorkingQueryIsNotReady() throws Exception {
        when(statement.executeQuery("SELECT 1")).thenThrow(new SQLException("private-query-diagnostic","08006"));
        mvc.perform(get("/health/ready")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(content().string(not(containsString("private-query-diagnostic"))));
        verify(statement).close();verify(connection).close();
    }
}
