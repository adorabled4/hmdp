package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.VoucherServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import io.netty.handler.ipfilter.IpSubnetFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    CacheClient cacheClient;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private VoucherServiceImpl voucherService;

    ExecutorService es= Executors.newFixedThreadPool(500);
    @Test
    public void tokenTest(){
        String tokenKey="login:token:cb0c6cdd-0080-476a-990e-b14a7d2d380a";
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(tokenKey);

        System.out.println(entries);
    }

    @Test
    public void getVoucher(){
        Voucher voucher = voucherService.getById(1);
        System.out.println(JSONUtil.toJsonStr(voucher));
    }

    @Test
    public void test(){
        System.out.println(new Date().getTime());
    }


    @Test
    public void IdTest() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task= ()->{
            for (int i = 0; i < 100; i++) {
                long order = redisIdWorker.nextId("order");
                System.out.println("id = "+ order);
            }
            latch.countDown(); // 每当线程执行完毕 就执行 countDown();
        };
        long begin= System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await(); // 等待所有的线程结束
        long end= System.currentTimeMillis();
        System.out.println("time : "+ (end-begin));
    }

    @Test
    public void nextIdTest(){
        long order = redisIdWorker.nextId("order");
        System.out.println("id = "+ order);
    }

    @Test
    public void testLeft(){
        //912437 124738
        long a=912437L;
        long b= 912437L;
        long c= 124738L;
        //System.out.println(a<<=32);
        //System.out.println(b<<32);
        //3918887074660352
        //3918887074660352
        System.out.println(a<<=32 |c);
        System.out.println(b<< 32 |c);
        //15675548298641408
        //3918887074785090
    }

    @Resource
    UserMapper userMapper;


    /**
     * 生成所有user 的token , 便于测试 => jmeter
     * @throws IOException
     */
    @Test
    public void FileAndRedisInputTokenTest() throws IOException {
        List<User> users = userMapper.selectList(new QueryWrapper<>());
        FileOutputStream fos=new FileOutputStream(new File("C:\\Users\\lenovo\\Desktop\\tokens.txt"));
        for (User user : users) {
            System.out.println("user : "+ user.getId());
            //生成token
            String token= UUID.randomUUID().toString();
            String tokenKey= LOGIN_USER_KEY+token;
            // 写入txt文件
            byte[] bytes=token.getBytes();
            fos.write(bytes);
            fos.write((byte)('\n'));

            //存入redis => 实际存储使用UserDTO对象
            UserDTO userDTO=BeanUtil.copyProperties(user,UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                    CopyOptions.create().
                            setIgnoreNullValue(true).
                            setFieldValueEditor((fieldName, fieldValue) ->fieldValue.toString()));
            stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
//            stringRedisTemplate.expire(tokenKey, Long.MAX_VALUE, TimeUnit.MINUTES);
        }
    }
    @Test
    public void FileInputTest() throws IOException { // 测试文件写入
        FileOutputStream fos=new FileOutputStream(new File("C:\\Users\\lenovo\\Desktop\\tokens.txt"));
        for (int i = 0; i < 10; i++) {
            String UUID= String.valueOf(cn.hutool.core.lang.UUID.randomUUID());
            byte[] bytes=UUID.getBytes();
            fos.write(bytes);
            fos.write((byte)('\n'));
        }
    }

    /**
     * 测试hash与string的存储占比
     */
    @Test
    public void HashTest() {
        final String TEST_KEY="pers:user:";
        for (int i = 0; i < 100; i++) {
            User user = userMapper.selectById(i);
            if(user==null){
                continue;
            }
            UserDTO userDTO=BeanUtil.copyProperties(user,UserDTO.class);
            String key1=TEST_KEY+"string:"+ UUID.randomUUID();
            String key2=TEST_KEY+"hash:"+ UUID.randomUUID();
            String json=JSONUtil.toJsonStr(userDTO);
            stringRedisTemplate.opsForValue().set(key1,json);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName,fieldValue)-> fieldValue.toString()));
            stringRedisTemplate.opsForHash().putAll(key2,userMap);
        }
    }

    @Test
    public void IconTest() {
        User user = userMapper.selectById(10);
        user.setIcon("https://xingqiu-tuchuang-1256524210.cos.ap-shanghai.myqcloud.com/1082/avater.png");
        userMapper.updateById(user);
    }
}
