package com.tang.sky.ai.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tang.sky.common.*;
import java.net.*;
import java.net.http.*;
import java.nio.channels.UnresolvedAddressException;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.SSLException;

/** 保存有限类别与HTTP状态；永不保存原异常cause、响应正文、URL或请求头。 */
public final class AiFailure extends BusinessException {
    public enum Category {
        DNS, TCP, TLS, PROXY, CONNECT_TIMEOUT, READ_TIMEOUT, TOTAL_TIMEOUT,
        AUTH, BALANCE, PERMISSION, ENDPOINT, REQUEST_FORMAT, MODEL_NOT_FOUND,
        RATE_LIMIT, PROVIDER_5XX, JSON, ADAPTER, MISSING_KEY, IN_PROGRESS,
        LOCAL_BUSY, CANCELLED, EMPTY_RESPONSE
    }
    public record Details(String category, Integer upstreamStatus, boolean retryable) {}
    private final Category category;
    private final Integer upstreamStatus;
    private AiFailure(Category category, Integer status) {
        super(ErrorCode.AI_UNAVAILABLE, "[AI_" + category.name() + "] " + hint(category) + "；未自动重试");
        this.category = category; this.upstreamStatus = status;
    }
    public static AiFailure of(Category category) { return new AiFailure(category, null); }
    public Category category() { return category; }
    public Integer upstreamStatus() { return upstreamStatus; }
    public Details details() { return new Details(category.name(), upstreamStatus, Set.of(Category.CONNECT_TIMEOUT,
        Category.READ_TIMEOUT, Category.TOTAL_TIMEOUT, Category.RATE_LIMIT, Category.PROVIDER_5XX,
        Category.IN_PROGRESS, Category.LOCAL_BUSY).contains(category)); }

    public static AiFailure http(int status, byte[] boundedBody) {
        Category category = switch(status) {
            case 400, 422 -> Category.REQUEST_FORMAT; case 401 -> Category.AUTH;
            case 402 -> Category.BALANCE; case 403 -> Category.PERMISSION;
            case 404 -> Category.ENDPOINT; case 407 -> Category.PROXY; case 429 -> Category.RATE_LIMIT;
            default -> status >= 500 ? Category.PROVIDER_5XX : Category.ADAPTER;
        };
        // 只在内存中检查有限正文里的已知错误标识，最终异常不保留任何文本。
        if (boundedBody != null && boundedBody.length <= 4096) {
            try {
                var error = new ObjectMapper().readTree(boundedBody).path("error");
                String code = error.path("code").asText("").toLowerCase(Locale.ROOT);
                String message = error.path("message").asText("").toLowerCase(Locale.ROOT);
                if ((status == 400 || status == 404) && (Set.of("model_not_found", "model_not_exist", "invalid_model").contains(code)
                        || message.contains("model not exist") || message.contains("model not found") || message.contains("unknown model")))
                    category = Category.MODEL_NOT_FOUND;
                if ((status == 400 || status == 402 || status == 429) && (Set.of("insufficient_balance", "insufficient_quota").contains(code)
                        || message.contains("insufficient balance"))) category = Category.BALANCE;
            } catch (Exception ignored) { /* 非JSON错误页仍保留HTTP类别，不猜测正文。 */ }
        }
        return new AiFailure(category, status);
    }

    public static AiFailure classify(Throwable error) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = error; current != null && visited.size() < 20 && visited.add(current); current = current.getCause()) {
            if (current instanceof AiFailure failure) return failure;
            if (current instanceof UnknownHostException || current instanceof UnresolvedAddressException) return of(Category.DNS);
            if (current instanceof SSLException) return of(Category.TLS);
            if (current instanceof HttpConnectTimeoutException) return of(Category.CONNECT_TIMEOUT);
            if (current instanceof SocketTimeoutException) {
                String message = current.getMessage();
                return of(message != null && message.toLowerCase(Locale.ROOT).contains("connect") ? Category.CONNECT_TIMEOUT : Category.READ_TIMEOUT);
            }
            if (current instanceof HttpTimeoutException) return of(Category.READ_TIMEOUT);
            if (current.getClass().getSimpleName().contains("Proxy")
                    || current instanceof java.io.IOException && current.getMessage() != null
                    && current.getMessage().toLowerCase(Locale.ROOT).contains("tunnel through proxy")) return of(Category.PROXY);
            if (current instanceof ConnectException || current instanceof NoRouteToHostException) return of(Category.TCP);
            if (current instanceof JsonProcessingException) return of(Category.JSON);
            if (current instanceof TimeoutException) return of(Category.READ_TIMEOUT);
            if (current instanceof CancellationException || current instanceof InterruptedException) return of(Category.CANCELLED);
        }
        return of(Category.ADAPTER);
    }

    private static String hint(Category category) {
        return switch(category) {
            case DNS -> "模型服务域名解析失败"; case TCP -> "无法连接模型服务TCP端口";
            case TLS -> "模型服务TLS握手或证书校验失败"; case PROXY -> "模型请求代理连接失败";
            case CONNECT_TIMEOUT -> "连接模型服务超时"; case READ_TIMEOUT -> "等待模型响应超时";
            case TOTAL_TIMEOUT -> "模型请求超过总时限，已取消本地等待";
            case AUTH -> "模型服务拒绝认证，请检查本机凭据"; case BALANCE -> "模型账户余额或额度不足";
            case PERMISSION -> "模型账户没有此请求权限"; case ENDPOINT -> "模型接口路径不存在";
            case REQUEST_FORMAT -> "模型服务拒绝请求格式或参数"; case MODEL_NOT_FOUND -> "模型服务报告模型不存在";
            case RATE_LIMIT -> "模型服务限流"; case PROVIDER_5XX -> "模型服务返回服务器错误";
            case JSON -> "模型响应JSON解析失败"; case ADAPTER -> "模型客户端或协议适配失败";
            case MISSING_KEY -> "当前后端进程未读取到AI_API_KEY"; case IN_PROGRESS -> "已有探针正在执行，请勿重复提交";
            case LOCAL_BUSY -> "本地AI并发额度已满，请稍后重试"; case CANCELLED -> "模型请求已取消";
            case EMPTY_RESPONSE -> "模型未返回有效文本";
        };
    }
}
