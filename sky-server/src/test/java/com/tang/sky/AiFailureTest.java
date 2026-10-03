package com.tang.sky;

import com.tang.sky.ai.security.AiFailure;
import com.tang.sky.ai.security.AiFailure.Category;
import com.tang.sky.ai.service.*;
import com.tang.sky.controller.ai.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiFailureTest {
    @Test void upstreamStatusesAndKnownCodesHaveSafeCategories() {
        var statuses=Map.of(400,Category.REQUEST_FORMAT,401,Category.AUTH,402,Category.BALANCE,
            403,Category.PERMISSION,404,Category.ENDPOINT,407,Category.PROXY,429,Category.RATE_LIMIT,500,Category.PROVIDER_5XX);
        statuses.forEach((status,expected)->{
            var failure=AiFailure.http(status,"private-provider-body".getBytes(StandardCharsets.UTF_8));
            assertEquals(expected,failure.category());assertEquals(status,failure.upstreamStatus());
            assertFalse(failure.getMessage().contains("private-provider-body"));assertNull(failure.getCause());
        });
        assertEquals(Category.MODEL_NOT_FOUND,AiFailure.http(400,"{\"error\":{\"code\":\"model_not_found\",\"message\":\"private-marker\"}}".getBytes(StandardCharsets.UTF_8)).category());
        assertEquals(Category.MODEL_NOT_FOUND,AiFailure.http(404,"{\"error\":{\"message\":\"Model Not Exist\"}}".getBytes(StandardCharsets.UTF_8)).category());
        assertEquals(Category.BALANCE,AiFailure.http(429,"{\"error\":{\"code\":\"insufficient_quota\"}}".getBytes(StandardCharsets.UTF_8)).category());
    }
    @Test void nestedTransportFailuresDoNotExposeCauseMessages() {
        Map<Throwable,Category> cases=Map.of(new UnknownHostException("secret-host"),Category.DNS,
            new ConnectException("secret-address"),Category.TCP,new SSLHandshakeException("secret-certificate"),Category.TLS,
            new HttpConnectTimeoutException("secret-url"),Category.CONNECT_TIMEOUT,new HttpTimeoutException("secret-url"),Category.READ_TIMEOUT,
            new SocketTimeoutException("Read timed out secret"),Category.READ_TIMEOUT,
            new java.io.IOException("Unable to tunnel through proxy secret"),Category.PROXY,
            new IllegalStateException("secret-adapter-body"),Category.ADAPTER);
        cases.forEach((cause,category)->{
            var failure=AiFailure.classify(new RuntimeException("private-wrapper",cause));
            assertEquals(category,failure.category());assertNull(failure.getCause());assertFalse(failure.getMessage().contains("secret"));
        });
    }
    @Test void providerAuthenticationFailureReturns503WithSafeDataRatherThanLoggingOutUser()throws Exception {
        var provider=mock(AiProviderService.class);when(provider.probe()).thenThrow(AiFailure.http(401,null));
        var mvc=MockMvcBuilders.standaloneSetup(new AiAssistantController(mock(AiAssistantService.class),provider))
            .setControllerAdvice(new AiFailureHandler()).build();
        mvc.perform(post("/user/ai/probe")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.data.category").value("AUTH"))
            .andExpect(jsonPath("$.data.upstreamStatus").value(401));
    }
}
