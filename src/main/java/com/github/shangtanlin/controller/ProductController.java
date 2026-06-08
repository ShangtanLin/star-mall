package com.github.shangtanlin.controller;

import com.github.shangtanlin.model.dto.product.ProductQueryDTO;
import com.github.shangtanlin.result.PageResult;
import com.github.shangtanlin.result.Result;
import com.github.shangtanlin.service.ProductService;
import com.github.shangtanlin.model.vo.ProductCardVO;
import com.github.shangtanlin.model.vo.ProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/product")
@Slf4j
public class ProductController {

    @Autowired
    private ProductService productService;

    /**
     * 获取商品列表
     * @param productQueryDTO
     * @return
     */
    @PostMapping("/list") //将参数封装到body里，用于应对多参数的情况
    public Result<?> getProductList(@RequestBody ProductQueryDTO productQueryDTO)  {
        PageResult<ProductCardVO> productPageResult = productService.list(productQueryDTO);
        return Result.ok(productPageResult);
    }

    /**
     * 获取商品展示卡片详情
     * @param spuId
     * @return
     */
    @GetMapping("/{spuId}")
    public Result<?> getProductById(@PathVariable("spuId") Long spuId)  {
        ProductVO productVO = productService.getProductById(spuId);
        return Result.ok(productVO);
    }

    /**
     * 搜索商品
     * @param keyword
     * @param categoryId
     * @param pageNo
     * @param pageSize
     * @return
     * @throws IOException
     */
    @GetMapping("/search")
    public Result<?> searchProduct(@RequestParam(value = "keyword",required = false) String keyword,
                                   @RequestParam(value = "categoryId",required = false) Long categoryId,
                                   @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
                                   @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
                                   @RequestParam(value="pageNo",defaultValue = "1") Integer pageNo,
                                   @RequestParam(value="pageSize",defaultValue = "12") Integer pageSize) throws IOException {
        PageResult<ProductCardVO> productPageResult = productService.searchProduct(keyword,categoryId,minPrice,maxPrice,pageNo,pageSize);
        return Result.ok(productPageResult);
    }


    /**
     * 获取今日热门商品
     * @return
     */
    @GetMapping("/hotProductsToday")
    public Result<?> hotProductsToday() {
        List<ProductCardVO> productCardVOS = productService.getHotProductsToday();
        return Result.ok(productCardVOS);
    }




}
