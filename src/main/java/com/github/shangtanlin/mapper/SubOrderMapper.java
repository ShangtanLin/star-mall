package com.github.shangtanlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.shangtanlin.model.entity.order.ParentOrder;
import com.github.shangtanlin.model.entity.order.SubOrder;
import com.github.shangtanlin.model.vo.order.SubOrderIdSn;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 子订单mapper
 */
@Mapper
public interface SubOrderMapper extends BaseMapper<SubOrder> {

    /**
     * 根据主订单编号修改子订单状态
     * @param orderSn
     * @param status
     * @param oldStatus
     */
    @Update("update sub_order set status = #{status} " +
            "where parent_order_sn = #{orderSn} " +
            "and status = #{oldStatus}")
    int setStatusByParentSn(
            @Param("orderSn") String orderSn,
            @Param("status") Integer status,
            @Param("oldStatus") Integer oldStatus);

    /**
     * 根据主订单编号修改子订单退款状态
     * @param parentOrderSn
     * @param refundStatus
     * @param oldRefundStatus
     * @return 影响的行数（0 表示状态已变更，未更新）
     */
    @Update("update sub_order set refund_status = #{refundStatus} " +
            "where parent_order_sn = #{parentOrderSn} " +
            "and refund_status = #{oldRefundStatus}")
    void setRefundStatusByParentSn(
            @Param("parentOrderSn") String parentOrderSn,
            @Param("refundStatus") Integer refundStatus,
            @Param("oldRefundStatus") Integer oldRefundStatus);



    /**
     * 修改子订单退款状态
     * @param subOrderSn
     * @param refundStatus
     * @param oldRefundStatus
     * @return 影响的行数（0 表示状态已变更，未更新）
     */
    @Update("update sub_order set refund_status = #{refundStatus} " +
            "where sub_order_sn = #{subOrderSn} " +
            "and refund_status = #{oldRefundStatus}")
    void setRefundStatus(
            @Param("subOrderSn") String subOrderSn,
            @Param("refundStatus") Integer refundStatus,
            @Param("oldRefundStatus") Integer oldRefundStatus);


    /**
     * 根据父订单编号查询子订单
     * @param parentOrderSn
     * @return
     */
    @Select("select * from sub_order where parent_order_sn = #{parentOrderSn}")
    SubOrder selectByParentId(@Param("parent_order_sn") String parentOrderSn);


    /**
     * 修改子订单状态
     * @param parentOrderSn(主订单编号)
     * @param status
     */
    @Update("update sub_order set status = #{status} " +
            "where parent_order_sn = #{parentOrderSn}")
    void setStatusWithoutUserId(
            @Param("parentOrderSn") String parentOrderSn,
            @Param("status") Integer status);



    /**
     * 修改子订单支付时间和支付类型
     * @param parentOrderSn
     * @param paymentType
     * @param paymentTime
     */
    @Update("update sub_order set payment_type = #{paymentType}, " +
            "payment_time = #{paymentTime} " +
            "where parent_order_sn = #{parentOrderSn}")
    void setPaymentType(
            @Param("parentOrderSn") String parentOrderSn,
            @Param("paymentType") Integer paymentType,
            @Param("paymentTime") LocalDateTime paymentTime);


    /**
     * 修改子订单状态
     * @param subOrderSn
     * @param status
     * @return
     */
    @Update("update sub_order set status = #{status} " +
            "where sub_order_sn = #{subOrderSn}")
    int updateStatus(
            @Param("subOrderSn") String subOrderSn,
            @Param("status") Integer status);


    /**
     * 查询未成功的订单数量
     * @param parentOrderSn
     * @return
     */
    @Select("select count(*) from sub_order where " +
            "parent_order_sn = #{parentOrderSn} " +
            "and status in (1,2,5) ")
    int selectUnSuccess(@Param("parentOrderSn") String parentOrderSn);



    /**
     * 查询未支付的订单数量
     * @param parentOrderSn
     * @return
     */
    @Select("select count(*) from sub_order where " +
            "parent_order_sn = #{parentOrderSn} " +
            "and status = 0 ")
    int selectUnpaid(@Param("parentOrderSn") String parentOrderSn);




    /**
     * 根据主订单编号查询所有子订单ID
     * @param parentOrderSn
     * @return
     */
    @Select("select id from sub_order " +
            "where parent_order_sn = #{parentOrderSn}")
    List<Long> selectIdsByParentSn(@Param("parentOrderSn") String parentOrderSn);


    /**
     * 根据子订单编号查询子订单
     * @param subOrderSn
     */
    @Select("select * from sub_order " +
            "where sub_order_sn = #{subOrderSn}")
    SubOrder selectByOrderSn(@Param("subOrderSn") String subOrderSn);


    /**
     * 根据编号查询子订单（加行锁）
     * @param subOrderSn
     * @return
     */
    @Select("select * from sub_order " +
            "where sub_order_sn = #{subOrderSn} for update")
    SubOrder selectBySnForUpdate(
            @Param("subOrderSn") String subOrderSn);


    /**
     * 根据主订单编号查询子订单编号
     * @param parentOrderSn
     * @return
     */
    @Select("select sub_order_sn from sub_order " +
            "where parent_order_sn = #{parentOrderSn}")
    List<String> selectSnByParentSn(@Param("parentOrderSn") String parentOrderSn);


    /**
     * 根据主订单编号查询子订单ID及编号
     * @param parentOrderSn
     * @return
     */
    @Select("select id, sub_order_sn from sub_order " +
            "where parent_order_sn = #{parentOrderSn}")
    List<SubOrderIdSn> selectIdSnByParentSn(@Param("parentOrderSn") String parentOrderSn);


    /**
     * 根据子订单编号修改子订单状态
     * @param subOrderSn
     * @param status
     */
    @Update("update sub_order set status = #{status} " +
            "where sub_order_sn = #{subOrderSn}")
    void setStatusBySubOrderSn
            (@Param("subOrderSn") String subOrderSn,
             @Param("status") Integer status);


    /**
     * 根据主订单编号查询子订单编号集合
     * @param parentOrderSn
     * @return
     */
    @Select("select sub_order_sn from sub_order " +
            "where parent_order_sn = #{parentOrderSn}")
    List<String> selectSnsByParentSn(
            @Param("parentOrderSn") String parentOrderSn);




    /**
     * 根据主订单编号更新支付相关字段
     * @param parentOrderSn
     * @param status
     * @param paymentType
     * @param paymentTime
     */
    @Update("update sub_order set status = #{status}," +
            "payment_type = #{paymentType}," +
            "payment_time = #{paymentTime} " +
            "where parent_order_sn = #{parentOrderSn} " +
            "and status = #{oldStatus}")
    int updatePayInfoByParentSn(
            @Param("parentOrderSn") String parentOrderSn,
            @Param("status") Integer status,
            @Param("paymentType") Integer paymentType,
            @Param("paymentTime") LocalDateTime paymentTime,
            @Param("oldStatus") Integer oldStatus);


    /**
     * 根据子订单编号更新支付相关字段
     * @param subOrderSn
     * @param status
     * @param paymentType
     * @param paymentTime
     */
    @Update("update sub_order set status = #{status}," +
            "payment_type = #{paymentType}," +
            "payment_time = #{paymentTime} " +
            "where sub_order_sn = #{subOrderSn} " +
            "and status = #{oldStatus}")
    int updatePayInfo(
            @Param("subOrderSn") String subOrderSn,
            @Param("status") Integer status,
            @Param("paymentType") Integer paymentType,
            @Param("paymentTime") LocalDateTime paymentTime,
            @Param("oldStatus") Integer oldStatus);



    /**
     * 根据子订单编号更新支付相关字段
     * @param subOrderSn
     * @param status
     * @param paymentType
     * @param paymentTime
     */
    @Update("update sub_order set status = #{status}," +
            "payment_type = #{paymentType}," +
            "payment_time = #{paymentTime} " +
            "where sub_order_sn = #{subOrderSn}")
    void updatePayInfoBySubSn(
            @Param("subOrderSn") String subOrderSn,
            @Param("status") Integer status,
            @Param("paymentType") Integer paymentType,
            @Param("paymentTime") LocalDateTime paymentTime);



    /**
     * 查询子订单状态
     * @param subOrderSn
     */
    @Select("select status from sub_order " +
            "where sub_order_sn = #{subOrderSn}")
    Integer selectStatus(String subOrderSn);


    /**
     * 查询子订单退款状态
     * @param subOrderSn
     */
    @Select("select refund_status from sub_order " +
            "where sub_order_sn = #{subOrderSn}")
    Integer selectRefundStatus(String subOrderSn);

    /**
     * 修改子订单的退款状态
     * @param subOrderSn
     * @param refundStatus
     */
    @Update("update sub_order set refund_status = #{refundStatus} " +
            "where sub_order_sn = #{parentOrderSn}")
    void setRefundStatusBySubOrderSn(String subOrderSn, int refundStatus);


    /**
     * 根据主订单编号修改子订单主状态及退款状态
     * @param parentOrderSn
     * @param status
     * @param refundStatus
     */
    @Update("update sub_order set status = #{status}," +
            "refund_status = #{refundStatus} " +
            "where parent_order_sn = #{parentOrderSn}")
    void setStatusAndRefundStatusByParentSn(String parentOrderSn, Integer status, Integer refundStatus);

    /**
     * 修改子订单主状态及退款状态
     * @param subOrderSn
     * @param status
     * @param refundStatus
     */
    @Update("update sub_order set status = #{status}," +
            "refund_status = #{refundStatus} " +
            "where sub_order_sn = #{subOrderSn}")
    void setStatusAndRefundStatus(String subOrderSn, Integer status, Integer refundStatus);
}
