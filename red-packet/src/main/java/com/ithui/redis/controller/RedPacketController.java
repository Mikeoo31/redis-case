package com.ithui.redis.controller;

import com.alibaba.fastjson.JSON;
import com.ithui.redis.entity.RedPacket;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author jihui
 * @date 2023/9/12 21:40
 */
@RestController
@Log4j2
public class RedPacketController {

    public static final String RED_PACKET_KEY = "redpacket:";//红包的key
    public static final String RED_PACKET_CONSUMER_KEY = "redpacket:consumer:";//用户领取红包记录

    @Autowired
    private RedisTemplate redisTemplate;
    //private StringRedisTemplate stringRedisTemplate;

    @GetMapping("/send")
    public String sendRedPacket(@RequestParam("money") double totalMoney, @RequestParam("num") Integer redPacketNum){
        //通过二倍均值法确定每个红包的金额
        String redPacketId = UUID.randomUUID().toString();

        List<RedPacket> redPacketList = this.divideRedPacketMoney(totalMoney, redPacketNum, redPacketId);

        String redPacketKey = RED_PACKET_KEY + redPacketId;

        redisTemplate.opsForList().leftPushAll(redPacketKey,redPacketList);
        //stringRedisTemplate.opsForList().leftPushAll(redPacketKey, redPacketList);

        //设置红包的过期时间
        redisTemplate.expire(redPacketKey,1, TimeUnit.DAYS);

        return "红包id" + redPacketKey + "红包列表" + JSON.toJSONString(redPacketList);

    }

    //抢红包
    @GetMapping("/receive")
    public String receiveRedPacket(@RequestParam("id") String redPacketId,
                                   @RequestParam("userId" ) String userId
    ) {
        //判断请求的用户是否已经抢过红包
        Boolean isHasKey = redisTemplate.opsForHash().hasKey(RED_PACKET_CONSUMER_KEY + redPacketId, userId);
        if(!isHasKey){
            //从redis中出列一个元素
            RedPacket redPacket = (RedPacket) redisTemplate.opsForList().leftPop(RED_PACKET_KEY + redPacketId);
            //判断红包是否已经抢完
            if(redPacket != null){
                //将红包信息存入到redis中的hash中，以便后续使用
                redisTemplate.opsForHash().put(RED_PACKET_CONSUMER_KEY + redPacketId,userId,redPacket);

                log.info("用户{}抢到红包金额为{}",userId,redPacket.getMoney());

                return "抢到红包金额为" + redPacket.getMoney();
            }else {
                return "红包已被抢完";
            }
        }else {
            return "你已经抢过红包！";
        }
    }

    private List<RedPacket> divideRedPacketMoney(double totalMoney, Integer redPacketNum,String redPacketId){
        //用二倍均值法将红包拆分并存入到一个list集合中
        List<RedPacket> redPacketList = new ArrayList<>();

        //剩余红包个数
        int restRedPacketNum = redPacketNum;

        Random random = new Random();

        for (int i = 0; i < redPacketNum - 1; i++) {
            //该方法调用从该随机数生成器的序列中返回下一个伪随机、均匀分布的双精度值，介于 0.0 和 1.0 之间
            double account = random.nextDouble() * (totalMoney / restRedPacketNum) * 2;
            //防止出现金额为0
            account = Math.max(0.01,account);
            //金额和人数都递减
            totalMoney = totalMoney - account;
            restRedPacketNum--;

            //account保留两位小数
            account = Double.parseDouble(String.format("%.2f",account));

            RedPacket redPacket = new RedPacket(account,redPacketId);
            redPacketList.add(redPacket);
        }
        totalMoney = Double.parseDouble(String.format("%.2f",totalMoney));
        RedPacket redPacket = new RedPacket(totalMoney,redPacketId);
        redPacketList.add(redPacket);

        return redPacketList;
    }
}
