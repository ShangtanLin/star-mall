package com.github.shangtanlin.controller;


import com.github.shangtanlin.common.response.WechatNotifyResponse;
import com.github.shangtanlin.service.OrderService;
import com.github.shangtanlin.service.RefundOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notify")
@Slf4j
public class WechatNotifyController {

    @Autowired
    private RefundOrderService refundOrderService;


    @Autowired
    private OrderService orderService;

    /**
     * 微信支付成功回调接口（模拟）
     */
    @PostMapping("/pay")
    public WechatNotifyResponse payNotify(
            @RequestParam String orderSn,
            @RequestParam Integer paymentType) {

        try {
            // 1. 验签 + 解密 (伪代码)
            // String plainText = WxPayUtil.verifyAndDecrypt(...);
            // String orderSn = ...;

            // 2. 业务处理
            orderService.paySuccess(orderSn, paymentType);

            // 3. 返回响应
            return WechatNotifyResponse.success();

        } catch (Exception e) {
            log.error("微信支付回调处理异常", e);
            // 🌟 返回失败
            return WechatNotifyResponse.fail("系统异常");
        }
    }

    /**
     * 微信退款结果通知回调接口（模拟）
     */
    @PostMapping("/refund")
    public WechatNotifyResponse refundNotify(
            @RequestParam String refundSn,
            @RequestParam Boolean success,
            @RequestParam String wechatRefundId) {

        try {
            // 1. 验签 + 解密 (伪代码)
            // String plainText = WxPayUtil.verifyAndDecrypt(...);

            // 2. 业务处理
            refundOrderService.handleRefundNotify(refundSn, success, wechatRefundId);

            // 3. 🌟 返回成功
            return WechatNotifyResponse.success();

        } catch (Exception e) {
            log.error("微信退款回调处理异常", e);
            // 🌟 返回失败
            return WechatNotifyResponse.fail("系统异常");
        }
    }



}