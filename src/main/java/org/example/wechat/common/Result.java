package org.example.wechat.common;

import lombok.Data;
import java.io.Serializable;

@Data
public class Result<T> implements Serializable {

    private Integer code; // 业务状态码
    private String msg;   // 提示信息
    private T data;       // 数据

    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.code = 200;
        result.msg = "success";
        return result;
    }

    public static <T> Result<T> success(String msg) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.msg = msg;
        return result;
    }

    public static <T> Result<T> success(String msg, T object) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.msg = msg;
        result.data = object;
        return result;
    }

    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<>();
        result.code = 501;
        result.msg = msg;
        return result;
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.code = code;
        result.msg = message;
        return result;
    }

    // 400 Bad Request - 参数校验失败
    public static <T> Result<T> badRequest(String msg) {
        Result<T> result = new Result<>();
        result.code = 400;
        result.msg = msg;
        return result;
    }

    // 401 Unauthorized - 未登录/Token 无效/未认证
    public static <T> Result<T> unauthorized(String msg) {
        Result<T> result = new Result<>();
        result.code = 401;
        result.msg = msg;
        return result;
    }

    // 403 Forbidden - 无权限
    public static <T> Result<T> forbidden(String msg) {
        Result<T> result = new Result<>();
        result.code = 403;
        result.msg = msg;
        return result;
    }

    // 404 Not Found - 资源不存在
    public static <T> Result<T> notFound(String msg) {
        Result<T> result = new Result<>();
        result.code = 404;
        result.msg = msg;
        return result;
    }

    // 409 Conflict - 数据冲突（如唯一键重复）
    public static <T> Result<T> conflict(String msg) {
        Result<T> result = new Result<>();
        result.code = 409;
        result.msg = msg;
        return result;
    }

    // 422 Unprocessable Entity - 业务逻辑校验失败
    public static <T> Result<T> validationError(String msg) {
        Result<T> result = new Result<>();
        result.code = 422;
        result.msg = msg;
        return result;
    }

    // 500 Internal Server Error - 系统内部错误
    public static <T> Result<T> serverError(String msg) {
        Result<T> result = new Result<>();
        result.code = 500;
        result.msg = msg;
        return result;
    }
}