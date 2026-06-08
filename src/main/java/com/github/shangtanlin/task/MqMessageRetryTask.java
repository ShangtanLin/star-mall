package com.github.shangtanlin.task;

import com.alibaba.fastjson.JSON;
import com.github.shangtanlin.common.utils.RabbitMQConsumeUtils;
import com.github.shangtanlin.config.mq.OrderMQConfig;
import com.github.shangtanlin.mapper.mq.MqMessageLogMapper;
import com.github.shangtanlin.model.entity.mq.MqMessageLog;
import com.github.shangtanlin.mq.message.OrderCancelMessage;
import com.github.shangtanlin.mq.message.OrderSyncMessage;
import com.github.shangtanlin.mq.message.CartWriteBackMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发送失败消息补偿重发定时任务
 * 处理 source_type=0（生产者端）且 status IN (0, 2, 3) 的记录
 */
@Component
@Slf4j
public class MqMessageRetryTask {

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 业务类型常量
     */
    private static final int BUSINESS_TYPE_CART = 0;      // 购物车写回
    private static final int BUSINESS_TYPE_ORDER = 1;     // 订单超时关单
    private static final int BUSINESS_TYPE_ES_SYNC = 2;   // 子订单ES同步

    @Autowired
    private MqMessageLogMapper mqMessageLogMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitMQConsumeUtils rabbitMQSendUtils;

    /**
     * 每分钟扫描一次待重试的发送失败消息
     */
    @Scheduled(cron = "0 0/1 * * * ?")
    public void retrySendMessages() {
        List<MqMessageLog> failLogs = mqMessageLogMapper.selectPendingRetry();

        if (CollectionUtils.isEmpty(failLogs)) {
            return;
        }

        log.info("[消息补发] 定时任务开始补偿发送 MQ 消息，共 {} 条", failLogs.size());

        for (MqMessageLog failLog : failLogs) {
            try {
                // 1. 检查重试次数，达到上限则标记为人工处理
                if (failLog.getRetryCount() >= MAX_RETRY_COUNT) {
                    mqMessageLogMapper.markManualProcessed(failLog.getId());
                    log.error("[消息补发] 消息 ID: {} 重试次数已达上限({})，转为人工处理！", failLog.getId(), MAX_RETRY_COUNT);
                    continue;
                }

                // 2. 根据业务类型反序列化为对应的业务对象
                Object messageObj = deserializeByBusinessType(failLog);

                if (messageObj == null) {
                    log.error("[消息补发] 无法反序列化消息，ID: {}, businessType: {}", failLog.getId(), failLog.getBusinessType());
                    mqMessageLogMapper.markManualProcessed(failLog.getId());
                    continue;
                }

                // 3. 构造 CorrelationData
                CorrelationData cd = new CorrelationData(failLog.getId());

                // 4. 更新数据库：重试次数+1，状态改为发送中，设置下次重试时间
                int newRetryCount = failLog.getRetryCount() + 1;
                LocalDateTime nextRetryTime = LocalDateTime.now().plusMinutes(newRetryCount);
                failLog.setStatus(0);  // 重置为发送中
                failLog.setRetryCount(newRetryCount);
                failLog.setNextRetryTime(nextRetryTime);
                mqMessageLogMapper.updateRetryInfo(failLog);

                // 5. 重新投递
                rabbitMQSendUtils.sendMessage(
                        failLog.getExchange(),
                        failLog.getRoutingKey(),
                        messageObj,
                        failLog.getId()
                );


                String msgTypeName = switch (failLog.getBusinessType()) {
                    case 0 -> "购物车写回";
                    case 1 -> "订单超时关闭";
                    case 2 -> "ES同步";
                    default -> "未知";
                };

                log.info("[消息补发] 已触发消息补发，ID: {}, 业务类型: {}, 当前重试次数: {}",
                        failLog.getId(), msgTypeName, newRetryCount);

            } catch (Exception e) {
                log.error("[消息补发] 重试发送消息发生异常，ID: {}", failLog.getId(), e);
            }
        }
    }

    /**
     * 根据业务类型反序列化消息
     */
    private Object deserializeByBusinessType(MqMessageLog failLog) {
        Integer businessType = failLog.getBusinessType();
        String payload = failLog.getPayload();

        if (businessType == null || payload == null) {
            return null;
        }

        switch (businessType) {
            case BUSINESS_TYPE_CART:
                // 购物车写回消息
                return JSON.parseObject(payload, CartWriteBackMessage.class);
            case BUSINESS_TYPE_ORDER:
                // 订单超时关单消息
                return JSON.parseObject(payload, OrderCancelMessage.class);
            case BUSINESS_TYPE_ES_SYNC:
                // 子订单ES同步消息
                return JSON.parseObject(payload, OrderSyncMessage.class);
            default:
                log.warn("[消息补发] 未知的业务类型: {}, ID: {}", businessType, failLog.getId());
                return null;
        }
    }

    /**
     * 每小时清理所有成功的消息记录
     */
    @Scheduled(cron = "0 0 * * * ?")
    //@Scheduled(fixedRate = 10_000)
    public void cleanSuccessRecords() {
        int deleted = mqMessageLogMapper.deleteSuccessAll();
        log.info("[本地消息表清理] 共删除 {} 条消息", deleted);
    }
}