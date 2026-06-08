package com.github.shangtanlin.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.shangtanlin.common.constant.order.OrderRefundStatusConstant;
import com.github.shangtanlin.common.constant.order.OrderStatusConstant;
import com.github.shangtanlin.common.constant.order.RefundOrderStatusConstant;
import com.github.shangtanlin.common.exception.BusinessException;
import com.github.shangtanlin.mapper.ParentOrderMapper;
import com.github.shangtanlin.mapper.RefundOrderMapper;
import com.github.shangtanlin.mapper.SubOrderMapper;
import com.github.shangtanlin.model.entity.order.ParentOrder;
import com.github.shangtanlin.model.entity.order.RefundOrder;
import com.github.shangtanlin.model.entity.order.SubOrder;
import com.github.shangtanlin.model.entity.wechat.WechatRefundResult;
import com.github.shangtanlin.service.RefundOrderService;
import com.github.shangtanlin.service.WechatPayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class RefundOrderServiceImpl extends
        ServiceImpl<RefundOrderMapper,RefundOrder>
        implements RefundOrderService {

    /** 最大重试次数 */
    public static final int MAX_RETRY_COUNT = 3;

    @Autowired
    private WechatPayService wechatPayService;

    @Autowired
    private ParentOrderMapper parentOrderMapper;

    @Autowired
    private SubOrderMapper subOrderMapper;


    @Autowired
    private RefundOrderMapper refundOrderMapper;



    /**
     * 系统自动退款
     * @param orderSn 原订单号
     * @param refundAmount 退款金额
     * @param reason 退款原因 (如：订单超时关闭自动退款)
     */
    @Override
    public void createSystemRefund(String orderSn, BigDecimal refundAmount, String reason) {
        // 1. 幂等校验：防止微信重复回调导致重复生成退款单
        // 查库：该原订单是否已经存在退款单记录？
        RefundOrder existRefund = this.getOne(
                new LambdaQueryWrapper<RefundOrder>().eq(RefundOrder::getOrderSn, orderSn)
        );

        if (existRefund != null) {
            // 已经存在退款单，说明该方法已经被调用过了，直接返回，保证幂等
            log.info("[系统退款] 退款单已存在，无需重复创建，orderSn: {}", orderSn);
            return;
        }

        // 2. 生成独立的退款单号 (绝不依赖原订单号拼接，避免部分退款冲突)
        // 这里以 Hutool 的雪花算法为例，生成全局唯一ID，前面拼接 "RF" 前缀
        String refundSn = "RF" + IdUtil.getSnowflakeNextIdStr();

        // 3. 构建退款单实体
        RefundOrder refundOrder = new RefundOrder();
        refundOrder.setOrderSn(orderSn);
        refundOrder.setRefundSn(refundSn);
        refundOrder.setRefundAmount(refundAmount); // 数据库是 decimal(10,2)，这里直接存元
        refundOrder.setStatus(RefundOrderStatusConstant.PENDING); // 0-待处理 (等待定时任务来扫)
        // 🌟 核心补充：第一次入库，重试时间就是当前时间，表示“立即可以处理”
        LocalDateTime now = LocalDateTime.now();
        refundOrder.setNextRetryTime(now);
         refundOrder.setRetryCount(0);
        // refundOrder.setReason(reason); // 如果你表里加了 reason 字段可以存一下，没加就算了

        // 4. 入库保命
        this.save(refundOrder);
        log.info("[系统退款] 退款单创建成功，等待定时任务处理，orderSn: {}, refundSn: {}, amount: {}",
                orderSn, refundSn, refundAmount);
    }


    /**
     * 处理待退款的订单（定时任务扫描调用）
     */
    @Override
    public void processPendingRefunds() {
        // 1. 捞取所有待处理的退款单
        List<RefundOrder> pendingList = this.list(
                new LambdaQueryWrapper<RefundOrder>()
                        .eq(RefundOrder::getStatus, RefundOrderStatusConstant.PENDING) // 0-待处理
                        .le(RefundOrder::getNextRetryTime, LocalDateTime.now()) // 必须到达重试时间
                        .last("LIMIT 50")
        );

        if (pendingList.isEmpty()) {
            return;
        }

        log.info("[定时退款] 扫描到待处理退款单 {} 笔", pendingList.size());

        for (RefundOrder refundOrder : pendingList) {
            try {
                // 🌟 核心改变：先判断是否达到最大重试次数
                int retryCount = refundOrder.getRetryCount() == null ? 0 : refundOrder.getRetryCount();
                if (retryCount > MAX_RETRY_COUNT) {
                    // 达到最大次数，走“死单处理”逻辑
                    handleDeadRefundOrder(refundOrder);
                    continue; // 处理完死单，跳过后续的微信API调用
                }

                // 未达到最大次数，正常走退款流程
                processSingleRefund(refundOrder);

            } catch (Exception e) {
                log.error("[定时退款] 处理退款单异常，refundSn: {}", refundOrder.getRefundSn(), e);
            }
        }
    }

    /**
     * 处理单笔正常退款单 (只有未超限的单子才会进入此方法)
     */
    private void processSingleRefund(RefundOrder refundOrder) {
        // 1. CAS 无锁更新状态 (0 -> 1)
        // 保证在任何时刻，针对同一笔退款单，绝对不允许有多个线程同时往下执行调用微信 API 的逻辑。
        boolean lockSuccess = this.update(
                new LambdaUpdateWrapper<RefundOrder>()
                        .eq(RefundOrder::getId, refundOrder.getId())
                        .eq(RefundOrder::getStatus, RefundOrderStatusConstant.PENDING)
                        .set(RefundOrder::getStatus, RefundOrderStatusConstant.PROCESSING)
        );

        if (!lockSuccess) {
            log.info("[定时退款] 抢占退款单失败，可能已被处理，refundSn: {}", refundOrder.getRefundSn());
            return;
        }

        // 2. 调用微信退款 API
        WechatRefundResult result = wechatPayService.refund(
                refundOrder.getOrderSn(),
                refundOrder.getRefundSn(),
                refundOrder.getRefundAmount()
        );

        // 3. 根据微信返回结果更新最终状态
        if (result != null && result.isSuccess()) {
            log.info("[定时退款] 微信退款受理成功，等待异步回调，refundSn: {}", refundOrder.getRefundSn());
        } else {
            String failReason = result == null ? "微信返回空" : result.getFailReason();
            log.error("[定时退款] 微信退款受理失败！refundSn: {}, 原因: {}", refundOrder.getRefundSn(), failReason);
            handleRetry(refundOrder, failReason); // 重试失败 (1 -> 0)
        }
    }


    /**
     * 处理可重试的退款失败（状态 1 -> 0，放回待处理池）
     */
    private void handleRetry(RefundOrder refundOrder, String failReason) {
        int retryCount = refundOrder.getRetryCount();
        int nextRetryCount = retryCount + 1;

        // 2. 计算退避时间
        // 简单的线性/阶梯退避策略：第1次失败等 5 分钟，第2次失败等 30 分钟
        long delayMinutes;
        if (nextRetryCount == 1) {
            delayMinutes = 1;
        } else if ((nextRetryCount == 2)) {
            delayMinutes = 5;
        } else {
            delayMinutes = 10;
        }
        LocalDateTime nextRetryTime = LocalDateTime.now().plusMinutes(delayMinutes);

        // 3. 更新数据库：状态回退，记录重试信息
        boolean updateSuccess = this.update(
                new LambdaUpdateWrapper<RefundOrder>()
                        .eq(RefundOrder::getId, refundOrder.getId())
                        // 🌟 极其关键：由于前面 CAS 已经把状态改成了 1(处理中)，
                        // 这里更新时期望状态必须是 1，否则可能出现状态覆盖混乱
                        .eq(RefundOrder::getStatus, RefundOrderStatusConstant.PROCESSING)
                        .set(RefundOrder::getStatus, RefundOrderStatusConstant.PENDING)      // 1 -> 0 (放回待处理)
                        .set(RefundOrder::getRetryCount, nextRetryCount)                   // 累加次数
                        .set(RefundOrder::getNextRetryTime, nextRetryTime)                 // 设置下次允许执行时间
                        .set(RefundOrder::getFailReason, failReason)                       // 记录本次失败原因
        );

        if (updateSuccess) {
            log.info("[定时退款] 退款单已加入重试队列，下次执行时间: {}, refundSn: {}", nextRetryTime, refundOrder.getRefundSn());
        } else {
            // 更新失败，通常是因为状态已经被微信异步回调给改成了 4(成功) 或其他并发修改导致
            log.warn("[定时退款] 重试状态回退失败，可能已被回调处理，refundSn: {}", refundOrder.getRefundSn());
        }
    }

    /**
     * 处理重试超限的"死单"
     */
    private void handleDeadRefundOrder(RefundOrder refundOrder) {
        log.error("[定时退款] 🚨 退款单重试次数已达上限，停止自动重试！refundSn: {}, 当前次数: {}",
                refundOrder.getRefundSn(), refundOrder.getRetryCount());

        // 🌟 关键 1：改变状态！从 0(待处理) 推进到 5(退款失败)
        // 这样下一次定时任务就绝对不会再捞到它了！
        boolean updateSuccess = this.update(
                new LambdaUpdateWrapper<RefundOrder>()
                        .eq(RefundOrder::getId, refundOrder.getId())
                        .eq(RefundOrder::getStatus, RefundOrderStatusConstant.PENDING) // 乐观锁防御
                        .set(RefundOrder::getStatus, RefundOrderStatusConstant.FAIL) // 5-失败
                        .set(RefundOrder::getFailReason, "系统自动重试超限，需人工介入")
        );

        // 🌟 关键 2：报警！
        if (updateSuccess) {
            // 只有状态真正更新成功了，才发报警，防止并发重复发报警
            //String alarmMsg = String.format(
            //        "🚨退款死单告警！\n退款单号: %s\n主订单号: %s\n失败原因: %s\n请立即人工核查！",
            //        refundOrder.getRefundSn(),
            //        refundOrder.getOrderSn(),
            //        refundOrder.getFailReason()
            //);
            // 发送钉钉/企微报警
            // DingTalkUtil.send(alarmMsg);
        }
    }




    /**
     * 接收微信退款回调
     * @param refundSn 退款单号
     * @param success 是否退款成功
     * @param wechatRefundId 微信退款单号（微信系统生成的，可用于对账）
     */
    @Override
    @Transactional(rollbackFor = Exception.class) //
    public void handleRefundNotify(String refundSn, boolean success, String wechatRefundId) {
        // 1. 查询当前退款单状态
        RefundOrder refundOrder = this.getOne(
                new LambdaQueryWrapper<RefundOrder>().eq(RefundOrder::getRefundSn, refundSn)
        );

        // 🌟 修正1：必须抛出异常！
        // 如果退款单都没了，说明这笔退款根本不是我们系统发起的（可能是脏数据或攻击），
        // 必须抛异常阻断，让外层给微信回 FAIL，或者触发报警人工介入
        if (refundOrder == null) {
            log.error("[退款回调] 严重错误：未找到退款单，refundSn: {}", refundSn);
            throw new BusinessException("退款单不存在");
        }

        // 🌟 修正2：终极幂等防御，判断最终态 (4:成功, 5:失败)
        if (Objects.equals(refundOrder.getStatus(), RefundOrderStatusConstant.SUCCESS) ||
                Objects.equals(refundOrder.getStatus(), RefundOrderStatusConstant.FAIL)) {
            log.info("[退款回调] 退款单已是最终态，忽略重复回调，refundSn: {}, currentStatus: {}",
                    refundSn, refundOrder.getStatus());
            return; // 直接放行
        }

        // 🌟 修正3：防御性校验。如果当前状态不是 1(处理中)，说明状态流转异常！
        // (比如状态还是0待处理，微信却发来了回调，这不符合正常流程)
        if (!Objects.equals(refundOrder.getStatus(), RefundOrderStatusConstant.PROCESSING)) {
            log.error("[退款回调] 退款单状态异常！期望状态为1(处理中)，实际为: {}，refundSn: {}",
                    refundOrder.getStatus(), refundSn);
            throw new BusinessException("退款单状态异常");
        }

        // 4. 根据微信的判定结果，走不同分支
        if (success) {
            handleRefundSuccess(refundOrder, wechatRefundId);
        } else {
            handleRefundFail(refundOrder);
        }
    }



    /**
     * 处理退款成功逻辑
     */
    private void handleRefundSuccess(RefundOrder refundOrder, String wechatRefundId) {
        // 1. 更新退款单自身状态为成功，并记录微信单号
        refundOrder.setStatus(RefundOrderStatusConstant.SUCCESS);
        refundOrder.setWechatRefundId(wechatRefundId);
        refundOrder.setSuccessTime(LocalDateTime.now());
        refundOrderMapper.updateById(refundOrder);
        // 2.1 若退款订单为主订单
        String orderSn = refundOrder.getOrderSn();
        ParentOrder parentOrder = parentOrderMapper.selectBySn(orderSn);
        if (parentOrder != null) {
            // 订单为正常售后退款
            if (!Objects.equals(parentOrder.getStatus(), OrderStatusConstant.CLOSED)) {
                // 更新主订单的主状态及退款状态
                parentOrderMapper.setStatusAndRefundStatus(
                        orderSn,
                        OrderStatusConstant.CLOSED,
                        OrderRefundStatusConstant.COMPLETED);
                // 更新子订单的主状态及退款状态
                subOrderMapper.setStatusAndRefundStatus(
                        orderSn,
                        OrderStatusConstant.CLOSED,
                        OrderRefundStatusConstant.COMPLETED);
            }
            // 订单为超时并发冲突退款
            else {
                // 更新主订单的退款状态
                parentOrderMapper.setRefundStatus(
                        orderSn,
                        OrderRefundStatusConstant.COMPLETED,
                        OrderRefundStatusConstant.REFUNDING);
                // 更新子订单的退款状态
                subOrderMapper.setRefundStatusByParentSn(
                        orderSn,
                        OrderRefundStatusConstant.COMPLETED,
                        OrderRefundStatusConstant.REFUNDING);
            }
            log.info("[退款回调] 退款处理成功！退款单号:{}",refundOrder.getRefundSn());
            return;
        }
        // 2.2 若退款订单为子订单
        SubOrder subOrder = subOrderMapper.selectByOrderSn(orderSn);
        if (subOrder != null) {
            // 更新子订单的主状态(不存在超时与支付并发的情况)
            subOrderMapper.setStatusBySubOrderSn(orderSn, OrderStatusConstant.CLOSED);

            // 更新子订单的退款状态
            subOrderMapper.setRefundStatus(
                    orderSn,
                    OrderRefundStatusConstant.COMPLETED,
                    OrderRefundStatusConstant.REFUNDING);
            log.info("[退款回调] 退款处理成功！退款单号:{}",refundOrder.getRefundSn());
            return;
        }
        log.info("[退款回调] 退款处理失败！原订单不存在，退款单号:{}",refundOrder.getRefundSn());
        throw new BusinessException("退款订单异常！原订单不存在");
    }


    /**
     * 处理退款失败逻辑（微信退款异常）
     */
    private void handleRefundFail(RefundOrder refundOrder) {
        log.warn("[退款回调] 微信告知退款未到账！退款单号: {}", refundOrder.getRefundSn());

        // 将状态从 1(处理中) 推进到 6(退款异常/人工介入)
        // 不用 5(退款失败) 是因为 5 通常代表接口层面被拒，而这个是钱已经扣了但没退给用户
        boolean updateSuccess = this.update(
                new LambdaUpdateWrapper<RefundOrder>()
                        .eq(RefundOrder::getId, refundOrder.getId())
                        .eq(RefundOrder::getStatus, RefundOrderStatusConstant.PROCESSING) // 乐观锁：期望 1
                        .set(RefundOrder::getStatus, RefundOrderStatusConstant.FAIL) // 6-失败
                        .set(RefundOrder::getFailReason, "微信回调通知退款未到账，需人工介入")
        );

        if (updateSuccess) {
            // 发送紧急报警！钱没退给用户，容易引发客诉
            // DingTalkUtil.send("🚨退款异常：用户未收到退款！退款单号: " + refundOrder.getRefundSn());
        }
    }





    /**
     * 根据退款单号查询退款单
     * @param refundSn
     * @return
     */
    @Override
    public RefundOrder getByRefundSn(String refundSn) {
        if (refundSn == null || refundSn.trim().isEmpty()) {
            throw new IllegalArgumentException("退款单号不能为空");
        }

        return this.getOne(
                new LambdaQueryWrapper<RefundOrder>()
                        .eq(RefundOrder::getRefundSn, refundSn)
                        // 🌟 防御性编程：按时间倒排，虽然退款单号是唯一的，理论上只有一条，
                        // 但养成习惯，防备未来可能出现的脏数据导致 MyBatis-Plus 报错
                        .last("LIMIT 1")
        );
    }

    /**
     * 根据原订单号查询退款单
     * @param orderSn
     * @return
     */
    @Override
    public RefundOrder getByOrderSn(String orderSn) {
        if (orderSn == null || orderSn.trim().isEmpty()) {
            throw new IllegalArgumentException("原订单号不能为空");
        }

        // 🚨 核心业务逻辑防范：一个订单可能发生多次部分退款！
        // 但在我们的业务场景中（超时关单全额退款），一个订单只会有一次退款。
        // 为了确保绝对安全，我们按 ID 倒排取最新的一条。
        return this.getOne(
                new LambdaQueryWrapper<RefundOrder>()
                        .eq(RefundOrder::getOrderSn, orderSn)
                        .orderByDesc(RefundOrder::getId) // 倒排，取最新的退款记录
                        .last("LIMIT 1")
        );
    }
}
