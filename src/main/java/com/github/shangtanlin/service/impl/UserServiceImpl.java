package com.github.shangtanlin.service.impl;

import com.github.shangtanlin.common.utils.UserHolder;
import com.github.shangtanlin.model.dto.authentication.CodeLoginDTO;
import com.github.shangtanlin.model.dto.authentication.PasswordLoginDTO;
import com.github.shangtanlin.model.dto.authentication.RegisterDTO;
import com.github.shangtanlin.model.dto.user.UserDTO;
import com.github.shangtanlin.model.entity.user.User;
import com.github.shangtanlin.mapper.UserMapper;
import com.github.shangtanlin.result.Result;
import com.github.shangtanlin.service.UserService;
import com.github.shangtanlin.model.vo.UserInfoVO;
import io.micrometer.common.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.shangtanlin.common.constant.RedisConstant.*;
import static com.github.shangtanlin.common.constant.UserConstant.*;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserMapper userMapper;

    //发送验证码
    @Override
    public Result<?> sendCode(String phoneNumber, String type) {
        //0.判断手机号是否为空
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return Result.fail("手机号不能为空");
        }
        //1.判断手机号是否合法
        if (!phoneNumber.matches("^1[3-9]\\d{9}$")) {
            return Result.fail("手机号不合法");
        }
        //2.调用接口得到验证码,这里采用随机生成
        String code = String.format("%06d", new Random().nextInt(999999));
        //3.判断验证码类型,将验证码存入redis
        String prefix = "";
        if (type.equals(TYPE_LOGIN)) {
            prefix = LOGIN_CODE_KEY;
        } else prefix = REGISTER_CODE_KEY;
        String key = prefix + phoneNumber;
        stringRedisTemplate.opsForValue().set(key, code, CODE_TTL, TimeUnit.MINUTES);
        //4.返回
        return Result.ok(code);
    }

    //注册
    @Override
    public Result<?> register(RegisterDTO registerDTO) {
        String phoneNumber = registerDTO.getPhoneNumber();
        //1.判断手机号是为空
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return Result.fail("手机号不能为空");
        }
        //2.判断手机号是否合法
        if (!phoneNumber.matches("^1[3-9]\\d{9}$")) {
            return Result.fail("手机号不合法");
        }
        if (userMapper.selectByPhone(phoneNumber) != null) {
            return Result.fail("该手机号已注册");
        }
        //3.根据手机号从Redis中查询验证码并校验
        String key = REGISTER_CODE_KEY + phoneNumber;
        String code = stringRedisTemplate.opsForValue().get(key);
        if (code == null || code.isEmpty()) {
            return Result.fail("验证码不存在");
        }
        String registerCode = registerDTO.getCode();
        if (!registerCode.equals(code)) {
            return Result.fail("验证码已失效");
        }
        //4.封装用户信息并插入数据库
        User user = new User();
        user.setPhone(phoneNumber);
        userMapper.insert(user);
        //5.生成token，写入redis，并返回给前端
        String token = UUID.randomUUID().toString().replaceAll("-","");
        String tokenKey = LOGIN_TOKEN_KEY + token;
        String id = user.getId().toString();
        stringRedisTemplate.opsForValue().set(tokenKey,id,TOKEN_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }

    //用户名密码登录
    @Override
    public Result<?> loginByPassword(PasswordLoginDTO passwordLoginDTO) {
        //1.判断用户名是否存在
        String username = passwordLoginDTO.getUsername();
        String password = passwordLoginDTO.getPassword();
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        //2.判断密码是否正确
        if ( !StringUtils.isNotBlank(password) || !password.equals(user.getPassword())) {
            return Result.fail("密码错误");
        }
        //3.创建token并存入redis
        String token = UUID.randomUUID().toString().replaceAll("-", "");
        String key = LOGIN_TOKEN_KEY + token;
        String id = user.getId().toString();
        stringRedisTemplate.opsForValue().set(key,id,TOKEN_TTL,TimeUnit.MINUTES);
        //4.返回token
        return Result.ok(token);
    }

    //手机号验证码登录
    @Override
    public Result<?> loginByCode(CodeLoginDTO codeLoginDTO) {
        //1.判断用户是否存在
        String phoneNumber = codeLoginDTO.getPhoneNumber();
        User user = userMapper.selectByPhone(phoneNumber);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        //2.判断验证码是否正确
        String codeKey = LOGIN_CODE_KEY + phoneNumber;
        String code = stringRedisTemplate.opsForValue().get(codeKey);
        if (code == null || !code.equals(codeLoginDTO.getCode())) {
            return Result.fail("验证码错误");
        }
        //3.创建token并存入redis
        String token = UUID.randomUUID().toString().replaceAll("-", "");
        String tokenKey = LOGIN_TOKEN_KEY + token;
        String id = user.getId().toString().replaceAll("-", "");
        stringRedisTemplate.opsForValue().set(tokenKey,id,TOKEN_TTL,TimeUnit.MINUTES);
        //4.返回token
        return Result.ok(token);
    }

    //获取当前登录用户信息
    @Override
    public Result<?> getProfile() {
        //1.从ThreadLocal中获得用户id
        UserDTO userDTO = UserHolder.getUser();
        Long id = userDTO.getId();
        //2.根据id查询用户信息
        User user = userMapper.selecyById(id);
        //3.封装用户信息
        UserInfoVO userInfoVO = new UserInfoVO();
        userInfoVO.setUserId(user.getId());
        userInfoVO.setUserName(user.getUsername());
        userInfoVO.setAvatar(user.getAvatar());
        return Result.ok(userInfoVO);
    }


}
