package com.github.shangtanlin.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.shangtanlin.common.exception.BusinessException;
import com.github.shangtanlin.mapper.ParentOrderMapper;
import com.github.shangtanlin.mapper.ShopMapper;
import com.github.shangtanlin.mapper.SkuMapper;
import com.github.shangtanlin.mapper.SpuMapper;
import com.github.shangtanlin.model.dto.order.OrderItemDTO;
import com.github.shangtanlin.model.dto.order.OrderSkuInfo;
import com.github.shangtanlin.model.entity.order.ParentOrder;
import com.github.shangtanlin.model.entity.product.Sku;
import com.github.shangtanlin.model.entity.product.Spu;
import com.github.shangtanlin.model.entity.shop.Shop;
import com.github.shangtanlin.service.SkuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SkuServiceImpl
        extends ServiceImpl<SkuMapper, Sku>
        implements SkuService {

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private ShopMapper shopMapper;

    /**
     * 批量查询sku，并将skuId及其详情封装到map中
     * @param skuIds
     * @return
     */
    @Override
    public Map<Long, OrderSkuInfo> getLatestSkuInfoBatch(List<Long> skuIds) {
        if (CollUtil.isEmpty(skuIds)) return Collections.emptyMap();

        // 1. 查询 SKU 基础信息 (MyBatis-Plus 示例)
        List<Sku> skus = skuMapper.selectBatchIds(skuIds);

        // 提取关联的 SPU ID 和 Shop ID 用于后续批量查询
        Set<Long> spuIds = skus.stream().map(Sku::getSpuId).collect(Collectors.toSet());
        Set<Long> shopIds = skus.stream().map(Sku::getShopId).collect(Collectors.toSet());

        // 2. 批量查询 SPU 信息 ，并将对应的skuId一起封装到map
        Map<Long, String> spuNameMap = spuMapper.selectBatchIds(spuIds).stream()
                .collect(Collectors.toMap(Spu::getId, Spu::getName));

        // 3. 批量查询 店铺信息 ，并将对应的shopName一起封装到map
        Map<Long, String> shopNameMap = shopMapper.selectBatchIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, Shop::getShopName));

        // 4. 组装成 OrderSkuInfo 聚合对象
        return skus.stream().map(sku -> {
            OrderSkuInfo info = new OrderSkuInfo();
            info.setSkuId(sku.getId());
            info.setSkuName(sku.getSkuTitle());
            info.setPrice(sku.getPrice()); // 数据库实时价
            info.setPicUrl(sku.getImage());
            info.setStock(sku.getStock());

            info.setSpuId(sku.getSpuId());
            info.setSpuName(spuNameMap.get(sku.getSpuId()));

            info.setShopId(sku.getShopId());
            info.setShopName(shopNameMap.get(sku.getShopId()));
            return info;
        }).collect(Collectors.toMap(OrderSkuInfo::getSkuId, info -> info));

    }

    /**
     * 批量查询SkuId
     * @param skuIds
     * @return
     */
    @Override
    public Map<Long, Sku> getSkuMapByIds(List<Long> skuIds) {
        if (CollectionUtils.isEmpty(skuIds)) {
            return Collections.emptyMap();
        }

        // 1. 调用 Mapper 的批量查询方法（MyBatis-Plus 自带 selectBatchIds）
        List<Sku> skuList = skuMapper.selectBatchIds(skuIds);

        // 2. 将 List 转换为 Map，Key 是 ID，Value 是对象本身
        return skuList.stream().collect(Collectors.toMap(
                Sku::getId,        // Key: Sku对象的ID
                sku -> sku,        // Value: Sku对象本身
                (existing, replacement) -> existing // 如果ID重复（理论上不会），保留第一个
        ));
    }

    @Override
    public void rollBackStock(String orderSn, List<OrderItemDTO> stockItems) {
        // 1. 非空校验
        if (CollectionUtils.isEmpty(stockItems)) {
            return;
        }

        // 2. 遍历回补
        for (OrderItemDTO item : stockItems) {
            Long skuId = item.getSkuId();
            Integer quantity = item.getQuantity();

            // 3. 执行原子加法()
            int rows = skuMapper.addStock(skuId, quantity);

            // 4. 防御性检查
            if (rows == 0) {
                // 如果返回0，说明数据库里找不到这个skuId（可能被管理员物理删除了）
                // 这种情况下抛出异常，触发事务回滚，保证逻辑一致性
                log.error("库存回补失败，商品不存在！主订单号：{}， skuId: {}", orderSn, skuId);
                throw new BusinessException("系统错误：商品不存在");
            }
        }

        log.info("[库存回补] 库存回补成功，共 {} 种商品，主订单号：{}，", stockItems.size(),orderSn);
    }
}
