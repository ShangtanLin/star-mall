package com.github.shangtanlin.controller;

import com.github.shangtanlin.common.utils.UserHolder;
import com.github.shangtanlin.model.dto.order.OrderConfirmDTO;
import com.github.shangtanlin.model.dto.order.OrderSubmitDTO;
import com.github.shangtanlin.model.vo.OrderCreateVO;
import com.github.shangtanlin.model.vo.order.OrderConfirmVO;
import com.github.shangtanlin.model.vo.order.OrderStatusVO;
import com.github.shangtanlin.model.vo.order.SubOrderVO;
import com.github.shangtanlin.result.PageResult;
import com.github.shangtanlin.result.Result;
import com.github.shangtanlin.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/order")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 1. 预下单
     * @param orderConfirmDTO 选择的商品详情
     * @return 聚合后的确认单信息
     */
    @PostMapping("/checkout")
    @Operation(summary = "预下单确认接口")
    public Result<OrderConfirmVO> getPreOrder(@RequestBody OrderConfirmDTO orderConfirmDTO) {
        // 调用 Service 进行内存计算（查地址、查库存、算价格、分摊优惠）
        OrderConfirmVO confirmVO = orderService.getPreOrder(orderConfirmDTO);
        return Result.ok(confirmVO);
    }


    /**
     * 提交订单（主订单）
     * @param orderSubmitDTO
     * @return
     */
    @PostMapping("/submit")
    @Operation(summary = "提交订单接口")
    public Result<?> submitOrder(@RequestBody OrderSubmitDTO orderSubmitDTO) {
        // 核心逻辑：创建 ParentOrder -> 创建多条 SubOrder -> 创建多条 OrderItem -> 扣减库存 -> 清理购物车
        // 返回父订单号（orderSn），前端拿到后跳转支付页
        OrderCreateVO vo = orderService.submitOrder(orderSubmitDTO);
        return Result.ok(vo);
    }



    /**
     * 重新提交（子订单）
     * @param subOrderSn
     * @return
     */
    @PostMapping("/submit/resume/{subOrderSn}")
    @Operation(summary = "继续支付接口")
    public Result<?> resumePay(@PathVariable("subOrderSn") String subOrderSn) {
        OrderCreateVO vo = orderService.resumePay(subOrderSn);
        return Result.ok(vo);
    }



     /**
     * 取消主订单
     * @param parentOrderSn(主订单编号)
     * @return
     */
    @PutMapping("/cancel/{parentOrderSn}")
    public Result<?> cancelOrder(@PathVariable("parentOrderSn") String parentOrderSn) {
        orderService.cancelParentOrder(parentOrderSn);
        return Result.ok();
    }


    /**
     * 取消子订单
     * @param subOrderSn(子订单编号)
     * @return
     */
    @PutMapping("/cancel/{subOrderSn}")
    public Result<?> cancelSubOrder(@PathVariable("subOrderSn") String subOrderSn) {
        //orderService.cancelSubOrder(subOrderSn);
        return Result.ok();
    }




    /**
     * 查询订单列表(ES索引实现 - 支持一键通搜)
     * @param keyword  搜索关键词（既能搜订单号，也能搜商品名）
     * @param status   订单状态筛选（可选）
     * @param pageNo   当前页码
     * @param pageSize 分页大小
     */
    @GetMapping("/list")
    public Result<?> getSubOrderList(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "12") Integer pageSize) throws IOException {

        // 调用 Service 层的新接口逻辑
        PageResult<SubOrderVO> subOrderVOPageResult = orderService
                .getSubOrderList(
                        keyword,
                        status,
                        pageNo,
                        pageSize);

        return Result.ok(subOrderVOPageResult);
    }


    /**
     * 查询主订单详情
     * @param parentOrderSn 主订单编号
     */
    @GetMapping("/detail/parent/{parentOrderSn}")
    public Result<?> getParentOrderDetail(@PathVariable("parentOrderSn") String parentOrderSn) {
        // 1. 获取当前登录用户ID
        Long userId = UserHolder.getUser().getId();

        // 2. 调用业务层获取详情
        List<SubOrderVO> detail = orderService.getParentOrderDetail(parentOrderSn, userId);

        return Result.ok(detail);
    }


    /**
     * 查询子订单详情
     * @param subOrderSn 子订单编号
     */
    @GetMapping("/detail/sub/{subOrderSn}")
    public Result<?> getSubOrderDetail(@PathVariable("subOrderSn") String subOrderSn) {
        // 1. 获取当前登录用户ID
        Long userId = UserHolder.getUser().getId();

        // 2. 调用业务层获取详情
        SubOrderVO detail = orderService.getSubOrderDetail(subOrderSn, userId);

        return Result.ok(detail);
    }



    /**
     * 确认收货
     * @param subOrderSn(子订单编号)
     */
    @PostMapping("/confirm/{subOrderSn}")
    public Result<?> confirmReceipt(@PathVariable("subOrderSn") String subOrderSn) {
        // 1. 调用 Service 执行确认收货逻辑
        // 内部包含：校验订单所属用户、修改数据库状态、同步更新 ES
        orderService.confirmReceive(subOrderSn);

        return Result.ok();
    }


    ///**
    // * 删除订单 (逻辑删除)
    // * @param subOrderSn 子订单编号
    // */
    //@DeleteMapping("/delete/{subOrderSn}")
    //public Result<?> deleteOrder(@PathVariable("subOrderSn") String subOrderSn) {
    //    // 1. 调用 Service 执行删除逻辑
    //    // 内部包含：权限校验、MySQL 状态更新、ES 索引更新
    //    //OrderService.deleteSubOrder(subOrderSn);
    //
    //    return Result.ok();
    //}




    /**
     * 查询主订单状态（供前端轮询）
     * @param parentOrderSn 主订单编号
     */
    @GetMapping("/status/parent/{parentOrderSn}")
    public Result<?> getParentOrderStatus(@PathVariable("parentOrderSn") String parentOrderSn) {
        OrderStatusVO vo = orderService.getParentOrderStatus(parentOrderSn);
        return Result.ok(vo);
    }



    /**
     * 查询子订单状态（供前端轮询）
     * @param subOrderSn 子订单编号
     */
    @GetMapping("/status/sub/{subOrderSn}")
    public Result<?> getSubOrderStatus(@PathVariable("subOrderSn") String subOrderSn) {
        OrderStatusVO vo = orderService.getSubOrderStatus(subOrderSn);
        return Result.ok(vo);
    }







}
