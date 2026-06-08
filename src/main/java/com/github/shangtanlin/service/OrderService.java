package com.github.shangtanlin.service;

import com.github.shangtanlin.model.dto.order.OrderConfirmDTO;
import com.github.shangtanlin.model.dto.order.OrderSubmitDTO;
import com.github.shangtanlin.model.vo.OrderCreateVO;
import com.github.shangtanlin.model.vo.order.OrderConfirmVO;
import com.github.shangtanlin.model.vo.order.OrderStatusVO;
import com.github.shangtanlin.model.vo.order.SubOrderVO;
import com.github.shangtanlin.result.PageResult;

import java.io.IOException;
import java.util.List;

public interface OrderService {
    //更新订单状态
    void updateStatus(String parentOrderSn, Integer status);

    /**
     * 预下单
     * @param orderConfirmDTO
     * @return
     */
    OrderConfirmVO getPreOrder(OrderConfirmDTO orderConfirmDTO);

    /**
     * 提交订单
     * @param orderSubmitDTO
     * @return
     */
    OrderCreateVO submitOrder(OrderSubmitDTO orderSubmitDTO);


    /**
     * 取消订单
     * @param parentOrderSn
     * @return
     */
    void cancelParentOrder(String parentOrderSn);






    /**
     * 查询子订单详情
     * @param orderSn（子订单编号）
     * @param userId
     * @return
     */
    SubOrderVO getSubOrderDetail(String orderSn, Long userId);



    /**
     * 支付成功
     * @param orderSn
     * @param paymentType
     * @return
     */
    void paySuccess(String orderSn, Integer paymentType);




    /**
     * 查询订单列表(ES索引实现)
     * @param keyword
     * @param status
     * @param pageNo
     * @param pageSize
     * @return
     */
    PageResult<SubOrderVO> getSubOrderList(
            String keyword,   // 统一关键词
            Integer status,
            Integer pageNo,
            Integer pageSize) throws IOException;


    /**
     * 子订单确认收货
     * @param subOrderSn
     */
    void confirmReceive(String subOrderSn);


    /**
     * 查询主订单状态
     * @param parentOrderSn
     */
    OrderStatusVO getParentOrderStatus(String parentOrderSn);



    /**
     * 查询主订单详情
     * @param parentOrderSn（子订单编号）
     * @param userId
     * @return
     */
    List<SubOrderVO> getParentOrderDetail(String parentOrderSn, Long userId);


    /**
     * 继续支付（子订单）
     * @param subOrderSn
     * @return
     */
    OrderCreateVO resumePay(String subOrderSn);





    /**
     * 查询主订单状态
     * @param subOrderSn
     */
    OrderStatusVO getSubOrderStatus(String subOrderSn);
}
