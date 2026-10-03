package com.tang.sky;

import com.tang.sky.ai.config.AiConfig;
import com.tang.sky.ai.config.AiProviderConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 仅验证配置与构建参数；不发送HTTP请求，也不读取开发者真实Key。 */
class AiProviderConfigurationTest {
    @Test void typoDoesNotSilentlySelectMock() {
        for (String mode : new String[]{"LIVE", "lve", "", "mock "}) {
            var error = assertThrows(IllegalArgumentException.class,
                () -> new AiConfig().chatModel(mode, "", "https://api.deepseek.com", "deepseek-flash"));
            assertTrue(error.getMessage().contains("AI_MODE"));
        }
    }

    @Test void baseUrlsAvoidDuplicatedV1AndPreserveCompatiblePrefix() {
        assertEndpoint("https://api.deepseek.com", "https://api.deepseek.com", "/chat/completions");
        assertEndpoint("https://api.deepseek.com/v1/", "https://api.deepseek.com/v1", "/chat/completions");
        assertEndpoint("https://api.example.test", "https://api.example.test", "/v1/chat/completions");
        assertEndpoint("https://api.example.test/openai/v1/", "https://api.example.test/openai/v1", "/chat/completions");
        assertEndpoint("http://127.0.0.1/v1", "http://127.0.0.1/v1", "/chat/completions");
    }

    @Test void rejectsUnsafeUrlsWithoutEchoingInput() {
        for (String url : new String[]{"http://api.deepseek.com", "https://user:private-marker@api.deepseek.com",
                "https://api.deepseek.com?key=private-marker", "https://api.deepseek.com#private-marker",
                "https://api.deepseek.com/private-marker", "https://api.example.test/v1/chat/completions",
                "https://api.example.test/../private-marker", "not a URI private-marker"}) {
            var error = assertThrows(IllegalArgumentException.class, () -> AiProviderConfiguration.endpoint(url));
            assertTrue(error.getMessage().contains("AI_BASE_URL"));
            assertFalse(error.getMessage().contains("private-marker"));
            assertNull(error.getCause(), "异常原因不能保留包含输入URL的URISyntaxException");
        }
    }

    @Test void retiredNamesAreRejectedOnlyForOfficialDeepseekAndExplicitNamesArePreserved() {
        var official = AiProviderConfiguration.endpoint("https://api.deepseek.com");
        var compatible = AiProviderConfiguration.endpoint("https://api.example.test/v1");
        for (String retired : new String[]{"deepseek-chat", "deepseek-reasoner"}) {
            assertThrows(IllegalArgumentException.class, () -> AiProviderConfiguration.options(official, retired, 64));
            assertEquals(retired, AiProviderConfiguration.options(compatible, retired, 64).getModel());
        }
        var options = AiProviderConfiguration.options(official, "deepseek-v4-pro", 64);
        assertEquals("deepseek-v4-pro", options.getModel());
        assertEquals("none", options.getReasoningEffort());
        assertEquals(64, options.getMaxTokens());
        assertNull(AiProviderConfiguration.options(compatible, "other-model", 64).getReasoningEffort());
    }

    private static void assertEndpoint(String input, String base, String path) {
        var endpoint = AiProviderConfiguration.endpoint(input);
        assertEquals(base, endpoint.baseUrl()); assertEquals(path, endpoint.completionsPath());
    }
}
