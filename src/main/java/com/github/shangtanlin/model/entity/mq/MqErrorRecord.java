package com.github.shangtanlin.model.entity.mq;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * MQ 死信/异常消息记录表
 */
@Data
public class MqErrorRecord {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 原始消息 ID（对应 CorrelationData 中的 id）
     */
    private String msgId;

    /**
     * 来源交换机
     */
    private String exchange;

    /**
     * 来源路由键
     */
    private String routingKey;

    /**
     * 消息体内容（通常转为 JSON 字符串存储）
     */
    private String payload;

    /**
     * 异常原因（简要错误信息或堆栈）
     */
    private String errorCause;

    /**
     * 处理状态：0-待处理，1-已人工修复，2-已重新投递
     */
    private Integer status;

    /**
     * 进入死信/记录时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间（用于记录修复时间）
     */
    private LocalDateTime updateTime;
}