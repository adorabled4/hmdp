package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author Dhx_
 * @className RedisIdWorker 生成全局唯一ID
 * @description TODO
 * @date 2022/10/11 11:25
 */
@Component
public class RedisIdWorker {

    /**
     * 开始时间戳
     */
    public static final long BEGIN_TIMESTAMP=1664582400L;
    /**
     * 序列号的位数
     */
    public static final int COUNT_BITS=32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * @param keyPrefix 业务前缀:根据不同的业务, 生成ID
     * @return
     */
    public long nextId(String keyPrefix) {
        //1.时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);// 获取当前的秒数
        long timeStamp = nowSecond-BEGIN_TIMESTAMP;
        //2.序列号
        //自增长 =>拼接日期字符串, 改变key => 使用每天的日期作为key
        //2.1获取当前日期, 天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //redis会自动创建, 不用担心npe
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //3.拼接返回
        //return timeStamp<<COUNT_BITS | count;    // 向左移动, 为序列号空出位置(32)
        return timeStamp<<COUNT_BITS | count;    // 向左移动, 为序列号空出位置(32)
    }

    public static void main(String[] args) {
//        LocalDateTime time = LocalDateTime.of(2022, 10, 1, 0, 0, 0);
//        long second = time.toEpochSecond(ZoneOffset.UTC);
//        System.out.println("second=  " + second);
    }
}
