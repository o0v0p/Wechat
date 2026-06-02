package org.example.wechat.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.Result;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException ex) {
        log.warn("业务异常: {} (HTTP: {}, Code: {})", ex.getMessage(), ex.getHttpStatus(), ex.getCode());
        switch (ex.getHttpStatus().value()) {
            case 400:
                return Result.badRequest(ex.getMessage());
            case 401:
                return Result.unauthorized(ex.getMessage());
            case 403:
                return Result.forbidden(ex.getMessage());
            case 404:
                return Result.notFound(ex.getMessage());
            case 409:
                return Result.conflict(ex.getMessage());
            default:
                return Result.error(ex.getCode(), ex.getMessage());
        }
    }

    // ========== 参数校验异常 ==========
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMsg = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return Result.badRequest(errorMsg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMissingParams(MissingServletRequestParameterException ex) {
        return Result.badRequest("缺少必要参数: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return Result.badRequest("参数类型错误: " + ex.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return Result.badRequest("请求体格式错误");
    }

    // ========== 数据库冲突 ==========
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<?> handleDuplicateKeyException(DuplicateKeyException ex) {
        String message = ex.getMessage();
        log.warn("数据库唯一键冲突: {}", message);
        if (message.contains("Duplicate entry")) {
            String[] split = message.split(" ");
            String value = split[2].replace("'", "");
            String msg = value + " 已存在";
            return Result.conflict(msg);
        }
        return Result.conflict("数据保存失败，唯一键冲突");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<?> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.warn("数据完整性约束违反: {}", ex.getMessage());
        String message = ex.getMostSpecificCause().getMessage();
        if (message.contains("foreign key")) {
            return Result.conflict("操作失败，关联数据不存在");
        }
        return Result.conflict("数据操作失败，请检查数据完整性");
    }

    // ========== 通用运行时异常 ==========
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleRuntimeException(RuntimeException ex) {
        log.error("运行时异常: {}", ex.getMessage(), ex);
        return Result.serverError(ex.getMessage());
    }

    // ========== 兜底异常 ==========
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception ex) {
        log.error("系统异常: ", ex);
        return Result.serverError("系统繁忙，请稍后重试");
    }
}