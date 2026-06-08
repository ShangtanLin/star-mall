package com.github.shangtanlin.service;


import com.github.shangtanlin.model.dto.cart.CartItemDTO;
import com.github.shangtanlin.model.vo.CartItemVO;
import com.github.shangtanlin.result.Result;

import java.util.List;

public interface CartService {

    /**
     * 获取购物车展示卡片列表
     * @return
     */
    List<CartItemVO> getCartList();



    /**
     * 添加购物车
     * @param cartItemDTO
     * @return
     */
    Result<?> addToCart(CartItemDTO cartItemDTO);



    /**
     * 删除购物车
     * @param skuId
     * @return
     */
    Result<?> deleteFromCart(Long skuId);



    /**
     * 更新购物车
     * @param cartItemDTO
     * @return
     */
    Result<?> updateCart(CartItemDTO cartItemDTO);
}
