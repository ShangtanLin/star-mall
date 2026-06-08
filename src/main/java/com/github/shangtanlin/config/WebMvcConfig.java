package com.github.shangtanlin.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Autowired
    private LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(
                        "/api/user/sendCode",
                        "/api/user/login/code",
                        "/api/user/register",
                        "/api/user/login/password",
                        "/avatar/*",
                        "/spu/*",
                        "/shop_logo/*",
                        "/api/category/**",
                        "/api/product/**",
                        // 核心修改点：必须补全 /api 前缀，并确保双星号覆盖所有子路径
                        "/api/order/pay/**",
                        "/api/category/**",
                        "/api/notify/**"
                );
    }
}
