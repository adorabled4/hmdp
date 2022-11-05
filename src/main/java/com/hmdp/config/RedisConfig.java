package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Dhx_
 * @className RedisConfig
 * @description TODO
 * @date 2022/10/12 11:34
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedissonClient redissonClient(){
        //配置
        Config config = new Config();
        config  .useSingleServer().setAddress("redis://192.168.159.129:6379").setPassword("qwer");
        //创建RedissonClient对象
        return Redisson.create(config);
    }
}
