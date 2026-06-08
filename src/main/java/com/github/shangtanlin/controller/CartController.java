package com.github.shangtanlin.controller;

import com.github.shangtanlin.model.dto.cart.CartItemDTO;
import com.github.shangtanlin.model.vo.CartItemVO;
import com.github.shangtanlin.result.Result;
import com.github.shangtanlin.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    @Autowired
    private CartService cartService;

    /**
     * 获取购物车展示卡片列表
     * @return
     */
    @GetMapping("/list")
    public Result<?> getCartList() {
        List<CartItemVO> cartItemVOS = cartService.getCartList();
        return Result.ok(cartItemVOS);
    }

    /**
     * 添加购物车
     * @param cartItemDTO
     * @return
     */
    @PostMapping("/add")
    public Result<?> addToCart(@RequestBody CartItemDTO cartItemDTO) {
        return cartService.addToCart(cartItemDTO);
    }


    /**
     * 删除购物车
     * @param skuId
     * @return
     */
    @DeleteMapping("/delete/{skuId}")
    public Result<?> deleteFromCart(@PathVariable("skuId") Long skuId) {
        return cartService.deleteFromCart(skuId);
    }


    /**
     * 更新购物车
     * @param cartItemDTO
     * @return
     */
    @PutMapping("/update")
    public Result<?> updateCart(@RequestBody CartItemDTO cartItemDTO) {
        return cartService.updateCart(cartItemDTO);
    }


}
