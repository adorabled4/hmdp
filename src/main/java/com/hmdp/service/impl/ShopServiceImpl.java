package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        // this::getById => id2->getById(id2)
        // 解决缓存穿透
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class, this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //使用逻辑过期 解决缓存击穿 => 注意使用逻辑过期需要现在redis中保存 永久的数据
        // Shop shop =cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class, this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //使用互斥锁解决缓存击穿
        Shop shop=cacheClient.queryWithMutex(CACHE_SHOP_KEY,id,Shop.class, this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if(shop==null){
            return Result.fail("店铺不存在!");
        }
        return Result.ok(shop);
    }

    /**
     * 缓存击穿
     * @param id
     * @return
     */
//    public Shop queryWithMutex(Long id) throws RuntimeException {
//        String key= CACHE_SHOP_KEY+id;
//        //1. 从redis中查询商户缓存 ,
//        String shopJson=stringRedisTemplate.opsForValue().get(key+id);
//        //2判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //3 存在 ==> 返回 Shop对象
//            Shop shop= BeanUtil.toBean(shopJson,Shop.class);
//            return shop;
//        }
//        if(shopJson!=null){
//            //如果shopJson 不为null 那么一定是一个空字符串,
//            return null;
//        }
//        // 4.实现缓存重构
//        //4.1获取互斥锁
//        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
//        Shop shop=null;
//        try {
//            boolean isLock=tryLock(lockKey);
//            //4.2判断是否获取成功
//            if(!isLock){
//                //4.3如果获取失败 , 那么就休眠并重试
//                Thread.sleep(50);//休眠
//                return queryWithMutex(id);
//            }
//            //4.4成功 , 根据id查询数据库
//            shop=getById(id);
//            //5.不存在 返回null
//            if(shop==null){
//                //将空值写入redis => 避免缓存穿透
//                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
//                return null;
//            }
//            //6.存在, 写入redis
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            //7.释放互斥锁
//            unLock(lockKey);
//        }
//        //8.返回
//        return shop;
//    }

    /**
     * 缓存穿透
     * @return
     */
//    public Result queryWithPassThrough(Long id){
//        String key= CACHE_SHOP_KEY+id;
//        //1. 从redis中查询商户缓存 ,
//        String shopJson=stringRedisTemplate.opsForValue().get(key);
//        //2判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //3 存在 ==> 返回 Shop对象
//            Shop shop= BeanUtil.toBean(shopJson,Shop.class);
//            return Result.ok(shop);
//        }
//        if(shopJson!=null){
//            //如果shopJson 不为null 那么一定是一个空字符串,
//            return Result.ok("店铺信息为空");
//        }
//        //4 不存在 根据ID查询数据库
//        Shop shop=getById(id);
//
//        //5.不存在 返回error
//        if(shop==null){
//            //将空值写入redis => 避免缓存穿透
//            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return Result.ok("店铺不能为空!");
//        }
//        //6.存在, 写入redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //7.返回
//        return Result.ok(shop);
//    }

    /**
     * 逻辑过期解决缓存击穿
     * @param id
     * @return
     */
//    public Shop queryWithLogicalExpire(Long id){
//        String key= CACHE_SHOP_KEY+id;
//        //1. 从redis中查询商户缓存 ,
//        String shopJson=stringRedisTemplate.opsForValue().get(key);
//        //2判断是否存在
//        if(StrUtil.isBlank(shopJson)){
//            //3不存在 , 返回null
//            return null;
//        }
//        //4.命中,需要吧JSON反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime=redisData.getExpireTime();
//        //5. 需要判断过期时间
//        if(expireTime.isAfter(LocalDateTime.now())){
//            //5.1未过期, 返回店铺信息
//            return shop;
//        }
//        //5.2已过期, 需要缓存重建
//        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
//        ///6. 缓存重建
//        //6.1获取互斥锁
//        boolean isLock=tryLock(lockKey);
//        //6.2判断是否获取锁成功
//        if(isLock){
//            //todo 6.3 成功 => 开启独立线程, 实现缓存重建
//            //将空值写入redis => 避免缓存穿透
//            CACHE_REBUILD_EXECUTOR.submit(()->{
//                try {
//                    //重建缓存
//                    this.saveShop2Redis(id, 20L);
//                    // save 方法封装了 读取数据库以及 写入缓存
//                }catch(Exception e){
//                    throw new RuntimeException(e);
//                }finally {
//                    //释放锁
//                    unLock(lockKey);
//                }
//            });
//        }
//        //6.4 失败 返回过期的店铺信息
//        return shop;
//    }

    /**
     * 设置逻辑过期
     * @param id
     */
    void saveShop2Redis(Long id , Long expireSeconds){
        //1.查询店铺数据
        Shop shop = getById(id);
        //2.封装成逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis , 没有设置TTL ,实际上是永久有效,
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 通过 redis 的 string 类型的 setnx来设置锁
     * @param key 锁的key
     * @return
     * setnx 的返回值是0 1 , 0代表设置失败 , 1 代表设置成功
     *  RedisTemplate自动转换为了Boolean类型,
     *  那么对应到工程中, 也就是false代表设置失败, 1代表设置成功
     *  也就是说如果是返回false就是之前就已经上锁了, 1表示上锁成功.
     */
//    private boolean tryLock(String key){
//        // 设置锁的时间为10s
//        Boolean flag= stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag); //使用工具类转换成初始类型避免出现nullPointerException
//    }

    /**
     * 解锁
     * @param key
     */
//    private void unLock(String key){
//        stringRedisTemplate.delete(key);
//    }


    /**
     * 更新店铺信息 => 方案:先更新数据库,然后删除缓存
     * @param shop 需要更新的shop
     * @return 返回Ok
     */
    @Override
    @Transactional // 事务回滚 ,通过事务保证一致性
    public Result updateShop(Shop shop) {
        Long id=shop.getId();
        if(id==null){
            Result.fail("店铺id不能为空");
        }
        String key= CACHE_SHOP_KEY+id;
        //1. 更新数据库
        updateById(shop);
        //2. 删除缓存
        stringRedisTemplate.delete(key);
        return Result.ok();
    }
}
