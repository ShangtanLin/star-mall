package com.github.shangtanlin.common.constant.order;

import com.github.shangtanlin.model.vo.order.OrderStatusVO;

public class FrontOrderDisplayStatus {
    // ==================== 1. 前端展示状态码 ====================
    public static final int PAYING = 0;             // 待支付
    public static final int PENDING_SHIP = 1;       // 待发货
    public static final int PENDING_RECEIPT = 2;    // 待收货
    public static final int COMPLETED = 3;          // 已完成

    // --- 逆向/异常状态（按你的逻辑细化） ---
    public static final int AFTER_SALE_REFUNDING = 4;     // 售后中，退款中
    public static final int AFTER_SALE_COMPLETED = 5; // 售后中，退款完成
    public static final int CLOSED_REFUNDING = 6;         // 已关闭，退款中 (订单已关，钱在退，比如并发冲突)
    public static final int CLOSED = 7;                   // 已关闭 (终态：超时未付款关单，或全额退款已完成关单)

    // ==================== 2. 默认提示语常量 ====================
    public static final String MSG_PAYING = "订单待支付，请尽快付款";
    public static final String MSG_PENDING_SHIP = "支付成功，等待商家发货";
    public static final String MSG_PENDING_RECEIPT = "商家已发货，等待收货";
    public static final String MSG_COMPLETED = "交易已完成，感谢您的购买";

    // 🌟 针对细化状态的精准文案
    public static final String MSG_AFTER_SALE_REFUNDING = "售后处理中，正在等待退款";
    public static final String MSG_AFTER_SALE_COMPLETED = "售后处理完成，退款成功";
    public static final String MSG_CLOSED_REFUNDING = "订单超时已关闭，款项正在原路退回，请稍后关注到账信息";
    public static final String MSG_CLOSED = "订单已关闭";

    public static final String MSG_ERROR = "订单状态异常";

    // ==================== 3. 状态转换引擎 ====================
    public static OrderStatusVO translate(Integer status, Integer refundStatus) {
        OrderStatusVO vo = new OrderStatusVO();

        if (status == null) {
            fill(vo, CLOSED, MSG_ERROR);
            return vo;
        }

        switch (status) {
            case 0: // 待付款
                fill(vo, PAYING, MSG_PAYING);
                break;
            case 1: // 待发货
                fill(vo, PENDING_SHIP, MSG_PENDING_SHIP);
                break;
            case 2: // 待收货
                fill(vo, PENDING_RECEIPT, MSG_PENDING_RECEIPT);
                break;
            case 3: // 已完成
                fill(vo, COMPLETED, MSG_COMPLETED);
                break;

            case 4: // 主状态：售后中
                if (refundStatus != null && refundStatus == OrderRefundStatusConstant.REFUNDING) {
                    // 退款进行中
                    fill(vo, AFTER_SALE_REFUNDING, MSG_AFTER_SALE_REFUNDING);
                } else if (refundStatus != null && refundStatus == OrderRefundStatusConstant.COMPLETED) {
                    // 🌟 退款已完成（不管部分还是全额，只要在售后中完成了，就提示退款成功）
                    fill(vo, AFTER_SALE_COMPLETED, MSG_AFTER_SALE_COMPLETED);
                } else {
                    // 兜底：刚发起售后还没开始退款
                    fill(vo, AFTER_SALE_REFUNDING, MSG_AFTER_SALE_REFUNDING);
                }
                break;

            case 5: // 主状态：已关闭
                if (refundStatus != null && refundStatus == OrderRefundStatusConstant.REFUNDING) {
                    // 🌟 5 + 退款中：并发冲突，关单但正在退钱
                    fill(vo, CLOSED_REFUNDING, MSG_CLOSED_REFUNDING);
                } else {
                    // 🌟 5 + 无退款 / 5 + 退款完成：统一显示“订单已关闭”
                    // 前端如果需要区分，可以通过别的入口（如退款详情）去看，主列表状态用 MSG_CLOSED 足矣
                    fill(vo, CLOSED, MSG_CLOSED);
                }
                break;

            default:
                fill(vo, CLOSED, MSG_ERROR);
                break;
        }
        return vo;
    }

    private static void fill(OrderStatusVO vo, int displayStatus, String displayMsg) {
        vo.setDisplayStatus(displayStatus);
        vo.setDisplayMsg(displayMsg);
    }
}
