package com.jichi.ragkb.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 统一 API 响应封装
 */
@Getter
@Setter
@Accessors(chain = true)
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<T>()
                .setCode(200)
                .setMessage("success")
                .setData(data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<T>()
                .setCode(code)
                .setMessage(message);
    }
}
