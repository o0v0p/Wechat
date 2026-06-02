package org.example.wechat.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常基类
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final Integer code;

    public BusinessException(String message) {
        this(message, HttpStatus.BAD_REQUEST, 400);
    }

    public BusinessException(String message, HttpStatus httpStatus) {
        this(message, httpStatus, httpStatus.value());
    }

    public BusinessException(String message, HttpStatus httpStatus, Integer code) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(message, HttpStatus.BAD_REQUEST, 400);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(message, HttpStatus.NOT_FOUND, 404);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(message, HttpStatus.CONFLICT, 409);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(message, HttpStatus.UNAUTHORIZED, 401);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(message, HttpStatus.FORBIDDEN, 403);
    }
}