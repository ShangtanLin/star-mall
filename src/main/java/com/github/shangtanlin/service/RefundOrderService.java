package com.github.shangtanlin.service;

import com.github.shangtanlin.model.entity.order.RefundOrder;

import java.math.BigDecimal;

public interface RefundOrderService {
    // ==========================================
    // 1. 退款发起阶段（生成退款单）
    // ==========================================

    /**
     * 系统自动退款（用于超时关单、异常等系统级触发）
     * @param orderSn 原订单号
     * @param refundAmount 退款金额
     * @param reason 退款原因 (如：订单超时关闭自动退款)
     */
    void createSystemRefund(String orderSn, BigDecimal refundAmount, String reason);

    /**
     * 用户主动申请退款（用于C端用户发起的退货退款、仅退款等业务）
     * 注意：未来做用户端退款时再加，预留口子
     * @param orderSn 原订单号
     * @param refundAmount 退款金额
     * @param reason 退款原因 (如：不想要了)
     */
    // void createUserRefund(String orderSn, BigDecimal refundAmount, String reason);


    // ==========================================
    // 2. 退款处理阶段（定时任务调用）
    // ==========================================

    /**
     * 处理待退款的订单（定时任务扫描调用）
     * 扫描 status=0 的记录，调用微信API发起退款，并更新状态为 1-退款中
     */
    void processPendingRefunds();


    // ==========================================
    // 3. 退款结果通知阶段（微信回调调用）
    // ==========================================

    /**
     * 处理微信退款结果通知（微信异步回调调用）
     * 根据微信的回调结果，将退款单状态更新为 2-退款成功 或 3-退款失败
     * @param refundSn 退款单号
     * @param success 是否退款成功
     * @param wechatRefundId 微信退款单号（微信系统生成的，可用于对账）
     */
    void handleRefundNotify(String refundSn, boolean success, String wechatRefundId);


    // ==========================================
    // 4. 基础查询方法（内部协作 & 校验使用）
    // ==========================================

    /**
     * 根据退款单号查询退款单
     */
    RefundOrder getByRefundSn(String refundSn);

    /**
     * 根据原订单号查询退款单（用于幂等校验，判断该订单是否已发起过退款）
     * 注意：如果未来支持部分退款，这里应返回 List<RefundOrder>，目前超时关单场景全额退款返回单个即可
     */
    RefundOrder getByOrderSn(String orderSn);
}
