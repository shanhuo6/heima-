package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Thread.sleep;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private RedisIdWorker redisIdWorker;

//    private ExecutorService executorService = Executors.newFixedThreadPool(500);
    @Test
    public void test1() throws InterruptedException {

        for (int i = 0; i < 300; i++) {
            sleep(1000);
            System.out.println(redisIdWorker.nextId("order"));
        }

    }
    @Test
    public void test2() {
        Boolean a =null;
        System.out.println(a);

    }





}
