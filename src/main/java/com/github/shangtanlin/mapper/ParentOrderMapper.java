package com.github.shangtanlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.shangtanlin.model.entity.order.ParentOrder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface ParentOrderMapper extends BaseMapper<ParentOrder> {

    /**
     * 根据编号和用户Id查询订单详情
     * @param orderSn
     * @param userId
     */
    @Select("select * from parent_order where " +
            "order_sn = #{orderSn} " +
            "and user_id = #{userId}")
    ParentOrder selectBySnAndUserId(
            @Param("orderSn") String orderSn,
            @Param("userId") Long userId);

    /**
     * 根据编号修改订单主状态（乐观锁）
     * @param orderSn
     * @param status
     * @param oldStatus
     * @return 影响的行数（0 表示状态已变更，未更新）
     */
    @Update("update parent_order set status = #{status} " +
            "where order_sn = #{orderSn} " +
            "and status = #{oldStatus}")
    int setStatus(
            @Param("orderSn") String orderSn,
            @Param("status") Integer status,
            @Param("oldStatus") Integer oldStatus);

    /**
     * 根据主订单编号修改子订单退款状态
     * @param orderSn
     * @param refundStatus
     * @param oldRefundStatus
     * @return 影响的行数（0 表示状态已变更，未更新）
     */
    @Update("update parent_order set refund_status = #{refundStatus} " +
            "where order_sn = #{orderSn} " +
            "and refund_status = #{oldRefundStatus}")
    void setRefundStatus(
            @Param("orderSn") String orderSn,
            @Param("refundStatus") Integer refundStatus,
            @Param("oldRefundStatus") Integer oldRefundStatus);


    /**
     * 根据主订单编号查询主订单
     * @param orderSn
     * @return
     */
    @Select("select * from parent_order where order_sn = #{orderSn}")
    ParentOrder selectBySn(@Param("orderSn") String orderSn);


    /**
     * 修改主订单状态
     * @param orderSn
     * @param status
     */
    @Update("update parent_order set status = #{status} " +
            "where order_sn = #{orderSn}")
    void setStatusWithoutUserId(@Param("orderSn") String orderSn,
                                @Param("status") Integer status);


    /**
     * 修改主订单支付时间和支付类型
     * @param orderSn
     * @param paymentType
     * @param paymentTime
     */
    @Update("update parent_order set payment_type = #{paymentType}, " +
            "payment_time = #{paymentTime} where order_sn = #{orderSn}")
    void setPaymentType(
            @Param("orderSn") String orderSn,
            @Param("paymentType") Integer paymentType,
            @Param("paymentTime") LocalDateTime paymentTime);


    /**
     * 修改主订单状态
     * @param orderSn
     * @param status
     */
    @Update("update parent_order set status = #{status} " +
            "where order_sn = #{orderSn}")
    void updateStatus(
            @Param("orderSn") String orderSn,
            @Param("status") Integer status);


    /**
     * 查询主订单状态
     * @param parentOrderSn
     */
    @Select("select status from parent_order " +
            "where order_sn = #{parentOrderSn}")
    Integer selectStatus(@Param("parentOrderSn") String parentOrderSn);


    /**
     * 查询主订单退款状态
     * @param parentOrderSn
     */
    @Select("select refund_status from parent_order " +
            "where order_sn = #{parentOrderSn}")
    Integer selectRefundStatus(@Param("parentOrderSn") String parentOrderSn);


    /**
     * 根据编号查询主订单（加行锁）
     * @param parentOrderSn
     * @return
     */
    @Select("select * from parent_order " +
            "where order_sn = #{parentOrderSn} for update")
    ParentOrder selectBySnForUpdate(
            @Param("parentOrderSn") String parentOrderSn);


    /**
     * 更新支付相关字段
     * @param parentOrderSn
     * @param status
     * @param paymentType
     * @param paymentTime
     */
    @Update("update parent_order set status = #{status}," +
            "payment_type = #{paymentType}," +
            "payment_time = #{paymentTime} " +
            "where order_sn = #{parentOrderSn} " +
            "and status = #{oldStatus}")
    int updatePayInfo(
            @Param("parentOrderSn") String parentOrderSn,
            @Param("status") Integer status,
            @Param("paymentType") Integer paymentType,
            @Param("paymentTime") LocalDateTime paymentTime,
            @Param("oldStatus") Integer oldStatus);


    /**
     * 退款回调成功时，原子性更新主订单的退款金额和状态 (纯注解版)
     */
    @Update("UPDATE parent_order " +
            "SET refunded_amount = refunded_amount + #{currentRefundAmount}, " +
            "    refund_status = CASE " +
            "                      WHEN (refunded_amount + #{currentRefundAmount}) >= #{payAmount} THEN #{refundStatusFull} " +
            "                      ELSE #{refundStatusPart} " +
            "                    END, " +
            "    status = CASE " +
            "              WHEN (refunded_amount + #{currentRefundAmount}) >= #{payAmount} THEN #{orderStatusClosed} " +
            "              ELSE status " +
            "            END " +
            "WHERE id = #{orderId}")
    void updateOrderRefundInfoAtomic(@Param("orderId") Long orderId,
                                     @Param("currentRefundAmount") BigDecimal currentRefundAmount,
                                     @Param("payAmount") BigDecimal payAmount,
                                     @Param("refundStatusFull") Integer refundStatusFull,
                                     @Param("refundStatusPart") Integer refundStatusPart,
                                     @Param("orderStatusClosed") Integer orderStatusClosed);



    /**
     * 修改主订单主状态及退款状态
     * @param parentOrderSn
     * @param status
     * @param refundStatus
     */
    @Update("update parent_order set status = #{status}," +
            "refund_status = #{refundStatus} " +
            "where order_sn = #{parentOrderSn}")
    void setStatusAndRefundStatus(String parentOrderSn, Integer status, Integer refundStatus);
}
