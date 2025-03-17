package com.ithui.distributed1.distributedlock;

import cn.hutool.core.util.IdUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class DistributedLock implements Lock {

    private StringRedisTemplate stringRedisTemplate;

    private String lockName;// 锁的名称==>KEYS[1]
    private String threadUUID;// 当前线程的UUID==>ARGV[1]
    private long expireTime; // 锁的过期时间==>ARGV[2]

    public DistributedLock(StringRedisTemplate stringRedisTemplate, String lockName){
        this.stringRedisTemplate = stringRedisTemplate;
        this.lockName = lockName;
        this.threadUUID = IdUtil.simpleUUID() + Thread.currentThread().getId();
        this.expireTime = 50; // 默认50秒过期时间
    }

    @Override
    public void lock() {
        tryLock();
    }

    @Override
    public boolean tryLock() {
        try {
            return tryLock(-1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        // 真正实现分布式锁的逻辑
        if(time == -1){
            String script =
                    "if redis.call('exists', KEYS[1]) == 0 or redis.call('hexists', KEYS[1], ARGV[1]) == 1  then " +
                            "redis.call('hincrby', KEYS[1], ARGV[1], 1) " +
                            "redis.call('expire', KEYS[1], ARGV[2]) " +
                            "return 1 " +
                    "else " +
                            "return 0 " +
                    "end";

            while(!stringRedisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName), threadUUID, String.valueOf(expireTime))){
                try {
                    TimeUnit.MILLISECONDS.sleep(20);
                }catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void unlock() {
        String script =
                "if redis.call('hexists',KEYS[1],ARGV[1]) == 0 then " +
                        "   return 0 " +
                        "elseif redis.call('hincrby',KEYS[1],ARGV[1],-1) == 0 then " +
                        "   return redis.call('del',KEYS[1]) " +
                        "else " +
                        "   return 0 " +
                        "end";

        Long flag = stringRedisTemplate.execute(
                new DefaultRedisScript<>(script, Long.class),
                Arrays.asList(lockName),
                threadUUID
        );

        if (flag == null || flag == 0) {
            throw new RuntimeException("this thread does not hold the lock");
        }
    }

    // 暂时用不到
    @Override
    public void lockInterruptibly() throws InterruptedException {

    }
    @Override
    public Condition newCondition() {
        return null;
    }
}
