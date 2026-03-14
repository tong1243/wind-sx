package com.wut.screencommonsx.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    // 快捷构建方法
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "操作成功", data);
    }
    public static <T> ApiResponse<T> success(String msg, T data) {
        return new ApiResponse<>(200, msg, data);
    }
    public static <T> ApiResponse<T> error(int code, String msg) {
        return new ApiResponse<>(code, msg, null);
    }
    public static <T> ApiResponse<T> badRequest(String msg) {
        return error(400, msg);
    }
    public static <T> ApiResponse<T> unauthorized(String msg) {
        return error(401, msg);
    }
    public static <T> ApiResponse<T> notFound(String msg) {
        return error(404, msg);
    }
}