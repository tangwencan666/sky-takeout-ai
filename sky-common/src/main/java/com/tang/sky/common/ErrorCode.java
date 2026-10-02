package com.tang.sky.common;

/** 错误码与HTTP语义保持直观对应；业务需要更细分类时再扩展，避免散落魔法数字。 */
public enum ErrorCode {
    BAD_REQUEST(400, "请求参数不合法"), UNAUTHORIZED(401, "请先登录"),
    FORBIDDEN(403, "无权执行此操作"), NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据状态冲突"), TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"), INTERNAL_ERROR(500, "服务暂时不可用"),
    AI_UNAVAILABLE(503, "AI服务暂时不可用");
    private final int code;
    private final String message;
    ErrorCode(int code, String message) { this.code = code; this.message = message; }
    public int code() { return code; }
    public String message() { return message; }
}
