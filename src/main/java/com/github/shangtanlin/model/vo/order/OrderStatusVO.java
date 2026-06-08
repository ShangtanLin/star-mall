package com.github.shangtanlin.model.vo.order;

import lombok.Data;

@Data
public class OrderStatusVO {
    // 给前端用的最终展示状态
    private Integer displayStatus;

    // 可选的动态提示语（如果前端不想自己维护文案，可以直接用这个）
    private String displayMsg;
}
