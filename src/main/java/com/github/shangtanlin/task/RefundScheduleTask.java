package com.github.shangtanlin.task;

import com.github.shangtanlin.mapper.RefundOrderMapper;
import com.github.shangtanlin.service.RefundOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RefundScheduleTask {

    @Autowired
    private RefundOrderService refundOrderService;

    @Autowired
    private RefundOrderMapper refundOrderMapper;

    /**
     * 处理待退款订单定时任务
     * 1. fixedRate = 60000: 每 60 秒执行一次（可根据业务量调整，1-5分钟均可）
     * 2. initialDelay = 10000: 项目启动后延迟 10 秒再执行，给应用预热时间
     * 3. 🌟 强烈建议：如果你们是集群部署，必须加此注释，防同一时刻多台机器同时执行！
     */
    @Scheduled(initialDelay = 10000, fixedRate = 10000)
    public void processRefund() {
        try {
            refundOrderService.processPendingRefunds();
        } catch (Exception e) {
            // 兜底：虽然 Service 层已经 try-catch 了单条异常，但以防 Service 层外层出现如 DB 连接断开等致命错误
            log.error("[定时任务] processRefund 发生系统级异常", e);
            e.printStackTrace();
        }
    }




    /**
     * 每小时清理所有成功的退款单
     */
    @Scheduled(cron = "0 0 * * * ?")
    //@Scheduled(fixedRate = 10_000)
    public void cleanSuccessRefundOrder() {
        int deleted = refundOrderMapper.deleteSuccessAll();
        log.info("[退款单清理] 共删除 {} 条退款单", deleted);
    }
}
