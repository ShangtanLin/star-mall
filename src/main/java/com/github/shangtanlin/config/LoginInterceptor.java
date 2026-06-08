package com.github.shangtanlin.config;

import com.github.shangtanlin.common.utils.UserHolder;
import com.github.shangtanlin.model.dto.user.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

import static com.github.shangtanlin.common.constant.RedisConstant.LOGIN_TOKEN_KEY;
import static com.github.shangtanlin.common.constant.RedisConstant.TOKEN_TTL;

@Component
public class LoginInterceptor implements HandlerInterceptor {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //0.放行跨域的OPTIONS预检请求
        if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
            return true;
        }


        //1.获取token并判断是否为空
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(401);
            return false;
        }
        String token = header.substring(7);
        //2.判断token是否有效
        String key = LOGIN_TOKEN_KEY + token;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            response.setStatus(401);
            return false;
        }
        //3.将json中的用户id存入UserHolder
        Long id = Long.valueOf(json);
        UserDTO userDTO = new UserDTO();
        userDTO.setId(id);
        UserHolder.setUser(userDTO);
        //4.刷新TTL
        stringRedisTemplate.expire(key,TOKEN_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //从ThreadLocal中移除用户id
        UserHolder.removeUser();
    }
}
