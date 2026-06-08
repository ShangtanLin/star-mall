package com.github.shangtanlin.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderSyncMessage implements Serializable {
    private Long subOrderId;
    private Integer type; // 1-新增，2-修改 3-删除
}