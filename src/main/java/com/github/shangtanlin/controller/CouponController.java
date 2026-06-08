package com.github.shangtanlin.controller;

import com.github.shangtanlin.common.exception.BusinessException;
import com.github.shangtanlin.common.utils.UserHolder;
import com.github.shangtanlin.model.vo.coupon.CouponTemplateVO;
import com.github.shangtanlin.model.vo.coupon.UserCouponVO;
import com.github.shangtanlin.result.Result;
import com.github.shangtanlin.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/coupon")
@Slf4j
public class CouponController {
    @Autowired
    private CouponService couponService;


    /**
     * 查询领券中心
     * @return
     */
    @GetMapping("/center/list")
    public Result<?> getPublishingCoupon() {
        List<CouponTemplateVO> list = couponService.getPublishingCoupon();
        return Result.ok(list);
    }



    /**
     * 领取优惠券接口（秒杀模块）
     * @param templateId 优惠券模板ID
     * @return 统一返回体
     */
    @PostMapping("/receive/{templateId}")
    public Result<?> receiveCoupon(@PathVariable("templateId") Long templateId) {

        return couponService.receiveCoupon(templateId);

    }

    /**
     * 查询我的优惠券列表
     * @param status
     * @return
     */
    @GetMapping("/my/list")
    public Result<?> getMyCoupons(@RequestParam(value = "status",required = false) Integer status) {


        List<UserCouponVO> list = couponService.getMyCoupons(status);
        return Result.ok(list);
    }

    /**
     * 结算时查询当前订单可用优惠券
     * @param orderAmount
     * @return
     */
    @GetMapping("/available")
    public Result<?> getAvailable(@RequestParam("orderAmount") BigDecimal orderAmount) {
        List<UserCouponVO> list = couponService.getAvailableCoupons(orderAmount);
        return Result.ok(list);
    }



    ///**
    // * 锁定优惠券 (0 -> 1)
    // * 调用时机：用户提交订单，后端创建订单记录时
    // */
    //@PostMapping("/lock")
    //public Result<?> lock(@RequestParam("recordId") Long recordId) {
    //    couponService.lockCoupon(recordId);
    //    return Result.ok();
    //}


    /**
     * 正式核销优惠券 (1 -> 2)
     * 调用时机：支付系统回调，确认支付成功时
     */
    @PostMapping("/use")
    public Result<Void> use(@RequestParam("recordId") Long recordId) {
        couponService.useCoupon(recordId);
        return Result.ok();
    }


    ///**
    // * 释放/回滚优惠券 (1 -> 0)
    // * 调用时机：用户手动取消订单 或 支付超时自动关闭订单
    // */
    //@PostMapping("/release")
    //public Result<Void> release(@RequestParam("recordId") Long recordId) {
    //    couponService.releaseCoupon(recordId);
    //    return Result.ok();
    //}

}
