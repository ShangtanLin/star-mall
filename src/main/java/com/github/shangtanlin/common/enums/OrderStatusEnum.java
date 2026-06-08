package com.github.shangtanlin.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    UNPAID(0, "待付款"),
    WAIT_DELIVERY(1, "待发货"),
    DELIVERED(2, "已发货"),
    SUCCESS(3, "交易成功"),
    CLOSED(4, "已关闭"),
    REFUNDING(5, "退款中");

    private final Integer code;
    private final String desc;

    /**
     * 根据状态码获取描述文字
     */
    public static String getDescByCode(Integer code) {
        if (code == null) {
            return "未知状态";
        }
        for (OrderStatusEnum status : OrderStatusEnum.values()) {
            if (status.getCode().equals(code)) {
                return status.getDesc();
            }
        }
        return "未知状态";
    }
}
