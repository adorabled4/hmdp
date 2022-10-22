package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserConstant;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.val;
import org.apache.coyote.Response;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Dhx_
 * @className LoginInterceptor
 * @description TODO
 * @date 2022/10/8 20:09
 */
public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate=stringRedisTemplate;
    }

    public LoginInterceptor(){}


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1. 判断是否需要去拦截(TheadLocal 中是否有用户)
        if(UserHolder.getUser()==null) {
            // 没有 , 需要拦截
            response.setStatus(401);
            //拦截
            return false;
        }
        //有用户 , 放行
        return true;
    }
}
