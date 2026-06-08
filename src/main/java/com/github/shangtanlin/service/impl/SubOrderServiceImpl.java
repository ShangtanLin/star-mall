package com.github.shangtanlin.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;

import co.elastic.clients.elasticsearch.core.search.HighlightField;

import co.elastic.clients.util.NamedValue;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.shangtanlin.common.exception.BusinessException;
import com.github.shangtanlin.common.utils.UserHolder;
import com.github.shangtanlin.config.mq.SubOrderMQConfig;
import com.github.shangtanlin.mapper.OrderItemMapper;
import com.github.shangtanlin.mapper.ShopMapper;
import com.github.shangtanlin.mapper.SubOrderMapper;
import com.github.shangtanlin.mq.message.OrderSyncMessage;
import com.github.shangtanlin.model.dto.es.SubOrderIndexDoc;
import com.github.shangtanlin.model.entity.order.OrderItem;
import com.github.shangtanlin.model.entity.order.SubOrder;
import com.github.shangtanlin.model.entity.shop.Shop;
import com.github.shangtanlin.common.enums.OrderStatusEnum;
import com.github.shangtanlin.model.vo.order.SubOrderVO;
import com.github.shangtanlin.result.PageResult;
import com.github.shangtanlin.service.OrderService;
import com.github.shangtanlin.service.SubOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SubOrderServiceImpl
        extends ServiceImpl<SubOrderMapper, SubOrder> // 1. 继承基类，获得 CRUD 能力
        implements SubOrderService {

    @Autowired
    private OrderService orderService;


    @Autowired
    private SubOrderMapper subOrderMapper;

    @Autowired
    private ElasticsearchClient elasticsearchClient;


    @Autowired
    private OrderItemMapper orderItemMapper;


    @Autowired
    private ShopMapper shopMapper;


    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    @Qualifier("orderQueryExecutor")
    private ThreadPoolExecutor orderQueryExecutor;


    /**
     * 查询订单列表
     * @param subOrderId
     * @param status
     * @param spuName
     * @param pageNo
     * @param pageSize
     * @return
     */
    @Override
    public PageResult<SubOrderVO> getSubOrderList(Long subOrderId, Integer status,
                                                  String spuName, Integer pageNo, Integer pageSize) throws IOException, IOException {
        // 1. 获取当前登录用户 ID (安全隔离核心)
        Long userId = UserHolder.getUser().getId();

        // 2. 构建查询条件 (使用 9.x Query.of 风格)
        Query query = Query.of(q -> q.bool(b -> {
            // 核心：锁定当前用户
            b.must(m -> m.term(t -> t.field("user_id").value(userId)));

            // 精确匹配子订单ID
            if (subOrderId != null) {
                b.must(m -> m.term(t -> t.field("id").value(subOrderId)));
            }


            // 2. 逻辑删除过滤（核心：只看活着的订单）
            b.must(m -> m.term(t -> t.field("is_delete").value(0)));

            // 状态过滤
            if (status != null) {
                b.must(m -> m.term(t -> t.field("status").value(status)));
            }

            // ✅ Nested 嵌套查询处理商品名
            if (StringUtils.hasText(spuName)) {
                b.must(m -> m.nested(n -> n
                        .path("items")
                        .query(nq -> nq.match(mq -> mq
                                .field("items.spu_name")
                                .query(spuName)
                        ))
                ));
            }
            return b;
        }));

        // 3. 构建 SearchRequest (使用 9.1+ NamedValue 方式处理高亮)
        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index("sub_order_index")
                .query(query)
                .from((pageNo - 1) * pageSize)
                .size(pageSize)
                .sort(so -> so.field(f -> f.field("create_time").order(SortOrder.Desc)))
                .highlight(h -> h
                        .preTags("<em style='color:red;'>")
                        .postTags("</em>")
                        .fields(Collections.singletonList(
                                NamedValue.of("items.spu_name", HighlightField.of(hf -> hf))
                        ))
                )
        );

        // 4. 执行搜索 (参照 searchProduct)
        SearchResponse<SubOrderIndexDoc> response = elasticsearchClient.search(searchRequest, SubOrderIndexDoc.class);

        // 5. 结果映射 + 高亮处理
        List<SubOrderVO> voList = response.hits().hits().stream()
                .map(hit -> {
                    SubOrderIndexDoc doc = hit.source();
                    if (doc == null) return null;

                    // ✅ 关键：调用统一的转换函数，处理金额、状态描述等
                    SubOrderVO vo = convertToVO(doc);

                    // 6. 保持你原来的高亮覆盖逻辑
                    if (hit.highlight() != null && !hit.highlight().isEmpty()) {
                        if (hit.highlight().containsKey("items.spu_name")) {
                            String highlightedName = hit.highlight().get("items.spu_name").get(0);
                            if (vo.getItems() != null && !vo.getItems().isEmpty()) {
                                // 覆盖第一个命中的商品名
                                vo.getItems().get(0).setSpuName(highlightedName);
                            }
                        }
                    }
                    return vo;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 6. 按照你的 PageResult 构造返回
        return new PageResult<>(response.hits().total().value(), voList);
    }

    /**
     * 确认收货
     * @param subOrderId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(Long subOrderId) {
        // 1. 安全校验：获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        // 2. 查询子订单详情（校验归属权）
        SubOrder subOrder = this.getOne(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getId, subOrderId)
                .eq(SubOrder::getUserId, userId));

        if (subOrder == null) {
            throw new BusinessException("订单不存在或无权操作");
        }

        // 3. 幂等校验：如果已经是成功状态，直接返回
        if (OrderStatusEnum.SUCCESS.getCode().equals(subOrder.getStatus())) {
            return;
        }

        // 4. 更新数据库子订单状态
        subOrder.setStatus(OrderStatusEnum.SUCCESS.getCode());
        boolean updateSub = this.updateById(subOrder);
        if (!updateSub) {
            throw new BusinessException("确认收货失败，请稍后重试");
        }

        // 5. 核心逻辑：联动检查父订单
        checkAndCompleteParentOrder(subOrder.getParentOrderSn());


        // 逻辑：在事务提交后，通知消费者重新拉取最新的子订单视图
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 构建更新消息 (type=2 代表更新)
                    OrderSyncMessage message = new OrderSyncMessage(subOrderId, 2);

                    rabbitTemplate.convertAndSend(
                            SubOrderMQConfig.SUB_ORDER_EXCHANGE,
                            SubOrderMQConfig.SUB_ORDER_SYNC_ROUTING_KEY,
                            message
                    );
                    log.info("确认收货事务已提交，已发出 ES 同步消息，子订单ID: {}", subOrderId);
                }
            });
        }

    }


    /**
     * 删除订单 (逻辑删除)
     * @param subOrderId 子订单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSubOrder(Long subOrderId) {
        // 1. 获取当前登录用户 ID (安全屏障)
        Long userId = UserHolder.getUser().getId();

        // 2. 查询订单是否存在且属于该用户
        SubOrder subOrder = this.getOne(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getId, subOrderId)
                .eq(SubOrder::getUserId, userId));

        if (subOrder == null) {
            throw new BusinessException("订单不存在或无权操作");
        }

        // 3. 业务校验：只有【已完成】或【已关闭】的订单才能删除
        Integer status = subOrder.getStatus();
        if (!status.equals(OrderStatusEnum.SUCCESS.getCode()) &&
                !status.equals(OrderStatusEnum.CLOSED.getCode())) {
            throw new BusinessException("当前订单状态不允许删除，请先确认收货或取消订单");
        }

        // 4. 执行数据库逻辑删除 (MyBatis-Plus 自动处理 update_time)
        boolean success = this.update(new LambdaUpdateWrapper<SubOrder>()
                .eq(SubOrder::getId, subOrderId)
                .set(SubOrder::getIsDelete, 1)); // 标记为已删除

        if (!success) {
            throw new BusinessException("数据库更新失败");
        }

        // 5. 同步更新 Elasticsearch (非常重要：否则搜索依然能搜出已删除订单)
        try {
            elasticsearchClient.update(u -> u
                            .index("sub_order_index")
                            .id(subOrderId.toString())
                            .doc(Collections.singletonMap("is_delete", 1)), // 局部更新 ES 标记
                    SubOrderIndexDoc.class
            );
            log.info("ES 订单逻辑删除同步成功，ID: {}", subOrderId);
        } catch (IOException e) {
            log.error("ES 订单逻辑删除同步失败，ID: {}", subOrderId, e);
            // 根据业务需求决定是否回滚，通常建议记录日志并由补偿任务重试
        }

    }




    /**
     * 从数据库中查出子订单表数据并封装成SubOrderVO
     * @param subOrderId
     * @return
     */
    @Override
    public SubOrderVO getDetailForEs(Long subOrderId) {
        // 1. 查询子订单主表数据
        SubOrder subOrder = this.getById(subOrderId);
        if (subOrder == null) {
            log.warn("查询 ES 同步数据失败：子订单 ID {} 不存在", subOrderId);
            return null;
        }

        // 2. 查询关联的商品明细列表 (OrderItem)
        List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getSubOrderId, subOrderId)
        );

        // 3. 封装并转换成 SubOrderVO
        SubOrderVO vo = new SubOrderVO();
        BeanUtils.copyProperties(subOrder, vo);
        vo.setSubOrderId(subOrderId);

        // 查询店铺信息
        Shop shop = shopMapper.selectById(vo.getShopId());
        vo.setShopName(shop.getShopName());
        vo.setShopLogo(shop.getShopLogo());

        // 转换并封装商品明细
        if (!CollectionUtils.isEmpty(orderItems)) {
            List<SubOrderVO.OrderItemVO> itemVOs = orderItems.stream().map(item -> {
                SubOrderVO.OrderItemVO itemVO = new SubOrderVO.OrderItemVO();
                BeanUtils.copyProperties(item, itemVO);
                return itemVO;
            }).collect(Collectors.toList());
            vo.setItems(itemVOs);
        }

        return vo;
    }


    /**
     * 数据转换逻辑：SubOrderIndexDTO -> SubOrderVO
     */
    private SubOrderVO convertToVO(SubOrderIndexDoc dto) {
        if (dto == null) return null;
        SubOrderVO vo = new SubOrderVO();

        // 1. 拷贝第一层简单属性 (id, status, shopName 等)
        BeanUtils.copyProperties(dto, vo);

        // 2. 手动补全第一层的金额 (Double -> BigDecimal)
        if (dto.getGoodsAmount() != null) vo.setGoodsAmount(BigDecimal.valueOf(dto.getGoodsAmount()));
        if (dto.getPayAmount() != null) vo.setPayAmount(BigDecimal.valueOf(dto.getPayAmount()));
        if (dto.getFreightAmount() != null) vo.setFreightAmount(BigDecimal.valueOf(dto.getFreightAmount()));

        // 3. 手动补全状态文字
        vo.setStatusDesc(OrderStatusEnum.getDescByCode(dto.getStatus()));

        // 4. 转换内部的 Items 列表 (核心修复点)
        if (dto.getItems() != null) {
            List<SubOrderVO.OrderItemVO> itemVos = dto.getItems().stream().map(item -> {
                SubOrderVO.OrderItemVO itemVo = new SubOrderVO.OrderItemVO();

                // 这里建议也手动赋值，或者确保名字完全对上
                itemVo.setSpuId(item.getSpuId());
                itemVo.setSpuName(item.getSpuName());
                itemVo.setSkuName(item.getSkuName());
                itemVo.setPicUrl(item.getPicUrl());
                itemVo.setQuantity(item.getQuantity());

                // ✅ 关键：手动处理 Price 的类型转换
                if (item.getPrice() != null) {
                    itemVo.setPrice(BigDecimal.valueOf(item.getPrice()));
                }

                return itemVo;
            }).collect(Collectors.toList());
            vo.setItems(itemVos);
        }
        return vo;
    }



    /**
     * 检查父订单下所有子订单状态，若全完成则关闭父订单
     */
    private void checkAndCompleteParentOrder(String parentOrderSn) {
        log.info(">>>>>> 开始联动检查父订单状态: {}", parentOrderSn);
        // 查询该父订单下【非成功】状态的子订单数量
        Long unFinishedCount = this.count(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getParentOrderSn, parentOrderSn)
                .ne(SubOrder::getStatus, OrderStatusEnum.SUCCESS.getCode())
                .ne(SubOrder::getStatus, OrderStatusEnum.CLOSED.getCode())); // 排除已关闭/已退款

        if (unFinishedCount == 0) {
            log.info("父订单 {} 下所有子订单已完成，更新父订单状态", parentOrderSn);
            // 更新父订单表状态
            orderService.updateStatus(parentOrderSn, OrderStatusEnum.SUCCESS.getCode());

            // 如果你以后给父订单建了 ES 索引，记得在这里也 update 一下
        }
    }
}
