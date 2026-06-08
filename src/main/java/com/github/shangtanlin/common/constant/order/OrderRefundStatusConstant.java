package com.github.shangtanlin.common.constant.order;

public class OrderRefundStatusConstant {
    // ==================== 状态码定义 ====================

    /**
     * 0: 无退款 / 退款未开始
     * 订单正常流转中，没有任何退款申请，或者退款流程还未启动时的初始状态。
     */
    public static final int NO_REFUND = 0;

    /**
     * 1: 退款进行中
     * 只要有一笔退款单在处理中（用户申请待审核、微信退款处理中），即为1。
     * 注意：即使部分退款已经到账，只要订单里还有未退完的钱（或者还有退款单在走流程），
     * 这个状态就依然是 1（进行中）。
     */
    public static final int REFUNDING = 1;

    /**
     * 2: 退款已完成 (🌟 核心改变：不再区分部分还是全额！)
     * 代表该订单关联的所有退款流程都已经走完（退款单全部审核完毕且微信处理结束）。
     * 至于到底是部分退款完成，还是全额退款完成，完全由【主订单状态 status】和【已退金额】来决定：
     * - 如果 status=4(售后中) 且 退款已完成 -> 部分退款完成（因为还有钱没退，或者剩下的不退了）
     * - 如果 status=5(已关闭) 且 退款已完成 -> 全额退款完成（钱全退了，订单彻底终结）
     */
    public static final int COMPLETED = 2;

    // ==================== (可选) 状态描述 ====================

    public static String getStatusDesc(int status) {
        switch (status) {
            case NO_REFUND:
                return "无退款";
            case REFUNDING:
                return "退款中";
            case COMPLETED:
                return "退款已完成"; // 统一叫已完成，不细分
            default:
                return "未知状态";
        }
    }
}
