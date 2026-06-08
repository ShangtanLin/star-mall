package com.github.shangtanlin.config.mq;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CartMQConfig {
    // --- 业务常量 ---
    public static final String CART_EXCHANGE = "cart.write.back.exchange";
    public static final String CART_QUEUE = "cart.write.back.queue";
    public static final String CART_ROUTING_KEY = "cart.write.back";

    // --- 死信常量 ---
    public static final String CART_DLX_EXCHANGE = "cart.dlx.exchange";
    public static final String CART_DLX_QUEUE = "cart.dlx.queue";
    public static final String CART_DLX_ROUTING_KEY = "cart.dlx.routing.key";


    /** 消费者重试间隔：30秒 */
    public static final int RETRY_INTERVAL_MS = 30000;
    //public static final int RETRY_INTERVAL_MS = 3000;
    /** 最大重试次数：消息从死信队列回流的最大次数 */
    public static final int MAX_RETRY_COUNT = 3;

    /* ========== 业务队列配置 ========== */

    @Bean
    public DirectExchange cartExchange() {
        return new DirectExchange(CART_EXCHANGE, true, false);
    }

    @Bean
    public Queue cartQueue() {
        return QueueBuilder.durable(CART_QUEUE)
                .deadLetterExchange(CART_DLX_EXCHANGE) // 失败后去死信交换机
                .deadLetterRoutingKey(CART_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding cartBinding() {
        return BindingBuilder.bind(cartQueue()).to(cartExchange()).with(CART_ROUTING_KEY);
    }

    /* ========== 死信队列配置（改造重点） ========== */

    @Bean
    public DirectExchange cartDlxExchange() {
        return new DirectExchange(CART_DLX_EXCHANGE, true, false);
    }

    /**
     * 改造后的死信队列：
     * 它不再由 Consumer 消费，而是作为一个“计时器”
     */
    @Bean
    public Queue cartDlxQueue() {
        return QueueBuilder.durable(CART_DLX_QUEUE)
                // 1. 设置消息在死信队列中只活30秒
                .ttl(RETRY_INTERVAL_MS)
                // 2. 5秒过期后，消息再次变成死信，把它发回【业务交换机】
                .deadLetterExchange(CART_EXCHANGE)
                // 3. 发回业务交换机时，使用的 RoutingKey
                .deadLetterRoutingKey(CART_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding cartDlxBinding() {
        return BindingBuilder
                .bind(cartDlxQueue())
                .to(cartDlxExchange())
                .with(CART_DLX_ROUTING_KEY);
    }
}
