package com.github.shangtanlin.common.constant.order;

public class OrderStatusConstant {
    // ==================== 1. 订单状态码 ====================
    /**
     * 0: 待付款
     * 订单刚创建，等待用户支付
     */
    public static final Integer PENDING_PAY = 0;

    /**
     * 1: 待发货
     * 用户已支付，等待商家发货
     */
    public static final Integer PENDING_SHIP = 1;

    /**
     * 2: 已发货 (待收货)
     * 商家已发货，等待用户确认收货
     */
    public static final Integer SHIPPED = 2;

    /**
     * 3: 已完成 (正常收货)
     * 用户主动确认收货，或系统自动收货
     */
    public static final Integer COMPLETED = 3;

    /**
     * 4: 售后中 (🌟 新增核心状态)
     * 只要产生了退款申请（无论部分还是全额），且订单仍在正常流转或等待退款，
     * 主订单主状态就应该被推进到 4。
     * 比如：买了一双鞋和一件衣服，衣服退款了（部分退款），鞋子还要发货，
     * 此时主状态=4，退款状态=部分退款。
     */
    public static final Integer AFTER_SALE = 4;

    /**
     * 5: 已关闭
     * 终态1：超时未支付关闭
     * 终态2：全额退款完成，订单彻底终结
     */
    public static final Integer CLOSED = 5;
}
