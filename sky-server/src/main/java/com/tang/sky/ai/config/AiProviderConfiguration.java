package com.tang.sky.ai.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import org.springframework.ai.openai.OpenAiChatOptions;

/** 配置验证不访问网络，异常消息不携带原始URL、凭据或查询参数。 */
public final class AiProviderConfiguration {
    public static final String DEFAULT_MODEL = "deepseek-flash";
    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private AiProviderConfiguration() {}

    public record Endpoint(String baseUrl, String completionsPath, boolean deepseek, boolean local) {
        public String provider() { return deepseek ? "deepseek" : local ? "local-test" : "openai-compatible"; }
    }

    public static void validateMode(String mode) {
        if (mode == null || !Set.of("mock", "live").contains(mode))
            throw new IllegalArgumentException("AI_MODE只支持mock或live，请检查拼写；配置错误不会自动降级为Mock");
    }

    public static Endpoint endpoint(String baseUrl) {
        try {
            if (baseUrl == null || baseUrl.isBlank()) throw invalidUrl();
            URI uri = new URI(baseUrl);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean local = Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(host);
            if (host.isBlank() || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !("https".equals(scheme) || "http".equals(scheme) && local)) throw invalidUrl();
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            // base URL应为服务前缀；拒绝完整接口或路径穿越，避免意外向其他接口发送Key。
            if (path.contains("..") || path.endsWith("/chat/completions") || path.contains("//")) throw invalidUrl();
            boolean deepseek = host.equals("api.deepseek.com");
            if (deepseek && !(path.isEmpty() || path.equals("/v1"))) throw invalidUrl();
            String normalized = baseUrl.replaceAll("/+$", "");
            String completion = deepseek || path.endsWith("/v1") ? "/chat/completions" : "/v1/chat/completions";
            return new Endpoint(normalized, completion, deepseek, local);
        } catch (URISyntaxException e) {
            // URISyntaxException本身包含输入URL，不能挂为cause或写到日志。
            throw invalidUrl();
        }
    }

    public static void validateModel(Endpoint endpoint, String model) {
        if (model == null || !model.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"))
            throw new IllegalArgumentException("AI_MODEL为空或格式无效，请填写Provider提供的模型名称");
        if (endpoint.deepseek() && Set.of("deepseek-chat", "deepseek-reasoner").contains(model))
            throw new IllegalArgumentException("DeepSeek官方已停用旧模型名称，请手动将AI_MODEL设为deepseek-flash或deepseek-v4-pro；不会替换显式配置");
    }

    public static OpenAiChatOptions options(Endpoint endpoint, String model, int maxTokens) {
        validateModel(endpoint, model);
        var options = OpenAiChatOptions.builder().model(model).temperature(0.3).maxTokens(maxTokens);
        // DeepSeek目前默认thinking启用。关闭后无需回传Spring AI 1.1.8未保存的reasoning_content。
        // 只对官方host设置，兼容服务可有不同参数语义，不能替用户猜测。
        if (endpoint.deepseek()) options.reasoningEffort("none");
        return options.build();
    }

    private static IllegalArgumentException invalidUrl() {
        return new IllegalArgumentException("AI_BASE_URL必须为不含凭据、查询参数或片段的HTTPS服务地址；HTTP仅允许本机测试地址，不能填写完整chat/completions接口");
    }
}
