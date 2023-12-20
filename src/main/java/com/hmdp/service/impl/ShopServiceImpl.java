package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
    @Override
    public Result queryById(Long id) {
        //从redis查询商铺
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //判断是否存在
        //存在，就返回
        if(StrUtil.isNotBlank(shopJson))
        {//对于null和""都会返回false
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);

            return Result.ok(shop);
        }
        //判断是不是空值（注意这里是!=）
        if(shopJson != null)
        {
            return Result.fail("店铺不存在");
        }
        //不存在，就从数据库查询
        //不存在报404
        Shop shop = getById(id);
        if(shop == null)
        {
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");

        }
        //存在写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回

        return Result.ok(shop);


    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        //对id判断是否存在
        Long id = shop.getId();
        if(id == null)
        {
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2，删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
