package com.ithui.distributed1.service;

import cn.hutool.core.lang.UUID;
import com.ithui.distributed1.distributedlock.DistributedLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;


import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class InventoryService {

    @Value( "${server.port}" )
    private String port;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Lock distributedLock = new DistributedLock(redisTemplate, "inventory_lock");

    public String getInventory() {
        String retResult = "";
        // 单机版的锁
        distributedLock.lock();
        try {
            String inventory = redisTemplate.opsForValue().get("inventory001");
            Integer inventoryNum = inventory == null? 0 : Integer.parseInt(inventory);
            if(inventoryNum > 0){
                redisTemplate.opsForValue().set("inventory001", String.valueOf(--inventoryNum));
                retResult = port + " " + "库存减少成功，剩余库存：" + inventoryNum;
                System.out.println(port + " " + "库存扣减成功,剩余库存：" + inventoryNum);
            }else {
                retResult = "库存不足";
                System.out.println(port + " " + "库存不足");
            }
        } finally {
            distributedLock.unlock();
        }
        return retResult;
    }


    /**
     * 分布式锁的第一种实现方式：基于Redis的setnx命令，设置一个key，并设置超时时间，保证原子性，
     * 同时，在删除锁之前，判断是否是自己的锁，防止其他线程删除自己的锁。这已经可以满足大部分场景，但还有一些问题需要解决。
     * 问题：如何设置可重入锁，保证线程安全。
     */
    /*
    public String getInventory() {
        String retResult = "";
        // 集群版的锁
        String lockKey = "inventory_lock";
        String value = UUID.randomUUID().toString() + Thread.currentThread().getId();
        // 保证加锁和设置超时时间的原子性
        while (!redisTemplate.opsForValue().setIfAbsent(lockKey, value, 10, TimeUnit.SECONDS)) {
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            String inventory = redisTemplate.opsForValue().get("inventory001");
            Integer inventoryNum = inventory == null ? 0 : Integer.parseInt(inventory);
            if(inventoryNum > 0){
                redisTemplate.opsForValue().set("inventory001", String.valueOf(--inventoryNum));
                retResult = port + " " + "库存减少成功，剩余库存：" + inventoryNum;
                System.out.println(port + " " + "库存扣减成功,剩余库存：" + inventoryNum);
            }else {
                retResult = "库存不足";
                System.out.println(port + " " + "库存不足");
            }
        } finally {
            // 改进：保证删除锁的原子性
            String evalStr = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else return 0 " +
                    "end";
            redisTemplate.execute(new DefaultRedisScript<>(evalStr, Boolean.class), Arrays.asList(lockKey), value);
        }
        return retResult;
    }*/

    /**
     * 分布式锁的进阶：基于Redis的setnx命令，设置一个key，并设置超时时间，保证原子性。
     * 同时，在删除锁之前，判断是否是自己的锁，防止其他线程删除自己的锁。
     * 问题：判断是否是自己的锁市，并不能完全保证原子性。
     */
    /*
    public String getInventory() {
        String retResult = "";
        // 集群版的锁
        String lockKey = "inventory_lock";
        String value = UUID.randomUUID().toString() + Thread.currentThread().getId();
        // 保证加锁和设置超时时间的原子性
        while (!redisTemplate.opsForValue().setIfAbsent(lockKey, value, 10, TimeUnit.SECONDS)) {
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            String inventory = redisTemplate.opsForValue().get("inventory001");
            Integer inventoryNum = inventory == null ? 0 : Integer.parseInt(inventory);
            if(inventoryNum > 0){
                redisTemplate.opsForValue().set("inventory001", String.valueOf(--inventoryNum));
                retResult = port + " " + "库存减少成功，剩余库存：" + inventoryNum;
                System.out.println(port + " " + "库存扣减成功,剩余库存：" + inventoryNum);
            }else {
                retResult = "库存不足";
                System.out.println(port + " " + "库存不足");
            }
        } finally {
            // 改进：为了防止某个线程将其他线程的锁删掉，在删除之前，必须先判断是否是自己的锁
            if (redisTemplate.opsForValue().get(lockKey).equals(value)) {
                redisTemplate.delete(lockKey);
            }
        }
        return retResult;
    }*/

    /**
     * 分布式锁的第一种实现方式：基于Redis的setnx命令，设置一个key，并设置超时时间，保证原子性。
     */
    /*
    public String getInventory() {
        String retResult = "";
        // 集群版的锁
        String lockKey = "inventory_lock";
        String value = UUID.randomUUID().toString() + Thread.currentThread().getId();
        // 保证加锁和设置超时时间的原子性
        while (!redisTemplate.opsForValue().setIfAbsent(lockKey, value, 10, TimeUnit.SECONDS)) {
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // 设置超时时间为10s
        try {
            String inventory = redisTemplate.opsForValue().get("inventory001");
            Integer inventoryNum = inventory == null ? 0 : Integer.parseInt(inventory);
            if(inventoryNum > 0){
                redisTemplate.opsForValue().set("inventory001", String.valueOf(--inventoryNum));
                retResult = port + " " + "库存减少成功，剩余库存：" + inventoryNum;
                System.out.println(port + " " + "库存扣减成功,剩余库存：" + inventoryNum);
            }else {
                retResult = "库存不足";
                System.out.println(port + " " + "库存不足");
            }
        } finally {
            redisTemplate.delete(lockKey);
        }
        return retResult;
    }*/

    /**
     * 设置超时间，为了解决当某时刻系统突然挂掉，导致锁一直被占用，需要设置兜底的超时时间，防止死锁。
     * 问题：但是，加锁和设置时间不是原子操作，也可能会出现时间设置不了。
     */
    /*
    public String getInventory() {
        String retResult = "";
        // 集群版的锁
        String lockKey = "inventory_lock";
        String value = UUID.randomUUID().toString() + Thread.currentThread().getId();
        while (!redisTemplate.opsForValue().setIfAbsent(lockKey, value)) {
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // 设置超时时间为10s
        redisTemplate.expire(lockKey, 10, TimeUnit.SECONDS);
        try {
            String inventory = redisTemplate.opsForValue().get("inventory001");
            Integer inventoryNum = inventory == null ? 0 : Integer.parseInt(inventory);
            if(inventoryNum > 0){
                redisTemplate.opsForValue().set("inventory001", String.valueOf(--inventoryNum));
                retResult = port + " " + "库存减少成功，剩余库存：" + inventoryNum;
                System.out.println(port + " " + "库存扣减成功,剩余库存：" + inventoryNum);
            }else {
                retResult = "库存不足";
                System.out.println(port + " " + "库存不足");
            }
        } finally {
            redisTemplate.delete(lockKey);
        }
        return retResult;
    }*/

    /**
     * 自旋锁，防止stack overflow异常
     * 问题：高并发场景下，会出现线程阻塞，导致系统响应变慢，但某时刻系统挂了，则会导致锁一直被占用，导致其他线程无法正常执行。
     */
    /*
    private final Lock lock = new ReentrantLock();
    public String getInventory() {
        String retResult = "";
        // 集群版的锁
        String lockKey = "inventory_lock";
        String value = IdUtil.simpleUUID() + Thread.currentThread().getId();
        // 改用自旋锁
        while (!redisTemplate.opsForValue().setIfAbsent(lockKey, value)) {
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            String inventory = redisTemplate.opsForValue().get("inventory001");
            Integer inventoryNum = inventory == null ? 0 : Integer.parseInt(inventory);
            if(inventoryNum > 0){
                redisTemplate.opsForValue().set("inventory001", String.valueOf(--inventoryNum));
                retResult = port + " " + "库存减少成功，剩余库存：" + inventoryNum;
                System.out.println(port + " " + "库存扣减成功,剩余库存：" + inventoryNum);
            }else {
                retResult = "库存不足";
                System.out.println(port + " " + "库存不足");
            }
        } finally {
            redisTemplate.delete(lockKey);
        }
        return retResult;
    }*/

    /**
     * 集群版的锁，使用递归持续调用循环，直到获取到锁。
     * 问题：在高并发场景下，会出现线程阻塞，导致系统响应变慢。并且可能会出息那stackoverflow的异常。
     * 并且高并发场景下，禁止使用递归调用。
     */
    /*
    public String getInventory() {
        String retResult = "";
        // 集群版的锁
        String lockKey = "inventory_lock";
        String value = IdUtil.simpleUUID() + Thread.currentThread().getId();
        Boolean setResult = redisTemplate.opsForValue().setIfAbsent(lockKey, value);
        if (!setResult) {
            // 等待20ms递归调用
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            }catch (InterruptedException e) {
                e.printStackTrace();
            }
            getInventory();
        }
        try {
            String inventory = redisTemplate.opsForValue().get("inventory001");
            Integer inventoryNum = inventory == null ? 0 : Integer.parseInt(inventory);
            if(inventoryNum > 0){
                redisTemplate.opsForValue().set("inventory001", String.valueOf(--inventoryNum));
                retResult = port + " " + "库存减少成功，剩余库存：" + inventoryNum;
                System.out.println(port + " " + "库存扣减成功,剩余库存：" + inventoryNum);
            }else {
                retResult = "库存不足";
                System.out.println(port + " " + "库存不足");
            }
        } finally {
            redisTemplate.delete(lockKey);
        }
        return retResult;
    }*/

    /**
     * 基于JVM的单机版的锁，实现简单
     * 问题：不适用集群，因为这是基于JVM的分布式锁，在分布式环境下，JVM之间是不共享的，所以无法实现分布式锁。
     * 单机版的锁，在高并发场景下，会出现线程阻塞，导致系统响应变慢。
     */
    /*

    public String getInventory() {
        String retResult = "";
        // 单机版的锁
        lock.lock();
        try {
            String inventory = redisTemplate.opsForValue().get("inventory001");
            Integer inventoryNum = inventory == null? 0 : Integer.parseInt(inventory);
            if(inventoryNum > 0){
                redisTemplate.opsForValue().set("inventory001", String.valueOf(--inventoryNum));
                retResult = "库存减少成功，剩余库存：" + inventoryNum;
                System.out.println(port + " " + "库存扣减成功,剩余库存：" + inventoryNum);
            }else {
                retResult = "库存不足";
                System.out.println(port + " " + "库存不足");
            }
        } finally {
            lock.unlock();
        }
        return retResult;
    }*/
}
