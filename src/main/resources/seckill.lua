--传入的参数
local voucherId= ARGV[1] -- 优惠券ID
local userId = ARGV[2] -- 用户ID
local orderId= ARGV[3] -- 订单ID
--数据库
local stockKey = 'seckill:stock:'..voucherId --  库存key   字符串使用 .. 拼接
local orderKey = 'seckill:order:'..voucherId --  订单key

--脚本业务
if(tonumber(redis.call('get', stockKey)) <= 0) then -- get stockKey 判断是否大于0    使用tunumber将字符串转换为数字, 然后再来作比较
    return 1;
end
-- 判断用户是否下单 , SISMAMBER orderKey userID
if (redis.call('SISMEMBER', orderKey,userId)==1) then
    -- 存在, 说明是重复下单
    return 2;
end

-- 创建订单的相关操作
    --1.扣库存  incrby stockKey -1
    --2.下单(保存用户到redis的集合)  sadd orderKey userId
    --3.发送消息到队列 XADD stream.orders[name]  *[消息ID]  k1 v1 k2 v2
redis.call('incrby',stockKey,-1)
redis.call('sadd', orderKey, userId)
redis.call('xadd','stream.orders','*','userId',userId, 'voucherId',voucherId ,'Id',orderId)

return 0;