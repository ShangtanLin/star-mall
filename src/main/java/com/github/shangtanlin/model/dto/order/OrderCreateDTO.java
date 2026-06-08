package com.github.shangtanlin.model.dto.order;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class OrderCreateDTO {
    // 1. 收货地址信息（可以直接复用你之前的 UserAddressDTO，或者传一个 addressId）
    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    // 优惠券领取记录ID
    private Long couponUserRecordId;

    // 2. 支付方式 (例如：1-微信, 2-支付宝)
    private Integer payType;

    // 3. 订单备注
    private String remark;

    // 4. 商品清单 (这是最核心的)
    @NotEmpty(message = "商品不能为空")
    private List<OrderItemDTO> items;

    /**
     * 内部类：订单项详情
     */
    @Data
    public static class OrderItemDTO {
        @NotNull(message = "商品SKU不能为空")
        private Long skuId;

        @NotNull(message = "购买数量不能为空")
        private Integer quantity;
    }

}
