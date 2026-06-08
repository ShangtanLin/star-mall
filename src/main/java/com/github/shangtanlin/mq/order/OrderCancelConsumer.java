package com.github.shangtanlin.mq.order;

import com.github.shangtanlin.common.utils.RabbitMQConsumeUtils;
import com.github.shangtanlin.config.mq.OrderMQConfig;
import com.github.shangtanlin.model.entity.order.OrderItem;
import com.github.shangtanlin.mq.message.OrderCancelMessage;
import com.github.shangtanlin.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 超时订单消费者（手动 ack 模式 + 死信队列重试）
 * 监听 order.timeout.queue（消息从延迟队列过期后进入此队列）
 */
@Component
@Slf4j
public class OrderCancelConsumer {

    @Autowired
    private OrderService orderService;

    @Autowired
    private RabbitMQConsumeUtils rabbitMQConsumeUtils;

    /**
     * 处理超时关单消息（手动 ack 模式）
     * 监听业务队列（order.timeout.queue），不是延迟队列
     */
    @RabbitListener(queues = OrderMQConfig.TIMEOUT_QUEUE, ackMode = "MANUAL")
    public void onOrderCancelMessage(OrderCancelMessage cancelMessage,
                                      Message message,
                                      Channel channel) throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        // 1. 检查重试次数（从 x-death header 获取）
        long retryCount = rabbitMQConsumeUtils.getRetryCount(message);
        String msgId = message.getMessageProperties().getCorrelationId();

        log.info("[超时关单-消费者] 收到消息，消息ID：{}, 当前重试次数: {}",
                msgId, retryCount);

        // 2. 基础判空校验（防御式编程）
        if (cancelMessage == null || cancelMessage.getOrderSn() == null) {
            log.warn("[超时关单-消费者] 收到无效消息，直接丢弃并签收: {}", cancelMessage);
            channel.basicAck(deliveryTag, false);
            return;
        }

        String orderSn = cancelMessage.getOrderSn();

        try {
            // 模拟异常
            //OrderItem orderItem = null;
            //orderItem.getId();

            // 3. 调用 Service 执行核心关单逻辑
            // 该方法内部应包含：状态校验、修改状态、回滚库存、回滚优惠券
            orderService.cancelParentOrder(orderSn);

            // 4. 业务成功，手动确认
            channel.basicAck(deliveryTag, false);
            log.info("[超时关单-消费者] 消息处理成功并已签收，消息ID:{}", msgId);

        } catch (Exception e) {
            log.error("[超时关单-消费者] 业务报错，错误: {}，消息ID:{}",
                    e, msgId);

            // 6. 判断是否达到最大重试次数
            // （这里由于消息是从死信队列进入到业务队列的，即x-death中初始的count为1，所以要改成MAX_RETRY_COUNT + 1）
            if (retryCount >= OrderMQConfig.MAX_RETRY_COUNT) {
                rabbitMQConsumeUtils.handleFinalFailure(cancelMessage, 1,
                OrderMQConfig.DLX_EXCHANGE,
                OrderMQConfig.TIMEOUT_ROUTING_KEY,
                OrderMQConfig.MAX_RETRY_COUNT,
                e.getMessage(),
                msgId);
                channel.basicAck(deliveryTag, false);  // 确认消息，不再重试
                log.error("[超时关单-消费者] 达到最大重试次数，消息入库并签收，消息ID:{}", msgId);
            } else {
                // 未达到最大重试次数，拒绝消息并进入死信队列
                // requeue=false，消息进入死信队列，TTL 后自动回流到业务队列
                channel.basicReject(deliveryTag, false);
            }
        }
    }
}