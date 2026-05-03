package com.github.shangtanlin.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CartItemVO {
    private Long spuId;

    private Long skuId;

    private Long userId;

    private Integer checked; //存入redis

    private Integer quantity; //存入redis

    private BigDecimal price;

    private String image;

    private String title;

    private String specJson;

    private LocalDateTime createTime; //存入redis


    //商家的信息
    private Long shopId;
    private String shopName;

    //商品状态信息（供前端展示）
    private String spuStatusDesc;   // "在售" 或 "下架"
    private String skuStockDesc;    // "库存充足" 或 "库存不足" 或 "已售空"

}
