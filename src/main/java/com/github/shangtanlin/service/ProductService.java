package com.github.shangtanlin.service;


import com.github.shangtanlin.model.dto.product.ProductQueryDTO;
import com.github.shangtanlin.result.PageResult;
import com.github.shangtanlin.model.vo.ProductCardVO;
import com.github.shangtanlin.model.vo.ProductVO;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

public interface ProductService {
    PageResult<ProductCardVO> list(ProductQueryDTO productQueryDTO);

    ProductVO getProductById(Long spuId);

    PageResult<ProductCardVO> searchProduct(String keyword, Long categoryId,
                                            BigDecimal minPrice, BigDecimal maxPrice,
                                            Integer pageNo, Integer pageSize)
            throws IOException;

    List<ProductCardVO> getHotProductsToday();

    void deductStock(Long skuId, Integer quantity);

    void rollbackStock(Long skuId, Integer quantity);
}
