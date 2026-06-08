package com.github.shangtanlin.model.dto.product;

import lombok.Data;

@Data
public class ProductQueryDTO {
    private Integer pageNo; //当前页码
    private Integer pageSize; //每页条数
}
