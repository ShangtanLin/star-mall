package com.github.shangtanlin.common.utils;

import com.alibaba.fastjson.JSON;
import com.github.shangtanlin.mapper.mq.MqMessageLogMapper;
import com.github.shangtanlin.model.entity.mq.MqMessageLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RabbitMQConsumeUtils {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConsumeUtils.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MqMessageLogMapper mqMessageLogMapper;

    // ==================== 生产者发送消息 ====================

    /**
     * 统一发送消息，异常时更新数据库状态
     * 消息发送成功/失败后会通过 ConfirmCallback 回调
     */
    public void sendMessage(String exchange, String routingKey, Object message, String msgId) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message,
                    m -> {
                        m.getMessageProperties().setCorrelationId(msgId);
                        return m;
                    },
                    new CorrelationData(msgId)
                    );
        } catch (AmqpException e) {
            mqMessageLogMapper.updateStatusToSendFail(msgId, e.getMessage());
            log.error("消息发送异常: ID = {}, 原因 = {}", msgId, e.getMessage());
        }
    }

    // ==================== 消费者重试处理 ====================

    /**
     * 从消息头获取重试次数（x-death header）
     * x-death 是 RabbitMQ 自动添加的，记录消息进入死信队列的历史
     */
    public long getRetryCount(Message message) {
        try {
            List<Map<String, ?>> xDeath = message.getMessageProperties().getXDeathHeader();
            if (xDeath != null && !xDeath.isEmpty()) {
                long maxCount = 0L;
                for (Map<String, ?> deathRecord : xDeath) {
                    // 🌟 关键修复：只统计因为 "rejected" (业务拒绝) 导致的死信
                    // 忽略 "expired" (TTL过期) 导致的死信，因为那是重试机制本身的流转，不算业务重试
                    String reason = deathRecord.get("reason") != null ? deathRecord.get("reason").toString() : "";
                    if ("rejected".equals(reason)) {
                        Object countObj = deathRecord.get("count");
                        long count = countObj != null ? Long.parseLong(countObj.toString()) : 0L;
                        maxCount = Math.max(maxCount, count);
                    }
                }
                return maxCount;
            }
        } catch (Exception e) {
            log.warn("解析重试次数失败: {}", e.getMessage());
        }
        return 0L;
    }

    /**
     * 最终失败后的处理（落库）
     * 使用新的 ID 入库，避免与生产者记录主键冲突
     *
     * @param msg 业务消息对象
     * @param businessType 业务类型（0-购物车，1-订单超时，2-ES同步）
     * @param exchange 交换机
     * @param routingKey 路由键
     * @param maxRetryCount 最大重试次数
     * @param errorMsg 错误信息
     * @param originMsgId 原始消息 ID（从 Message correlationId 获取）
     */
    public void handleFinalFailure(Object msg, int businessType, String exchange,
                                   String routingKey, int maxRetryCount, String errorMsg,
                                   String originMsgId) {
        try {
            String newId = UUID.randomUUID().toString();

            MqMessageLog messageLog = MqMessageLog.builder()
                    .id(newId)
                    .originId(originMsgId)
                    .sourceType(1)  // 1-消费者端
                    .businessType(businessType)
                    .exchange(exchange)
                    .routingKey(routingKey)
                    .payload(JSON.toJSONString(msg))
                    .status(4)  // 4-人工处理
                    .retryCount(maxRetryCount)
                    .cause(errorMsg)
                    .nextRetryTime(null)
                    .build();
            mqMessageLogMapper.insert(messageLog);
        } catch (Exception dbEx) {
            log.error("[消费者端消息入库] 入库失败，原因：{}，原始消息ID：{}", dbEx.getMessage(), originMsgId);
        }
    }
}