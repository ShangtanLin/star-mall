package com.github.shangtanlin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.shangtanlin.model.dto.order.OrderItemDTO;
import com.github.shangtanlin.model.dto.order.OrderSkuInfo;
import com.github.shangtanlin.model.entity.product.Sku;

import java.util.List;
import java.util.Map;

public interface SkuService extends IService<Sku> {

    /**
     * 批量查询skuId并封装
     * @param skuIds
     * @return
     */
    Map<Long, OrderSkuInfo> getLatestSkuInfoBatch(List<Long> skuIds);

    /**
     * 批量查询skuId
     * @param skuIds
     * @return
     */
    Map<Long, Sku> getSkuMapByIds(List<Long> skuIds);

    /**
     * 回滚库存
     * @param stockItems
     */
    void rollBackStock(String orderSn, List<OrderItemDTO> stockItems);
}
