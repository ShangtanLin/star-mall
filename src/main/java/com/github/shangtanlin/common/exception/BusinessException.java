package com.github.shangtanlin.common.exception;

import com.github.shangtanlin.common.enums.BizCodeEnum;
import lombok.Getter;

/**
 * 自定义业务异常类
 */
@Getter
public class BusinessException extends RuntimeException {
    private Integer code;

    // 带状态码的构造器
    public BusinessException(Integer code, String message) {
        super(message); // 把信息传给父类 Throwable
        this.code = code;
    }

    // 只传信息的构造器（默认 code 500 代表通用业务失败）
    public BusinessException(String message) {
        this(500, message);
    }

    // 🌟 新增：接收枚举的构造器（主力军）
    public BusinessException(BizCodeEnum bizCodeEnum) {
        super(bizCodeEnum.getMessage());
        this.code = bizCodeEnum.getCode();
    }
}