package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;


    @Override
    public Result queryBlogById(Long id) {
        //查询blog
        Blog blog  = getById(id);
        if(blog==null){
            return Result.fail("笔记不存在!");
        }
        //查询blog的用户 , 同时封装信息
        queryBlogUser(blog);
        //返回blog
        //todo 设置blog是否被当前用户(userHolder)点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(page);
    }

    /**
     * 为blog 设置用户信息
     * @param blog
     */
    private void queryBlogUser(Blog blog){
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result likeBlog(Long id) {
        //1.获取用户
        UserDTO user = UserHolder.getUser();
        String key= BLOG_LIKED_KEY + id; // 这个id 是 blog 的 id
        //2.判断当前登录用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(key,user.getId().toString());
        //3.1如果已经点赞
        if(score!=null){
            //todo 数据库点赞数-1 ,将用户从redis的set集合中移除
            //  set liked= liked -1  where blog_id = id
            boolean success= update().setSql("set liked = liked -1 ").eq("id",id).update();
            if(success){
                stringRedisTemplate.opsForZSet().remove(key,user.getId().toString());
            }
        }else{
            //3.2没有点赞
            //todo 数据库点赞数+1 把用户存储到redis的集合中 zadd key value score
            boolean success= update().setSql("set liked = liked + 1 ").eq("id",id).update();
            if(success){
                stringRedisTemplate.opsForZSet().add(key,user.getId().toString(),System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    /**
     * 判断 blog 是否 被当前的用户点赞 => 就是判断userId 是否存在于 blog 的 set集合中(redis)
     * @param blog
     * @return 如果已经点赞, 返回true
     */
    private void isBlogLiked(Blog blog){
        UserDTO user=UserHolder.getUser();
        if(user==null){ // 用户未登录, 也可以看blog , 此时不需要确定是否点赞
            return ;
        }
        Long id = user.getId();
        String key= BLOG_LIKED_KEY + blog.getId(); // 这个id 是 blog 的 id
        Double score = stringRedisTemplate.opsForZSet().score(key, id.toString());
        blog.setIsLiked(score!=null);
    }


    @Override
    public Result queryBlogLikes(Long id) {
        //1.查询当前blog点赞的用户 zrange  key 0 4
        Set<String> top5Id = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        if(top5Id==null||top5Id.isEmpty()){
            // 没人给当前的这个blog点赞
            return Result.ok(Collections.emptyList());
        }
        //2.解析出用户id
        List<Long> idList = top5Id.stream().map(Long::valueOf).collect(Collectors.toList());
        ////3.根据id 查询用户
        String idStr= StrUtil.join(",", idList);// 这个方法会把List中的元素拼接起来
        List<UserDTO> users=userService.query()
                .in("id",idList)
                .last("ORDER BY FILED (id, "+ idStr + ")").list()
                .stream()
                .map(user-> BeanUtil.copyProperties(user,UserDTO.class))
                .collect(Collectors.toList());
        //4.返回userDTO  List 数据
        return  Result.ok(users);
    }
}
