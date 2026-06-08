package com.github.shangtanlin.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PaymentTypeEnum {

    ALIPAY(1, "支付宝"),
    WECHAT(2, "微信");

    private final Integer code;
    private final String desc;

    /**
     * 根据支付类型码获取描述
     */
    public static String getDescByCode(Integer code) {
        if (code == null) {
            return "订单未支付";
        }
        for (PaymentTypeEnum type : PaymentTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type.getDesc();
            }
        }
        return "未知支付方式";
    }
}
