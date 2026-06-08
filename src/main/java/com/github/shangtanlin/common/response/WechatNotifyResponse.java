package com.github.shangtanlin.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信 V3 回调统一响应体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WechatNotifyResponse {

    private String code;
    private String message;

    // 🌟 提供两个静态工厂方法，用起来极其爽！
    public static WechatNotifyResponse success() {
        return new WechatNotifyResponse("SUCCESS", "成功");
    }

    public static WechatNotifyResponse fail(String message) {
        return new WechatNotifyResponse("FAIL", message);
    }
}
