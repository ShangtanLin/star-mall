package com.github.shangtanlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.shangtanlin.model.entity.order.RefundOrder;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 退款订单 Mapper 接口
 */
@Mapper
public interface RefundOrderMapper extends BaseMapper<RefundOrder> {

    @Delete("DELETE FROM refund_order WHERE status = 4")
    int deleteSuccessAll();


    // 这里是空的！不需要写任何方法！
    // 因为 BaseMapper<RefundOrder> 已经自动提供了：
    // 1. insert(RefundOrder entity)         -> 用于 createSystemRefund 落库
    // 2. selectOne(Wrapper<T> queryWrapper) -> 用于幂等校验查询
    // 3. updateById(RefundOrder entity)     -> 用于定时任务更新退款状态
    // 等等所有你目前需要的单表操作方法
}
