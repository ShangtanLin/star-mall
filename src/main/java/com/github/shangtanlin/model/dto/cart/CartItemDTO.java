package com.github.shangtanlin.model.dto.cart;

import lombok.Data;

@Data
public class CartItemDTO {
    private Long skuId;

    private Integer quantity;

    private Integer checked;
}
