package com.ithui.distributed2.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class InventoryService {

    @Value( "${server.port}" )
    private String port;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Lock lock = new ReentrantLock();

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
    }
}
