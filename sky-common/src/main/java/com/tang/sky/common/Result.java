package com.tang.sky.common;

/** 稳定的接口信封使前端统一处理成功与失败；泛型保留业务数据类型，不承担HTTP状态职责。 */
public record Result<T>(int code, String message, T data) {
    public static <T> Result<T> success(T data) { return new Result<>(200, "success", data); }
    public static Result<Void> failure(int code, String message) { return new Result<>(code, message, null); }
}
