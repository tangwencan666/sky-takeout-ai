package com.tang.sky.common;

/** 只携带可安全展示的业务错误，不把SQL、驱动信息或凭据转发给客户端。 */
public class BusinessException extends RuntimeException {
    private final ErrorCode error;
    public BusinessException(ErrorCode error) { super(error.message()); this.error = error; }
    public BusinessException(ErrorCode error, String safeMessage) { super(safeMessage); this.error = error; }
    public ErrorCode error() { return error; }
}
