package com.wut.screencommonsx.Exception;

import com.wut.screencommonsx.Response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // 处理业务异常
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<?> handleBusinessException(BusinessException e) {
        log.error("业务异常：{}", e.getMessage(), e);
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    // 处理参数验证异常
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    public ApiResponse<?> handleValidException(Exception e) {
        String msg = "参数验证失败";
        if (e instanceof MethodArgumentNotValidException) {
            msg = ((MethodArgumentNotValidException) e).getBindingResult().getFieldError().getDefaultMessage();
        } else if (e instanceof ConstraintViolationException) {
            msg = ((ConstraintViolationException) e).getConstraintViolations().iterator().next().getMessage();
        }
        log.error("参数异常：{}", msg, e);
        return ApiResponse.badRequest(msg);
    }

    // 处理404
    @ExceptionHandler(NoHandlerFoundException.class)
    public ApiResponse<?> handle404(NoHandlerFoundException e) {
        log.error("404异常：{}", e.getMessage());
        return ApiResponse.notFound("资源不存在");
    }

    // 处理通用异常
    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleException(Exception e) {
        log.error("服务器异常：{}", e.getMessage(), e);
        return ApiResponse.error(500, "服务器内部错误，请稍后重试");
    }
}