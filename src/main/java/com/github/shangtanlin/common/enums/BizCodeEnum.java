package com.github.shangtanlin.common.enums;

import lombok.Getter;

@Getter
public enum BizCodeEnum {
    // 系统级
    UNKNOWN_EXCEPTION(10000, "系统未知异常"),
    VALID_EXCEPTION(10001, "参数格式校验失败"),

    // 用户相关 (2xxxx)
    USER_NOT_EXIST(20001, "用户不存在"),
    USER_ACCOUNT_LOCKED(20002, "账号已被冻结"),

    // 订单相关 (3xxxx)
    ORDER_NOT_EXIST(30001, "订单不存在"),
    ORDER_TIMEOUT(30002, "订单已超时"),


    // 支付相关 (4xxxx)
    PAY_BALANCE_INSUFFICIENT(40001, "余额不足，请先充值"),
    PAY_CHANNEL_ERROR(40002, "支付通道故障");

    private final int code;
    private final String message;

    BizCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}