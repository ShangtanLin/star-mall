package com.github.shangtanlin.mq.coupon;

import com.github.shangtanlin.config.mq.CouponMQConfig;
import com.github.shangtanlin.model.dto.coupon.CouponRecordDTO;
import com.github.shangtanlin.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CouponReceiveConsumer {

    @Autowired
    private CouponService couponService;

    // 监听领券队列
    @RabbitListener(queues = CouponMQConfig.COUPON_RECEIVE_QUEUE)
    public void onMessage(CouponRecordDTO dto) {
        log.info("收到领券消息：{}", dto);

        try {
            // 核心逻辑：完成数据库库存扣减和记录插入
            // 这里一定要在数据库层面通过唯一索引或手动校验确保不重复
            couponService.doReceiveRecord(dto);
        } catch (Exception e) {
            // 如果报错，需要根据异常类型决定是否让 MQ 重试
            log.error("领券异步入库失败", e);
        }
    }
}