package com.github.shangtanlin.model.dto.address;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserAddressUpdateDTO {
    @NotBlank(message = "地址id不能为空")
    private Long id;

    private String receiverName;


    private String receiverPhone;


    private String province;


    private String city;


    private String district;


    private String detailAddress;

    // 是否设为默认：通常前端传 0 或 1
    private Integer isDefault;
}
