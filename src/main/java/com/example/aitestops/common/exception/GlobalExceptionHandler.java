package com.example.aitestops.common.exception;

import com.example.aitestops.common.response.ApiResponse;
import com.example.aitestops.common.util.TraceIdUtil;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器，统一转换为约定的 ApiResponse 结构。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException ex) {
        String traceId = TraceIdUtil.ensureTraceId();
        log.warn("业务异常: traceId={}, code={}, message={}", traceId, ex.getCode(), ex.getMessage(), ex);
        return ApiResponse.fail(ex.getCode(), ex.getMessage(), traceId);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String traceId = TraceIdUtil.ensureTraceId();
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("请求体参数校验失败: traceId={}, message={}", traceId, message);
        return ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), message, traceId);
    }

    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException ex) {
        String traceId = TraceIdUtil.ensureTraceId();
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("请求参数绑定失败: traceId={}, message={}", traceId, message);
        return ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), message, traceId);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolationException(ConstraintViolationException ex) {
        String traceId = TraceIdUtil.ensureTraceId();
        log.warn("请求参数校验失败: traceId={}, message={}", traceId, ex.getMessage());
        return ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), ex.getMessage(), traceId);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MultipartException.class})
    public ApiResponse<Void> handleRequestException(Exception ex) {
        String traceId = TraceIdUtil.ensureTraceId();
        log.warn("请求内容无法读取: traceId={}, message={}", traceId, ex.getMessage(), ex);
        return ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), "请求内容无法读取", traceId);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        String traceId = TraceIdUtil.ensureTraceId();
        log.warn("文件超过 Spring Multipart 限制: traceId={}, message={}", traceId, ex.getMessage(), ex);
        return ApiResponse.fail(ErrorCode.FILE_TOO_LARGE.getCode(), ErrorCode.FILE_TOO_LARGE.getMessage(), traceId);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
        String traceId = TraceIdUtil.ensureTraceId();
        log.debug("静态资源不存在: traceId={}, path={}", traceId, ex.getResourcePath());
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.fail(ErrorCode.RESOURCE_NOT_FOUND.getCode(), ErrorCode.RESOURCE_NOT_FOUND.getMessage(), traceId));
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        String traceId = TraceIdUtil.ensureTraceId();
        log.error("未处理系统异常: traceId={}", traceId, ex);
        return ApiResponse.fail(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage(), traceId);
    }
}
