package com.hmdp.utils;

/**
 * @author Dhx_
 * @className Ilock
 * @description TODO
 * @date 2022/10/12 8:56
 */
public interface ILock {

    /**
     * @param timeOutSec 设置锁的过期时间
     * @return  true表示可以获取到锁
     */
    boolean trylock(Long timeOutSec);


    /**
     * 释放锁
     */
    void unlock();

}
