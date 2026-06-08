package com.github.shangtanlin.mq.message;

import com.github.shangtanlin.model.redis.CartRedisJson;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartWriteBackMessage {

    private Long userId;

    private Long skuId;

    /**
     * 操作类型
     * 1 = UPDATE (添加购物车/修改数量/选中状态)
     * 2 = DELETE (删除购物车)
     * 3 = CLEAR  (清空购物车)
     */
    private Integer type;

    /**
     * 更改后的最新数据
     */
    private CartRedisJson cartRedisJson;

    /**
     * 消息发送的时间
     */
    private LocalDateTime createTime;
}