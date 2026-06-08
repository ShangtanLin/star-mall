package com.github.shangtanlin.model.entity.order;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款订单实体类
 */
@Data
public class RefundOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 原订单号
     */
    private String orderSn;

    /**
     * 退款单号(防重)
     */
    private String refundSn;

    /**
     * 🌟 微信退款单号 (微信系统生成的，对应微信的 refund_id)
     * 初始入库时为 null，在微信退款成功回调时更新落盘
     */
    private String wechatRefundId;

    /**
     * 退款金额(元)
     * 注意：数据库字段注释写的是(分)，但我们用了 decimal(10,2)，实际业务中存的是元。
     * 强烈建议将数据库字段注释改为'退款金额(元)'，保持代码与数据库语义一致，避免以后新同事接手时造成混淆！
     */
    private BigDecimal refundAmount;

    /**
     * 状态：0:待处理 ➡ 1:处理中 ➡ 4:退款成功 / 5:退款失败
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 退款成功时间
     * 注意：这里千万不要使用 MyBatis-Plus 的 @TableField(fill = FieldFill.INSERT) 或 INSERT_UPDATE
     * 因为这个时间不是记录创建时间，而是在微信回调确认成功时才写入的！
     */
    private LocalDateTime successTime;

    /**
     * 重试次数
     * 记录调用微信退款API失败的次数，超过一定次数（如3次）将标记为异常，需人工介入
     */
    private Integer retryCount;

    /**
     * 下次允许重试时间
     * 用于退避策略，当退款失败后，必须等到该时间之后，定时任务才会再次捞取该单据重试
     */
    private LocalDateTime nextRetryTime;


    /**
     * 退款失败原因
     * 记录微信返回的具体错误码或异常信息，如：NOTENOUGH(余额不足)、SYSTEMERROR(系统超时)等
     */
    private String failReason;

}
