package com.hmdp.controller;

import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Dhx_
 * @className TestController
 * @description TODO
 * @date 2022/10/11 15:05
 */
@Slf4j
@RestController
public class TestController {

    @GetMapping("/test")
    public Result test(){
        log.info("测试集群");
        return Result.ok("测试成功!");
    }
}
