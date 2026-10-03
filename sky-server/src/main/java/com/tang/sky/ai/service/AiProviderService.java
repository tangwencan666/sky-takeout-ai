package com.tang.sky.ai.service;

import com.tang.sky.ai.config.AiProviderConfiguration;
import com.tang.sky.ai.security.AiOutput;
import com.tang.sky.ai.security.AiRequestAudit;
import com.tang.sky.common.*;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 最小连通性探针不注册Tools、Memory或RAG；状态只展示安全元数据及本进程证据。 */
@Service
public class AiProviderService {
    private static final String INSTANCE_ID = java.util.UUID.randomUUID().toString();
    public record Status(String mode, String model, String provider, boolean keyConfigured,
                         boolean providerVerified, String verificationScope, Instant lastVerifiedAt,
                         String configurationWarning, String instanceId) {}
    public record Probe(String mode, String model, String message, boolean providerVerified, String instanceId) {}

    private final ChatClient client;
    private final String mode, model;
    private final boolean keyConfigured;
    private final AiProviderConfiguration.Endpoint endpoint;
    private final AtomicReference<Instant> lastVerified = new AtomicReference<>();
    private Probe successfulProbe;

    public AiProviderService(ChatModel chatModel, @Value("${AI_MODE:mock}") String mode,
            @Value("${AI_API_KEY:}") String key,
            @Value("${AI_BASE_URL:https://api.deepseek.com}") String baseUrl,
            @Value("${AI_MODEL:deepseek-flash}") String model) {
        AiProviderConfiguration.validateMode(mode);
        this.endpoint = AiProviderConfiguration.endpoint(baseUrl);
        if ("live".equals(mode)) AiProviderConfiguration.validateModel(endpoint, model);
        this.client = ChatClient.create(chatModel);
        this.mode = mode; this.model = model; this.keyConfigured = key != null && !key.isBlank();
    }

    public Status status() {
        String warning = !"live".equals(mode) ? "当前为Mock，配置Key不会自动启用真实模型"
            : !keyConfigured ? "当前后端进程未读取到AI_API_KEY"
            : endpoint.local() ? "当前地址为本机协议替身，不能视为真实Provider验证"
            : lastVerified.get() == null ? "配置已加载，尚无本进程最小探针成功证据" : null;
        return new Status(mode, model, endpoint.provider(), keyConfigured, verified(), "process-probe", lastVerified.get(), warning, INSTANCE_ID);
    }

    public synchronized Probe probe() {
        if (!"USER".equals(UserContext.require().role())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!"live".equals(mode))
            return new Probe(mode, model, "【Mock】未发送真实模型请求；请先以AI_MODE=live启动后端", false, INSTANCE_ID);
        if (!keyConfigured) throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "当前后端进程未读取到AI_API_KEY，请从已配置变量的终端启动");
        // 本进程已成功的最小探针直接返回证据，重复点击不会再次产生费用。
        if (successfulProbe != null) return successfulProbe;
        return AiRequestAudit.run("provider-probe", null, model, () -> {
            try {
                var options = AiProviderConfiguration.options(endpoint, model, 64);
                options.setInternalToolExecutionEnabled(false);
                // 新建的ChatClient没有业务提示词或默认工具；唯一用户消息固定为“你好”。
                String answer = AiOutput.checked(client.prompt().options(options).user("你好").call().content());
                if (!endpoint.local()) lastVerified.set(Instant.now());
                successfulProbe = new Probe(mode, model, answer, verified(), INSTANCE_ID);
                return successfulProbe;
            } catch (RuntimeException e) {
                throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "真实模型探针失败，请检查模型名称、账号额度或网络；未自动重试");
            }
        });
    }

    private boolean verified() { return "live".equals(mode) && !endpoint.local() && lastVerified.get() != null; }
}
