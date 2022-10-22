package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author Dhx_
 * @className SimpleRedisLock
 * @description TODO
 * @date 2022/10/12 8:57
 */
public class SimpleRedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;

    /**
     * 不同的业务对应不同的锁的名称
     */
    private String name;

    public SimpleRedisLock(){}

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    /**
     * 锁的前缀
     */
    public static final String KEY_PREFIX="lock:";

    /**
     * 线程标识前缀-> 防止多个jvm 生成了相同的线程ID
     */
    public static final String ID_Prefix= UUID.randomUUID().toString(true)+"-";

    public static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    //初始化lua脚本
    static{
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    @Override
    public boolean trylock(Long timeOutSec) {
        //获取当前线程的标识
        String threadId = ID_Prefix+Thread.currentThread().getId();
        String key= name +KEY_PREFIX;
        Boolean success=stringRedisTemplate.opsForValue().setIfAbsent(key, threadId,timeOutSec, TimeUnit.SECONDS);
        //手动拆箱, 防止空指针  isTrue , 如果success 是null ,也会返回false;
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock() {
        //调用lua脚本 , 只有一行代码, 保证释放锁的原子性
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX+name),
                ID_Prefix+Thread.currentThread().getId()
        );
    }
}
