package com.github.shangtanlin.model.entity.wechat;

import lombok.Data;

/**
 * 调用微信退款 API 的同步返回结果
 */
@Data
public class WechatRefundResult {
    private boolean success;      // 通信是否成功 (HTTP 200 且 状态码为 SUCCESS)
    private String refundSn;      // 商户退款单号
    private String wechatRefundId;// 微信退款单号
    private String status;        // 退款状态: SUCCESS-退款成功, CHANGE-退款异常, REFUNDCLOSE-退款关闭
    private String failReason;    // 失败原因 (如果失败的话)
    private String errorCode;     // 错误码
    private String errorMsg;      // 错误描述

}
