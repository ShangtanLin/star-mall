package com.github.shangtanlin.model.dto.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PreOrderItemDTO {
    // 1. 唯一标识：我们要去数据库查这个 SKU 的最新信息
    @NotNull(message = "商品SKU不能为空")
    private Long skuId;

    // 2. 购买数量：计算总价和校验库存的关键
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    private Integer quantity;
}
