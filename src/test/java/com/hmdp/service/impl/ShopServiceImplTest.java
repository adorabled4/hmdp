package com.hmdp.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Dhx_
 * @className ShopServiceImplTest
 * @description TODO
 * @date 2022/10/10 14:59
 */
@SpringBootTest
class ShopServiceImplTest {

    @Resource
    ShopServiceImpl shopService;
    @Test
    void saveShop2Redis() {
        shopService.saveShop2Redis(15L,20L);
    }
}