package com.github.shangtanlin.model.entity.order;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 子订单实体类（商家订单）
 * 对应数据库表：order_sub
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubOrder {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 关联主订单ID
     */
    private Long parentOrderId;

    /**
     * 主订单编号
     */
    private String parentOrderSn;

    /**
     * 子订单编号（商家订单号）
     */
    private String subOrderSn;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商家ID
     */
    private Long shopId;

    // --- 金额相关 ---

    /**
     * 该店铺商品原价总额
     */
    private BigDecimal goodsAmount;

    /**
     * 该店铺分摊到的优惠金额
     */
    private BigDecimal couponAmount;

    /**
     * 该店铺实付金额 (total - coupon + freight)
     */
    private BigDecimal payAmount;

    /**
     * 运费
     */
    private BigDecimal freightAmount;

    // --- 状态与物流 ---

    /**
     * 子订单状态：0->待付款；1->待发货；2->已发货；3->已完成；4->已关闭；5->售后中
     */
    private Integer status;

    /**
     * 退款状态：0-无退款 1-部分退款中 2-部分退款成功 3-全额退款中 4-全额退款成功
     */
    private Integer refundStatus; // 🌟 新增的退款状态字段

    /**
     * 累计已退金额(含退款中)
     */
    private BigDecimal refundedAmount;

    /**
     * 物流公司名称
     */
    private String deliveryCompany;

    /**
     * 物流单号
     */
    private String deliverySn;

    // --- 收货信息冗余（用于商家发货打印） ---

    private String receiverName;
    private String receiverPhone;
    private String receiverProvince;
    private String receiverCity;
    private String receiverDistrict;
    private String receiverDetailAddress;

    // --- 时间戳 ---

    private LocalDateTime createTime;

    /**
     * 支付时间（通常从主订单支付回调时同步更新）
     */
    private LocalDateTime paymentTime;

    /**
     * 支付方式
     */
    private Integer paymentType;


    private LocalDateTime updateTime;


    /**
     * 支付截至时间
     */
    private LocalDateTime payDeadline;


    /**
     * 订单备注
     */
    private String remark;



    /**
     * 逻辑删除
     */
    private Integer isDelete;
}