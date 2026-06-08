package com.github.shangtanlin.service.impl;

import com.github.shangtanlin.model.entity.wechat.WechatRefundNotifyResult;
import com.github.shangtanlin.model.entity.wechat.WechatRefundResult;
import com.github.shangtanlin.service.WechatPayService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class WechatPayServiceImpl implements WechatPayService {

    /**
     * 调用微信退款 API (API v3 版本)
     *
     * @param orderSn      原商户订单号
     * @param refundSn     商户退款单号
     * @param refundAmount 退款金额 (元)
     * @return 微信退款同步响应结果
     */
    @Override
    public WechatRefundResult refund(String orderSn, String refundSn, BigDecimal refundAmount) {
        WechatRefundResult result = new WechatRefundResult();
        result.setRefundSn(refundSn);
        result.setWechatRefundId("wechatRefundId");
        result.setSuccess(Boolean.TRUE);
        //result.setSuccess(Boolean.FALSE);
        return result;
    }

    /**
     * 验签并解析微信退款结果回调报文 (API v3 版本)
     *
     * @param requestHeader HTTP请求头 (用于验签)
     * @param requestBody   HTTP请求体 (密文)
     * @return 解密后的退款结果实体
     */
    @Override
    public WechatRefundNotifyResult parseRefundNotify(String requestHeader, String requestBody) {
        return null;
    }
}
