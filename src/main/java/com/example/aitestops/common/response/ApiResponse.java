package com.example.aitestops.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一接口返回对象，避免 Controller 暴露不一致的响应结构。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
public class ApiResponse<T> {

    private Integer code;
    private String message;
    private T data;
    private String traceId;

    public ApiResponse(Integer code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public ApiResponse(Integer code, String message, T data) {
        this(code, message, data, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(0, "success", null);
    }

    public static ApiResponse<Void> fail(Integer code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static ApiResponse<Void> fail(Integer code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId);
    }
}
