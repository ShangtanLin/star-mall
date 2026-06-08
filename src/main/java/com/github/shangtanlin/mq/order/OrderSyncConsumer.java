package com.github.shangtanlin.mq.order;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.alibaba.fastjson.JSON;
import com.github.shangtanlin.common.utils.RabbitMQConsumeUtils;
import com.github.shangtanlin.config.mq.SubOrderMQConfig;
import com.github.shangtanlin.mapper.OrderMapper;
import com.github.shangtanlin.mapper.ParentOrderMapper;
import com.github.shangtanlin.mapper.SubOrderMapper;
import com.github.shangtanlin.mq.message.OrderSyncMessage;
import com.github.shangtanlin.model.dto.es.SubOrderIndexDoc;
import com.github.shangtanlin.model.entity.order.ParentOrder;
import com.github.shangtanlin.model.entity.order.SubOrder;
import com.github.shangtanlin.model.vo.order.SubOrderVO;
import com.github.shangtanlin.service.SubOrderService;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OrderSyncConsumer {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ElasticsearchClient elasticsearchClient;

    @Resource
    private SubOrderService subOrderService;

    @Autowired
    private ParentOrderMapper parentOrderMapper;

    @Autowired
    private SubOrderMapper subOrderMapper;

    @Autowired
    private RabbitMQConsumeUtils rabbitMQConsumeUtils;
    @Autowired
    private OrderMapper orderMapper;

    /**
     * 监听子订单 ES 同步队列（手动 ACK 模式）
     * concurrency = "3-5" 表示最少开启 3 个线程，最多 5 个线程并发处理
     */
    @RabbitListener(
            queues = SubOrderMQConfig.SUB_ORDER_ES_QUEUE,
            ackMode = "MANUAL",
            concurrency = "3-5"
    )
    public void onOrderSyncMessage(OrderSyncMessage syncMessage,
                                   Message message,
                                   Channel channel) throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String msgId = message.getMessageProperties().getCorrelationId();

        // 1. 检查重试次数（从 x-death header 获取）
        long retryCount = rabbitMQConsumeUtils.getRetryCount(message);

        log.info("[ES同步-消费者] 收到消息，消息ID:{}, 当前重试次数:{}",
                msgId, retryCount);

        // 2. 基础校验
        if (syncMessage == null || syncMessage.getSubOrderId() == null) {
            log.warn("[ES同步-消费者] 收到无效消息，直接丢弃并签收");
            channel.basicAck(deliveryTag, false);
            return;
        }

        Long subOrderId = syncMessage.getSubOrderId();
        Integer type = syncMessage.getType();

        try {
             //模拟异常
            //SubOrder subOrder = null;
            //subOrder.getId();

            // 3. 根据类型分流处理
            if (type == 1 || type == 2) {
                handleSaveOrUpdate(subOrderId);
            } else if (type == 3) {
                handleDelete(subOrderId);
            } else {
                log.warn("[ES同步-消费者] 未知的操作类型:{}, 消息ID:{}", type, msgId);
            }

            // 4. 业务成功，手动确认
            channel.basicAck(deliveryTag, false);
            log.info("[ES同步-消费者] 消息处理成功并已签收，消息ID:{}", msgId);

        } catch (Exception e) {
            log.error("[ES同步-消费者] 业务报错，错误:{}，消息ID:{}",
                    e.getMessage(), msgId);

            // 5. 判断是否达到最大重试次数
            if (retryCount >= SubOrderMQConfig.MAX_RETRY_COUNT) {
                rabbitMQConsumeUtils.handleFinalFailure(syncMessage, 2,
                SubOrderMQConfig.SUB_ORDER_EXCHANGE,
                SubOrderMQConfig.SUB_ORDER_SYNC_ROUTING_KEY,
                SubOrderMQConfig.MAX_RETRY_COUNT,
                e.getMessage(),
                msgId);
                channel.basicAck(deliveryTag, false);  // 确认消息，不再重试
                log.error("[ES同步-消费者] 达到最大重试次数，消息入库并签收，消息ID:{}",msgId);
            } else {
                // 未达到最大重试次数，拒绝消息并进入死信队列
                channel.basicReject(deliveryTag, false);
            }
        }
    }

    private void handleSaveOrUpdate(Long subOrderId) throws IOException {
        // 1. 反查数据库：获取最全的订单数据
        SubOrderVO fullOrder = subOrderService.getDetailForEs(subOrderId);

        if (fullOrder == null) {
            log.warn("[ES同步-消费者] 数据库中未找到子订单 {}, 可能已被删除", subOrderId);
            return;
        }

        // 2. 转换为 ES 文档对象
        SubOrderIndexDoc doc = convertToEsDoc(fullOrder);

        // 封装其他字段
        SubOrder subOrder = subOrderMapper.selectById(subOrderId);
        ParentOrder parentOrder = parentOrderMapper.selectById(subOrder.getParentOrderId());

        doc.setSubOrderId(subOrderId);
        doc.setParentOrderId(parentOrder.getId());
        doc.setParentOrderSn(parentOrder.getOrderSn());
        doc.setUserId(parentOrder.getUserId());

        // 3. 写入 ES（Index 操作天然幂等）
        IndexResponse response = elasticsearchClient.index(i -> i
                .index("sub_order_index")
                .id(subOrderId.toString())
                .document(doc)
        );

        log.info("[ES同步-消费者] 新增/修改成功，子订单号: {}", subOrder.getSubOrderSn());
    }

    /**
     * ES 同步：删除
     */
    private void handleDelete(Long subOrderId) throws IOException {
        SubOrder subOrder = subOrderMapper.selectById(subOrderId);

        elasticsearchClient.delete(d -> d
                .index("sub_order_index")
                .id(subOrderId.toString())
        );
        log.info("[ES同步-消费者] 删除成功，子订单号: {}", subOrder.getSubOrderSn());
    }

    /**
     * 数据转换：SubOrderVO → SubOrderIndexDoc
     */
    private SubOrderIndexDoc convertToEsDoc(SubOrderVO vo) {
        SubOrderIndexDoc doc = new SubOrderIndexDoc();

        // 金额字段转换（BigDecimal → Double）
        if (vo.getGoodsAmount() != null) {
            doc.setGoodsAmount(vo.getGoodsAmount().doubleValue());
        }
        if (vo.getPayAmount() != null) {
            doc.setPayAmount(vo.getPayAmount().doubleValue());
        }
        if (vo.getCouponAmount() != null) {
            doc.setCouponAmount(vo.getCouponAmount().doubleValue());
        }
        if (vo.getFreightAmount() != null) {
            doc.setFreightAmount(vo.getFreightAmount().doubleValue());
        }

        BeanUtils.copyProperties(vo, doc);

        // 处理嵌套的 items 列表
        if (vo.getItems() != null) {
            List<SubOrderIndexDoc.ItemInnerDTO> esItems = vo.getItems().stream().map(item -> {
                SubOrderIndexDoc.ItemInnerDTO esItem = new SubOrderIndexDoc.ItemInnerDTO();
                BeanUtils.copyProperties(item, esItem);
                if (item.getPrice() != null) {
                    esItem.setPrice(item.getPrice().doubleValue());
                }
                return esItem;
            }).collect(Collectors.toList());
            doc.setItems(esItems);
        }

        return doc;
    }
}