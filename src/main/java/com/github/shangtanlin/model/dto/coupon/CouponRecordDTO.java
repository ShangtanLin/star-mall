package com.github.shangtanlin.model.dto.coupon;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 记录优惠券和用户的消息传输对象 (DTO)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CouponRecordDTO implements Serializable {
    private Long userId;
    private Long templateId;
    // 建议增加一个唯一流水号，用于入库幂等性校验
    private String traceId;
}