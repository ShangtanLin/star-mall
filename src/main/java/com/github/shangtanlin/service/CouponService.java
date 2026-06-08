package com.github.shangtanlin.service;

import com.github.shangtanlin.model.dto.coupon.CouponRecordDTO;
import com.github.shangtanlin.model.vo.coupon.CouponTemplateVO;
import com.github.shangtanlin.model.vo.coupon.UserCouponVO;
import com.github.shangtanlin.result.Result;

import java.math.BigDecimal;
import java.util.List;

public interface CouponService {

    /**
     * 查询领券中心
     * @return
     */
    List<CouponTemplateVO> getPublishingCoupon();

    /**
     * 领取优惠券
     * @param templateId
     * @return
     */
    Result<?> receiveCoupon(Long templateId);

    /**
     * 数据库异步扣减和领券记录插入
     * @param dto
     */
    void doReceiveRecord(CouponRecordDTO dto);

    /**
     * 查询我的优惠券列表
     * @param status
     * @return
     */
    List<UserCouponVO> getMyCoupons(Integer status);


    /**
     * 结算时查询当前订单可用优惠券
     * @param orderAmount
     * @return
     */
    List<UserCouponVO> getAvailableCoupons(BigDecimal orderAmount);

    /**
     * 锁定优惠券 (0 -> 1)
     * 调用时机：用户提交订单，后端创建订单记录时
     */
    void lockCoupon(String orderSn, Long recordId);

    /**
     * 正式核销优惠券 (1 -> 2)
     * 调用时机：支付系统回调，确认支付成功时
     */
    void useCoupon(Long recordId);


    /**
     * 释放/回滚优惠券 (1 -> 0)
     * 调用时机：用户手动取消订单 或 支付超时自动关闭订单
     */
    void releaseCoupon(String orderSn, Long recordId);

    /**
     * 计算当前优惠券的优惠金额（是否可用）
     * @param couponUserRecordId
     * @param totalAmount
     * @return
     */
    BigDecimal calculateDiscountAmount(Long couponUserRecordId, BigDecimal totalAmount);
}
