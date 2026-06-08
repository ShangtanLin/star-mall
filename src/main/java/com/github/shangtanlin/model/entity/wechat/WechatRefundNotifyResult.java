package com.github.shangtanlin.model.entity.wechat;

import lombok.Data;

/**
 * 微信退款异步回调解析结果
 */
@Data
public class WechatRefundNotifyResult {
    private String orderSn;       // 原商户订单号
    private String refundSn;     // 商户退款单号
    private String wechatRefundId;// 微信退款单号
    private String status;        // 退款状态: SUCCESS(退款成功), CHANGE(退款异常)
    private String successTime;   // 退款成功时间
}
