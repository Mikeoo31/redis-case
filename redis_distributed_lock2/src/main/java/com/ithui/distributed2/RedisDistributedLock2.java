package com.ithui.distributed2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RedisDistributedLock2 {
    public static void main(String[] args) {
        SpringApplication.run(RedisDistributedLock2.class, args);
    }
}
