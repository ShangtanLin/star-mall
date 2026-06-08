package com.github.shangtanlin.common.constant.order;

public class RefundOrderStatusConstant {

    /**
     * 0: 待处理 (系统已记录，尚未调用微信退款API)
     * 通常由定时任务扫描此状态的单据发起真实退款
     */
    public static final Integer PENDING = 0;

    /**
     * 1: 处理中 (已调用微信退款API，微信正在处理，钱还没到账)
     */
    public static final Integer PROCESSING = 1;

    /**
     * 4: 退款成功 (微信回调确认钱已原路退回) - 终态
     * 注意：这里跳过了2和3，是为了和之前讨论的订单状态习惯保持一致，预留扩展空间
     */
    public static final Integer SUCCESS = 4;

    /**
     * 5: 退款失败 (微信明确拒绝，或余额不足等) - 终态
     */
    public static final Integer FAIL = 5;

}