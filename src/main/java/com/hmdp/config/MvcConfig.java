package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @author Dhx_
 * @className MvcConfig
 * @description mvc配置类
 * @date 2022/10/8 20:18
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //添加拦截器
        // order越小 先执行

        // token刷新拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0); // 拦截所有请求

        //登录拦截器
        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns(
                "/user/code",
                "/user/login",
                "/blog/hot",
                "/shop/**",
                "/upload/**",
                "/voucher/**",
                "/voucher-order/**",
                "/shop-type/list"
        ).order(1);// 不登录也能看的需要放行 验证码  登录请求 热点请求 shop下的所有请求 (upload 用来测试方便)
    }
}
