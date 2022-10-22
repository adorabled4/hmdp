package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserConstant;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone=loginForm.getPhone();
        //1.先校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //2.校验验证码  todo 从redis中获取
        String code=loginForm.getCode();
        String realCode=stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY+phone);

        if(realCode==null|| !realCode.equals(code)){
            //3.不一致,报错
            return Result.fail("验证码错误!");
        }
        //4.一致=>查询数据库 select  form tb_user where phone = ?
        User user = query().eq("phone",phone).one();
        //5.不存在,注册
        if(user==null){
            //6. 不存在 放入数据库中保存
            user= createUserWithPhone(phone); // 这个方法封装了 写入数据库操作
        }

        //7.保存到Redis中

        //todo 7.1 随机生成token ,作为登录令牌
        String token= UUID.randomUUID().toString();
        String tokenKey=RedisConstants.LOGIN_USER_KEY+token;
        //todo 7.2 将user对象作为hash存储
//        session.setAttribute(UserConstant.USER_LOGIN_STATUS,userDTO);
        // 将userDTO转换到HashMap中存储
        UserDTO userDTO=BeanUtil.copyProperties(user,UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().
                        setIgnoreNullValue(true).
                        setFieldValueEditor((fieldName, fieldValue) ->fieldValue.toString()));
        //todo 7.3 存储
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //7.4设置token有效期
        stringRedisTemplate.expire(tokenKey,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //todo 8 返回token到客户端
        return Result.ok(token);// !!!!!!!!!!!!!!!!!!!!!!注意返回token!!!!!!!!!!!!!!!!!!!!!!!!
    }

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号 => 校验手机号是否符合规范
        if(RegexUtils.isPhoneInvalid(phone)){ // 手机号不管用
            //2.如果不符合, 返回错误信息
            return Result.fail("手机号不符合规范!");
        }
        //3.符合,生成验证码
        String code= RandomUtil.randomNumbers(6); //生成六位验证码
        //4.保存验证码到Redis , 需要注意的是 要设置有效期以及前缀
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY+phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        session.setAttribute(UserConstant.VERIFICATION_CODE,code);
        //5.发送验证码,返回OK
        log.debug("发送验证码成功: {}",code);
        //返回OK
        return Result.ok();
    }

    /**
     * 创建用户并保存
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        User user=new User();
        user.setPhone(phone);
        user.setNickName(UserConstant.DEFAULT_USER_NAME_PREFIX+RandomUtil.randomString(10));
        //2. 保存用户
        save(user);
        return user;
    }
}
