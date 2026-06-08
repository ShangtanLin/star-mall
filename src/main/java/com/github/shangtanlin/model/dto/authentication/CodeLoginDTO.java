package com.github.shangtanlin.model.dto.authentication;

import lombok.Data;

@Data
public class CodeLoginDTO {
    private String phoneNumber;

    private String  code;
}
