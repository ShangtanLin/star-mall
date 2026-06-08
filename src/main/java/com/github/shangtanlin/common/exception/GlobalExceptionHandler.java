package com.github.shangtanlin.common.exception;

import com.github.shangtanlin.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice // 1. 告诉 Spring：我是一个全局的异常拦截器，并且返回值都写成 JSON 响应
public class GlobalExceptionHandler {

    /**
     * 处理自定义的业务异常
     */
    @ExceptionHandler(BusinessException.class) // 2. 告诉 Spring：专门抓 BusinessException 这种异常
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("发生业务异常: {}", e.getMessage());
        // 3. 抓到之后，把异常里的信息提取出来，包装成统一的 Result 返回给前端
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 兜底处理：处理所有未知的系统异常（防泄漏）
     */
    @ExceptionHandler(Exception.class) // 4. 只要是 Exception，只要前面的没拦住，全归我管
    public Result<?> handleException(Exception e) {
        // 系统出大问题了，必须打 ERROR 日志，方便后端人员排查
        log.error("系统发生未知异常", e);

        // 绝对不能把 e.getMessage() 给前端！黑客会利用它。
        // 给前端一个友好的默认提示
        return Result.fail(500, "系统开小差了，请稍后再试");
    }
}
