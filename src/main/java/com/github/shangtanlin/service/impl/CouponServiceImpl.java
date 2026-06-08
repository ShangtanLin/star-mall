package com.github.shangtanlin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.shangtanlin.common.constant.RedisConstant;
import com.github.shangtanlin.common.constant.coupon.CouponConstants;
import com.github.shangtanlin.common.exception.BusinessException;
import com.github.shangtanlin.common.utils.DateUtils;
import com.github.shangtanlin.common.utils.RedisLuaScript;
import com.github.shangtanlin.common.utils.UserHolder;
import com.github.shangtanlin.config.mq.CouponMQConfig;
import com.github.shangtanlin.mapper.coupon.CouponTemplateMapper;
import com.github.shangtanlin.mapper.coupon.CouponUserRecordMapper;
import com.github.shangtanlin.model.dto.coupon.CouponRecordDTO;
import com.github.shangtanlin.model.entity.coupon.CouponTemplate;
import com.github.shangtanlin.model.entity.coupon.CouponUserRecord;
import com.github.shangtanlin.model.vo.coupon.CouponTemplateVO;
import com.github.shangtanlin.model.vo.coupon.UserCouponVO;
import com.github.shangtanlin.result.Result;
import com.github.shangtanlin.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CouponServiceImpl implements CouponService {
    @Autowired
    private CouponTemplateMapper couponTemplateMapper;

    @Autowired
    private CouponUserRecordMapper couponUserRecordMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 查询领券中心
     * @return
     */
    @Override
    public List<CouponTemplateVO> getPublishingCoupon() {
        LocalDateTime now = LocalDateTime.now();
        Long userId = UserHolder.getUser().getId();

        // 1. 构建查询条件,查询符合条件的优惠券
        LambdaQueryWrapper<CouponTemplate> query = new LambdaQueryWrapper<>();
        query.eq(CouponTemplate::getStatus, CouponConstants.TEMPLATE_STATUS_PROCESSING) // 状态：进行中
                .le(CouponTemplate::getPublishStartTime, now) // 已开始发放
                .gt(CouponTemplate::getPublishEndTime, now); // 未停止发放

        List<CouponTemplate> templates = couponTemplateMapper.selectList(query);

        // 2. 转换为 VO（只给前端需要的数据）
        return templates.stream().map(t -> {
            CouponTemplateVO vo = new CouponTemplateVO();
            BeanUtils.copyProperties(t, vo);
            //设置返回给前端的展示状态
            boolean userReceived = hasAvailableCoupon(userId, t.getId());

            //1.如果用户持有该券，则设置状态为“去使用”
            if (userReceived) {
                vo.setReceiveStatus(CouponConstants.VO_STATUS_HAS_RECEIVED);
            }
            //2.如果用户不持有该优惠券（没领过、已用掉、已过期),则判断该优惠券是否还有库存
            //有库存，返回“立即抢”
            else if (t.getReceivedCount() < t.getPublishCount()) {
                vo.setReceiveStatus(CouponConstants.VO_STATUS_CAN_TAKE);
            }
            //3.没库存，返回“已抢光”
            else {
                vo.setReceiveStatus(CouponConstants.VO_STATUS_EMPTY);
            }

            //封装有效期文案
            fillValidityDesc(vo, t);


            //没库存，返回“已抢光”

            // 逻辑：将金额数值转换为用户看得懂的文字
            if (t.getCouponType() == CouponConstants.TYPE_NO_THRESHOLD) {
                // 无门槛券逻辑
                vo.setThresholdDesc("无门槛立减");
            } else {
                // 满减券逻辑：展示为 "满100减20"
                vo.setThresholdDesc(String.format("满%s减%s",
                        t.getThresholdAmount().stripTrailingZeros().toPlainString(),
                        t.getReduceAmount().stripTrailingZeros().toPlainString()));
            }
            return vo;
        }).collect(Collectors.toList());

    }

    /**
     * 领取优惠券
     * @param templateId
     * @return
     */
    @Override
    public Result<?> receiveCoupon(Long templateId) {
        Long userId = UserHolder.getUser().getId();

        // 1. 准备 Key
        String stockKey = RedisConstant.COUPON_STOCK_KEY + templateId;
        String userKey = RedisConstant.COUPON_USERS_KEY + templateId;

        // 2. 执行Lua脚本 (直接调用静态常量)
        Long result = redisTemplate.execute(
                RedisLuaScript.SECKILL_SCRIPT,
                Arrays.asList(stockKey, userKey),
                userId.toString(), "1"
        );

        // 3. 处理结果 (0-成功, 1-库存不足, 2-重复领取, -1-未初始化)
        if (result == CouponConstants.SUCCESS) {
            // TODO: 发送 MQ 消息
            // 2. 构造 DTO
            String traceId = UUID.randomUUID().toString().replace("-", "");
            CouponRecordDTO message = new CouponRecordDTO(userId, templateId, traceId);

            // 3. 发送异步消息 (这里以 RocketMQ/RabbitMQ 为例)
            // 路由键建议：coupon.receive.queue
            rabbitTemplate.convertAndSend(CouponMQConfig.COUPON_EXCHANGE,
                    CouponMQConfig.COUPON_RECEIVE_KEY, message);

            log.info("用户 {} 抢券成功，消息已投递到 MQ, 模板ID: {}", userId, templateId);
        } else {
            throw new BusinessException(mapResultToMsg(result));
        }



        return Result.ok();
    }

    /**
     * 数据库异步扣减和领券记录插入
     * @param dto
     */
    @Override
    public void doReceiveRecord(CouponRecordDTO dto) {
        // 1. 数据库层面防重：检查该用户是否真的已经领过这张券
        // 虽然 Redis 挡住了大部分，但 DB 必须作为最后一道防线
        if (hasAvailableCoupon(dto.getUserId(), dto.getTemplateId())) {
            log.warn("用户 {} 已经领过券 {}，MQ消息忽略", dto.getUserId(), dto.getTemplateId());
            return;
        }

        // 2. 扣减数据库库存 (因为之前 Redis 已经扣了，这里直接减即可)
        // 注意：这里仍然建议使用带条件的 SQL 保证万无一失
        int updated = couponTemplateMapper.updateReceivedCount(dto.getTemplateId());

        if (updated > 0) {
            // 3. 插入用户领券记录
            CouponUserRecord record = new CouponUserRecord();
            record.setUserId(dto.getUserId());
            record.setTemplateId(dto.getTemplateId());

            CouponTemplate template = couponTemplateMapper.selectById(record.getTemplateId());


            // 3.1 设置状态：新入库的券默认都是“未使用”
            record.setStatus(CouponConstants.USER_RECORD_STATUS_UNUSED); // 0

            // 3.2 设置领取时间：以当前系统时间为准
            LocalDateTime now = LocalDateTime.now();
            record.setGetTime(now);

            //3.3 设置开始时间和结束时间
            if (template.getValidType() == CouponConstants.VALID_TYPE_FIXED) {
                record.setStartTime(template.getUseStartTime());
                record.setEndTime(template.getUseEndTime());
            } else {
                record.setStartTime(now);
                record.setEndTime(now.plusDays(template.getValidDays()));
            }

            // 5. 其他字段说明
            // order_sn: 此时还没下单，设为 null
            // use_time: 此时还没使用，设为 null
            record.setOrderSn(null);
            record.setUseTime(null);

            couponUserRecordMapper.insert(record);
            // 打印这条日志
            log.info("【领券异步入库成功】用户ID: {}, 模板ID: {}, 记录ID: {}",
                    dto.getUserId(), dto.getTemplateId(), record.getId());

        }
    }

    /**
     * 查询我的优惠券列表
     * @param status
     * @return
     */
    @Override
    public List<UserCouponVO> getMyCoupons(Integer status) {
        Long userId = UserHolder.getUser().getId();

        // 1. 查询该用户指定状态的领取记录
        // 如果 status 为 null，则查询全部
        List<CouponUserRecord> records = couponUserRecordMapper.selectList(
                new LambdaQueryWrapper<CouponUserRecord>()
                        .eq(CouponUserRecord::getUserId, userId)
                        .eq(status != null, CouponUserRecord::getStatus, status)
                        .orderByDesc(CouponUserRecord::getGetTime)
        );

        if (CollectionUtils.isEmpty(records)) {
            return new ArrayList<>();
        }

        // 2. 批量获取模板信息，减少数据库查询次数
        List<Long> templateIds = records.stream()
                .map(CouponUserRecord::getTemplateId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, CouponTemplate> templateMap = couponTemplateMapper.selectBatchIds(templateIds)
                .stream()
                .collect(Collectors.toMap(CouponTemplate::getId, t -> t));

        // 3. 封装 VO
        return records.stream().map(record -> {
            CouponTemplate template = templateMap.get(record.getTemplateId());
            UserCouponVO vo = new UserCouponVO();
            BeanUtils.copyProperties(template, vo); // 拷贝模板基础属性

            //封装优惠券门槛文案
            // 逻辑：将金额数值转换为用户看得懂的文字
            if (template.getCouponType() == CouponConstants.TYPE_NO_THRESHOLD) {
                // 无门槛券逻辑
                vo.setThresholdDesc("无门槛立减");
            } else {
                // 满减券逻辑：展示为 "满100减20"
                vo.setThresholdDesc(String.format("满%s减%s",
                        template.getThresholdAmount().stripTrailingZeros().toPlainString(),
                        template.getReduceAmount().stripTrailingZeros().toPlainString()));
            }

        // 核心封装逻辑：处理 expireDesc
        if (record.getEndTime() != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime endTime = record.getEndTime(); // 假设数据库存的是 LocalDateTime

            if (now.isAfter(endTime)) {
                vo.setExpireDesc("已过期");
            } else {
                Duration duration = Duration.between(now, endTime);
                long days = duration.toDays();
                long hours = duration.toHours();

                if (days > 0) {
                    vo.setExpireDesc(days + "天后过期");
                } else if (hours > 0) {
                    vo.setExpireDesc("仅剩 " + hours + " 小时");
                } else {
                    long minutes = duration.toMinutes();
                    vo.setExpireDesc("仅剩 " + (minutes > 0 ? minutes : 1) + " 分钟");
                }
            }
        }

            vo.setId(record.getId());
            vo.setTemplateId(record.getTemplateId());
            vo.setStatus(record.getStatus());
            // 使用 record 里的有效期，因为这才是属于该用户的真实有效期
            vo.setStartTime(DateUtils.format(record.getStartTime()));
            vo.setEndTime(DateUtils.format(record.getEndTime()));
            return vo;
        }).collect(Collectors.toList());

    }

    /**
     * 结算时查询当前订单可用优惠券
     * @param orderAmount
     * @return
     */
    @Override
    public List<UserCouponVO> getAvailableCoupons(BigDecimal orderAmount) {
        Long userId = UserHolder.getUser().getId();

        // 1. 基础查询：该用户所有 未使用(0) 的优惠券
        List<CouponUserRecord> myRecords = couponUserRecordMapper.selectList(
                new LambdaQueryWrapper<CouponUserRecord>()
                        .eq(CouponUserRecord::getUserId, userId)
                        .in(CouponUserRecord::getStatus,
                                CouponConstants.USER_RECORD_STATUS_UNUSED)
        );

        if (CollectionUtils.isEmpty(myRecords)) return new ArrayList<>();

        // 2. 获取模板信息 (批量查询提高性能)
        Map<Long, CouponTemplate> templateMap = getTemplateMap(myRecords);

        LocalDateTime now = LocalDateTime.now();

        return myRecords.stream().map(record -> {
                    CouponTemplate template = templateMap.get(record.getTemplateId());
                    UserCouponVO vo = convertToVO(record, template);

                    // --- 开始判定逻辑 ---
                    vo.setAvailable(true);
                    vo.setReason("");

                    // 判定 1：状态是否被锁定
                    if (record.getStatus() == CouponConstants.USER_RECORD_STATUS_LOCKED) {
                        vo.setAvailable(false);
                        vo.setReason("该券已在其他订单中使用中");
                        return vo;
                    }

                    // 判定 2：时间是否有效
                    if (now.isBefore(record.getStartTime())) {
                        vo.setAvailable(false);
                        vo.setReason("活动尚未开始");
                        return vo;
                    }
                    if (now.isAfter(record.getEndTime())) {
                        vo.setAvailable(false);
                        vo.setReason("优惠券已过期");
                        return vo;
                    }

                    // 判定 3：金额门槛校验 (最核心的凑单逻辑)
                    BigDecimal threshold = template.getThresholdAmount();
                    if (orderAmount.compareTo(threshold) < 0) {
                        vo.setAvailable(false);
                        // 计算差额：还差多少钱可用
                        BigDecimal gap = threshold.subtract(orderAmount);
                        vo.setReason("还差 " + gap.setScale(2, RoundingMode.HALF_UP) + " 元可用");
                    }

                    return vo;
                }).sorted(Comparator.comparing(UserCouponVO::getAvailable).reversed()) // 让可用的排在前面
                .collect(Collectors.toList());
    }

    /**
     * 锁定优惠券 (0 -> 1)
     * 调用时机：用户提交订单，后端创建订单记录时
     */
    @Override
    @Transactional(rollbackFor = Exception.class)  //方便之后做扩展
    public void lockCoupon(String orderSn, Long recordId) {
        Long userId = UserHolder.getUser().getId();
        
        // 只有状态为 0 (未使用) 的券才能被锁定为 1 (已锁定)
        int rows = couponUserRecordMapper.updateStatus(recordId,
                CouponConstants.USER_RECORD_STATUS_LOCKED,
                CouponConstants.USER_RECORD_STATUS_UNUSED);

        if (rows == 0) {
            // 报错说明：券可能已经过期、被别人锁了、或者根本不是这个用户的
            throw new BusinessException("[锁定优惠券] 锁定失败");
        }
        log.info("[锁定优惠券] 锁定成功，主订单号：{}，优惠券记录ID: {}", orderSn, recordId);
    }



    /**
     * 正式核销优惠券 (1 -> 2)
     * 调用时机：支付系统回调，确认支付成功时
     */
    @Override
    public void useCoupon(Long recordId) {
        //这里为了方便手机扫码测试，先不传入userId了,其实也不需要传
        //Long userId = UserHolder.getUser().getId();
        //log.info("内部调用：正式核销优惠券. 用户:{}, 记录ID:{}", userId, recordId);
        // 只有【已锁定】的券才能变成【已使用】
        //int rows = couponUserRecordMapper.updateStatus(recordId, userId,
        //        CouponConstants.USER_RECORD_STATUS_USED,
        //        CouponConstants.USER_RECORD_STATUS_LOCKED);
        int rows = couponUserRecordMapper.updateStatus(recordId,
                CouponConstants.USER_RECORD_STATUS_USED,
                CouponConstants.USER_RECORD_STATUS_LOCKED);

        if (rows == 0) {
            log.error("[优惠券核销] 核销失败, 记录ID: {}, 当前状态可能不是1",recordId);
            throw new BusinessException("优惠券核销失败");
        }

        log.info("[优惠券核销] 核销成功, 记录ID: {}",recordId);
    }


    /**
     * 释放/回滚优惠券 (1 -> 0)
     * 调用时机：用户手动取消订单 或 支付超时自动关闭订单
     */
    @Override
    public void releaseCoupon(String orderSn, Long recordId) {

        // 只有【已锁定】的券才能退回【未使用】
        int rows = couponUserRecordMapper.updateStatus(recordId,
                CouponConstants.USER_RECORD_STATUS_UNUSED,
                CouponConstants.USER_RECORD_STATUS_LOCKED);

        if (rows > 0) {
            log.info("[回滚优惠券] 回滚成功，优惠券记录ID: {}，主订单号{}", recordId,orderSn);
        }
    }

    /**
     * 计算当前优惠券的减免金额
     * @param couponUserRecordId
     * @param totalAmount
     * @return
     */
    @Override
    public BigDecimal calculateDiscountAmount(Long couponUserRecordId, BigDecimal totalAmount) {
        if (couponUserRecordId == null) {
            return BigDecimal.ZERO;
        }

        CouponUserRecord record = couponUserRecordMapper.selectById(couponUserRecordId);
        CouponTemplate  template = couponTemplateMapper.selectById(record.getTemplateId());
        // 判定 1：状态是否被锁定
        //if (record.getStatus() == CouponConstants.USER_RECORD_STATUS_LOCKED) {
        //    throw new BusinessException("该优惠券已被其他订单锁定");
        //}



        // 判定 2：时间是否有效
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(record.getStartTime())) {
            throw new BusinessException("活动尚未开始");
        }
        if (now.isAfter(record.getEndTime())) {
            throw new BusinessException("活动已经结束");
        }

        // 判定 3：金额门槛校验 (最核心的凑单逻辑)
        BigDecimal threshold = template.getThresholdAmount();
        if (totalAmount.compareTo(threshold) < 0) {
            throw new BusinessException("不满足优惠券的使用条件");
        }

        //返回优惠券的减命金额
        return template.getReduceAmount();

    }


    /**
     * 判断当前用户手里是否有还没用的该种优惠券，ture为有，false为没有。
     * @param userId
     * @param templateId
     * @return
     */
    private boolean hasAvailableCoupon(Long userId, Long templateId) {
        if (userId == null) return false;

        // 统计该用户手里“还没用”的这张券的数量
        Long count = couponUserRecordMapper.selectCount(new LambdaQueryWrapper<CouponUserRecord>()
                .eq(CouponUserRecord::getUserId, userId)
                .eq(CouponUserRecord::getTemplateId, templateId)
                // 重点：只有状态是 0(未使用) 或 1(锁定中) 才算作“已拥有”
                .in(CouponUserRecord::getStatus,
                        CouponConstants.USER_RECORD_STATUS_UNUSED,
                        CouponConstants.USER_RECORD_STATUS_LOCKED)
        );

        return count > 0;
    }


    /**
     * 封装VO的有效期展示文案
     * @param vo
     * @param t
     */
    private void fillValidityDesc(CouponTemplateVO vo, CouponTemplate t) {
        // 判定有效期类型：
        if (t.getValidType() == CouponConstants.VALID_TYPE_FIXED) { // 1-固定时间
            // 格式化日期，去掉时分秒，只留年月日，看起来更清爽
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
            String start = t.getUseStartTime().format(formatter);
            String end = t.getUseEndTime().format(formatter);

            vo.setValidityDesc(String.format("%s - %s", start, end));

        } else { // 2-领取后N天
            // 针对相对有效期，展示文案
            vo.setValidityDesc(String.format("领取后%d天内有效", t.getValidDays()));
        }
    }


    /**
     * 将 Lua 脚本的返回状态码转换为业务错误信息
     */
    private String mapResultToMsg(Long result) {
        if (result == null) {
            return "系统繁忙，请稍后再试";
        }

        // 这里的数字必须和你 Lua 脚本中 return 的值一一对应
        switch (result.intValue()) {
            case 0:
                return "领取成功"; // 实际上 0 会走异步入库逻辑，不会抛异常
            case 1:
                return "优惠券已经领光啦";
            case 2:
                return "您已经领过这张券了，不要贪心哦";
            case -1:
                return "活动尚未开启或已结束";
            default:
                return "领取失败，系统开小差了";
        }
    }

    /**
     * 批量获取优惠券模板并封装成 Map
     * Key: 模板ID, Value: 模板详情对象
     */
    private Map<Long, CouponTemplate> getTemplateMap(List<CouponUserRecord> records) {
        if (CollectionUtils.isEmpty(records)) {
            return new HashMap<>();
        }

        // 1. 提取所有不重复的模板 ID
        List<Long> templateIds = records.stream()
                .map(CouponUserRecord::getTemplateId)
                .distinct()
                .collect(Collectors.toList());

        // 2. 批量查询数据库 (SELECT * FROM coupon_template WHERE id IN (...))
        List<CouponTemplate> templates = couponTemplateMapper.selectBatchIds(templateIds);

        // 3. 转换为 Map 结构，方便 O(1) 效率查找
        return templates.stream()
                .collect(Collectors.toMap(CouponTemplate::getId, t -> t));
    }

    /**
     * 将 领取记录 和 模板详情 聚合成前端显示的 VO
     */
    private UserCouponVO convertToVO(CouponUserRecord record, CouponTemplate template) {
        UserCouponVO vo = new UserCouponVO();

        // 1. 拷贝模板基础信息（标题、金额、门槛等）
        // 如果属性名完全一致，可以用 BeanUtils，否则建议手动 set 保证安全
        vo.setTemplateId(template.getId());
        vo.setTitle(template.getTitle());
        vo.setSubTitle(template.getSubTitle());
        vo.setReduceAmount(template.getReduceAmount());
        vo.setThresholdAmount(template.getThresholdAmount());

        // 2. 拷贝个人领取记录信息（记录ID、状态）
        vo.setId(record.getId());
        vo.setStatus(record.getStatus());

        // 3. 核心：设置该用户真实的有效期（使用我们之前写的 DateUtils）
        // 注意：一定要用 record 里的时间，因为“领取后N天有效”类型的券，每个人的时间都不一样
        vo.setStartTime(DateUtils.format(record.getStartTime()));
        vo.setEndTime(DateUtils.format(record.getEndTime()));

        return vo;
    }
}
