package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.db.sql.Order;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    /**
     * 秒杀券service层 => 获取信息判断
     */
    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 用于生成全局唯一id
     */
    @Resource
    RedisIdWorker redisIdWorker;

    @Resource
    RedissonClient redissonClient;


    public static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    public static final ExecutorService SECKILL_ORDER_EXECUTOR =Executors.newSingleThreadExecutor();

    /**
     * 在当前类初始化完毕后去执行
     */
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }


    private IVoucherOrderService proxy;
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId=UserHolder.getUser().getId();
        long orderId=redisIdWorker.nextId("order");
        //1. 执行lua脚本
        Long result=stringRedisTemplate.execute(
                SECKILL_SCRIPT, // 脚本
                Collections.emptyList(), // KEY[]
                voucherId.toString(), userId.toString(),String.valueOf(orderId) //ARGV[]
        );
        //2.判断lua脚本的执行结果是否为0
        int res= result.intValue();
        if(res!=0){  // result不会为null , 不必在意idea的提示
            //2.1 判断 1 or 2  => 1库存不足 2重复下单 => 与lua脚本对应
            //不为0 ,代表没有购买资格
            return Result.fail(res==1?"库存不足!":"不能重复下单!");
        }

        //2.2为0 , 代表有购买资格, 把下单的信息保存到阻塞队列
        VoucherOrder voucherOrder=new VoucherOrder();
        //2.3 订单Id
        voucherOrder.setId(orderId);
        //2.4 用户id
        voucherOrder.setUserId(userId);
        //2.5 代金券id
        voucherOrder.setVoucherId(voucherId);
        //2.6创建阻塞队列
//        orderTasks.add(voucherOrder);
        //3.获取代理对象 , 放到当前类里面
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //3.返回订单ID
        return Result.ok(orderId);
    }

    String queueName="stream.orders";
    private class VoucherOrderHandler implements  Runnable{
        @Override
        public void run() {
            while(true){
                try {
                    //1.获取消息队列中的头部元素 : XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.order >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2.判断消息获取是否成功
                    if(list==null || list.size()==0){
                        //2.1没有消息, 继续下一次循环
                        continue;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    //3.获取成功, 创建订单
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //如果获取成功, 那么可以下单
                    handleVoucherOrder(voucherOrder);
                    //5.ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId()); //通过record获取消息ID
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                    handlePendingList();
                }
            }
        }
    }
//    private BlockingQueue<VoucherOrder> orderTasks=new ArrayBlockingQueue<>(1024*1024);
//    private class VoucherOrderHandler implements  Runnable{
//        @Override
//        public void run() {
//            while(true){
//                try {
//                    //1.获取阻塞队列中的头部元素 : take()
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    //2.创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常",e);
//                }
//            }
//        }
//    }

    /**
     * 处理订单
     * @param voucherOrder
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder){
        //注意此时不能从UserHolder中获取用户, 因为此时不是用的ThreadLocal
        Long userId= voucherOrder.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        SimpleRedisLock redisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        boolean isLock = lock.tryLock();
        //获取锁
        if(!isLock){ // 理论上这个锁是不会获取失败的, 因为在redis中已经处理过了
            //获取锁失败
            log.error("不允许重复下单");
            return ;
        }
        try{
            //获取事务有关的代理对象, spring事务是代理这个bean, 然后再去执行事务
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            lock.unlock();//释放锁
        }
    }
    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        // 加载文件源
        SECKILL_SCRIPT.setLocation(new ClassPathResource("secKill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        Long userId=UserHolder.getUser().getId();
//        //1. 执行lua脚本
//        Long result=stringRedisTemplate.execute(
//                SECKILL_SCRIPT, // 脚本
//                Collections.emptyList(), // KEY[]
//                voucherId.toString(), userId.toString() //ARGV[]
//        );
//        //2.判断lua脚本的执行结果是否为0
//        int res= result.intValue();
//        if(res!=0){  // result不会为null , 不必在意idea的提示
//            //2.1 判断 1 or 2  => 1库存不足 2重复下单 => 与lua脚本对应
//            return Result.ok(res==1?"库存不足!":"不能重复下单!");
//        }
//        //2.1不为0 ,代表没有购买资格
//        long orderId=redisIdWorker.nextId("order");
//        //2.2为0 , 代表有购买资格, 把下单的信息保存到阻塞队列
//        VoucherOrder voucherOrder=new VoucherOrder();
//        //2.3 订单Id
//        voucherOrder.setId(orderId);
//        //2.4 用户id
//        voucherOrder.setUserId(userId);
//        //2.5 代金券id
//        voucherOrder.setVoucherId(voucherId);
//        //2.6创建阻塞队列
//        orderTasks.add(voucherOrder);
//        //3.获取代理对象 , 放到当前类里面
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        //3.返回订单ID
//        return Result.ok(orderId);
//    }

    //    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始 or  结束
//        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            // 不在秒杀的时间段之内
//            return Result.fail("秒杀未开始!");
//        }
//        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
//            // 不在秒杀的时间段之内
//            return Result.fail("秒杀已结束!");
//        }
//
//        //3.判断库存
//        Integer stock = voucher.getStock();
//        if(stock<1){
//            return Result.fail("库存不足!");
//        }
//        Long userId= UserHolder.getUser().getId();
//        //创建锁对象
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
////        SimpleRedisLock redisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
//        boolean isLock = lock.tryLock();
//        //获取锁
//        if(!isLock){
//            //获取锁失败
//            return Result.fail("不允许重复下单"); // 当前的环境是 单个用户发出多个请求,
//        }
//        try{
//            //获取事务有关的代理对象, spring事务是代理这个bean, 然后再去执行事务
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return  proxy.createVoucherOrder(voucherId);
//        }finally {
//            lock.unlock();//释放锁
//        }
//    }
/*
        synchronized (userId.toString().intern()) {
        //获取事务有关的代理对象, spring事务是代理这个bean, 然后再去执行事务
        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        return  proxy.createVoucherOrder(voucherId);
    }
* */

    @Transactional // 加入事务 , 出现问题可以回滚
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId=voucherOrder.getUserId();
        //todo 添加一人一单的判断
        long orderId = redisIdWorker.nextId("order"); // 通过redis 生成全局唯一的订单ID
        //4.0.1 查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        //4.0.2 判断是否存在
        if(count>0){
            log.error("用户已经购买一次!");
            return ;
        }
        //4.   扣减库存
        boolean success= seckillVoucherService
                // set stock =stock -1
                .update().setSql("stock = stock-1")
                //where voucher_id=  ?and stock = ?
                .eq("voucher_id",voucherOrder.getVoucherId()).gt("stock",0)
                .update();

        if(!success){
            log.error("库存不足");
        }
        // 订单写入数据库
        save(voucherOrder);
     }

    private void handlePendingList() {
        while(true){
            try {
                //1.获取pending-list中的头部元素 : XREADGROUP GROUP g1 c1 COUNT 1  STREAMS streams.order 0 : 0代表读的是pendingList
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create("stream.orders", ReadOffset.from("0"))
                );
                //2.判断消息获取是否成功
                if(list==null || list.size()==0){
                    //2.1没有消息, 说明pending-list中没有异常消息,结束循环
                    break;
                }
                MapRecord<String, Object, Object> record = list.get(0);
                //3.获取成功, 创建订单
                Map<Object, Object> value = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                //如果获取成功, 那么可以下单
                handleVoucherOrder(voucherOrder);
                //5.ACK确认 SACK stream.orders g1 id
                stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId()); //通过record获取消息ID
            } catch (Exception e) {
                log.error("处理pending-list异常",e);
            }
        }
    }
}
