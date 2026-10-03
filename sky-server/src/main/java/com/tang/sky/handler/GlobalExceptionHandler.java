package com.tang.sky.handler;

import com.tang.sky.common.*;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 集中转换错误，Controller只表达请求流程；未知异常不向前端泄露堆栈或底层消息。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> business(BusinessException e) {
        return ResponseEntity.status(e.error().code()).body(Result.failure(e.error().code(), e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Result<Void>> validation(Exception e) {
        // 不回显被拒绝的输入值，避免密码等敏感字段进入响应或日志。
        return response(ErrorCode.BAD_REQUEST);
    }
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Result<Void>> missing(Exception e) { return response(ErrorCode.NOT_FOUND); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> unsupportedMethod(Exception e) { return response(ErrorCode.METHOD_NOT_ALLOWED); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> unexpected(Exception e) {
        log.error("Unhandled failure type={}", e.getClass().getSimpleName());
        return response(ErrorCode.INTERNAL_ERROR);
    }
    private ResponseEntity<Result<Void>> response(ErrorCode error) {
        return ResponseEntity.status(error.code()).body(Result.failure(error.code(), error.message()));
    }
}
