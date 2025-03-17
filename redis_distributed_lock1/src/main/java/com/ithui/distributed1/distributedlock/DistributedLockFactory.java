package com.ithui.distributed1.distributedlock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.Lock;

@Component
public class DistributedLockFactory {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public Lock getDistributedLock(String typeName) {
        if (typeName == null) {
            throw new IllegalArgumentException("typeName cannot be null");
        }else if (typeName.equals("redis")) {
            return new DistributedLock(redisTemplate,"redisLock");
        }else if(typeName.equals("mysql")) {
            //TODO: implement mysql lock
            return null;
        }else if(typeName.equals("zookeeper")){
            //TODO: implement zookeeper lock
            return null;
        }
        return null;
    }
}
