package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service

public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate  stringRedisTemplate;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1,查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2，判断是否过期
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            //未开始
            return Result.fail("活动还未开始");
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            //已经结束
            return Result.fail("活动已经结束");
        }
        //3,判断是否库存充足
        if(voucher.getStock()<1){
            //库存不足
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        /*synchronized (userId.toString().intern()){*/
        //自己创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock(LOCK_SHOP_KEY + userId, stringRedisTemplate);
        //锁住用户而不是整个方法，intern()放进常量池,在方法内加锁，先释放锁再提交事务
        boolean isLock = lock.tryLock(5);
        if(!isLock) {//反向逻辑
            return Result.fail("不允许重复下单");
        }
            //拿到事务的代理对象
        try {
            {IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);}
        } finally {
            lock.unLock();//出现异常，手动释放锁
        }
    }
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //一人一单
        Long userId = UserHolder.getUser().getId();


        //查询订单是否已经存在
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //判断是否存在
        if(count>0){
            //订单已经存在
            return Result.fail("用户已经购买过了");
        }
        //4，扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)//解决超卖问题!!!
                .update();
        if(!success)
        {
            return Result.fail("库存不足");
        }
        //5，创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = redisIdWorker.nextId("order");//唯一id生成器
        voucherOrder.setId(orderId);
        //用户id(过滤器获得)

        voucherOrder.setUserId(userId);
        //代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        //6，返回订单id
        return Result.ok(orderId);
    }
}
