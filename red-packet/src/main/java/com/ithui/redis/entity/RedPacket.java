package com.ithui.redis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author jihui
 * @date 2023/9/12 21:54
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class RedPacket implements Serializable {

    //红包的金额
    private double money;

    //红包id
    private String redPacketId;

}
