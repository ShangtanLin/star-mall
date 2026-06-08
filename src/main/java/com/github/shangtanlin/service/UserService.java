package com.github.shangtanlin.service;

import com.github.shangtanlin.model.dto.authentication.CodeLoginDTO;
import com.github.shangtanlin.model.dto.authentication.PasswordLoginDTO;
import com.github.shangtanlin.model.dto.authentication.RegisterDTO;
import com.github.shangtanlin.result.Result;

public interface UserService {
    Result<?> sendCode(String phoneNumber, String type);

    Result<?> register(RegisterDTO registerDTO);

    Result<?> loginByPassword(PasswordLoginDTO passwordLoginDTO);

    Result<?> loginByCode(CodeLoginDTO codeLoginDTO);


    Result<?> getProfile();
}
