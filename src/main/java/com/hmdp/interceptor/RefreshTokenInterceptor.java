package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Dhx_
 * @className RefreshTokenInterceptor
 * @description TODO 这个拦截器用来 拦截所有的请求, 同时刷新token的有效期
 * @date 2022/10/9 20:27
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {


    StringRedisTemplate stringRedisTemplate;
    public RefreshTokenInterceptor(){

    }
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate=stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //todo 获取请求头中的token
        String token =request.getHeader("authorization");

        if(StringUtils.isBlank(token)){//token为空
            return true;
        }

        //todo 2.基于token获取redis中的用户
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);

        //3.判断用户是否存在
        if(userMap.isEmpty()){
            //4.不存在拦截 , 返回401状态码=>未授权
            return true;
        }
        //5.将查询到的hashmap数据转换为对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false); //
        //6.存在,保存在TreadLocal 中
        UserHolder.saveUser(userDTO);
        //7.刷新token有效期
        stringRedisTemplate.expire(tokenKey,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //8.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
