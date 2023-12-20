package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    public SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true);//true去掉下划线
    private  static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }//提前加载好lua脚本
    @Override
    public boolean tryLock(long timeoutSec) {
        //获得线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();//不同jvm内部threadgetId()可能相同,加上随机前缀
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId , timeoutSec, TimeUnit.SECONDS);
        //set lock thread1 ex 10 nx
        return Boolean.TRUE.equals(success);//Boolean自动拆箱会有null风险
    }

    @Override
    public void unLock() {
        //调用lua脚本,保证原子性
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }

/*    public void unLock() {
        //释放锁(只能获取锁的线程去删除锁)
        //获取当前线程id
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //取出redis 的id
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if(threadId.equals(id)){
        stringRedisTemplate.delete(KEY_PREFIX + name);}
    }*/
}
