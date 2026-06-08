package com.github.shangtanlin.controller;

import com.github.shangtanlin.model.dto.authentication.CodeLoginDTO;
import com.github.shangtanlin.model.dto.authentication.PasswordLoginDTO;
import com.github.shangtanlin.model.dto.authentication.SendCodeDTO;
import com.github.shangtanlin.model.dto.authentication.RegisterDTO;
import com.github.shangtanlin.result.Result;
import com.github.shangtanlin.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;

    //发送验证码(包括注册和登录)
    @PostMapping("/sendCode")
    public Result<?> sendCode(@RequestBody SendCodeDTO sendCodeDTO) {
        String phoneNumber = sendCodeDTO.getPhoneNumber();
        String type = sendCodeDTO.getType();
        return userService.sendCode(phoneNumber,type);
    }

    //注册
    @PostMapping("/register")
    public Result<?> register(@RequestBody RegisterDTO registerDTO) {
        return userService.register(registerDTO);
    }

    //账号密码登录
    @PostMapping("/login/password")
    public Result<?> loginByPassword(@RequestBody PasswordLoginDTO loginDTO) {
        return userService.loginByPassword(loginDTO);
    }

    //手机号验证码登录
    @PostMapping("/login/code")
    public Result<?> loginByCode(@RequestBody CodeLoginDTO codeLoginDTO) {
        return userService.loginByCode(codeLoginDTO);
    }

    @GetMapping("/profile")
    public Result<?> profile() {
        return userService.getProfile();
    }
}
