package com.example.nettydemo.tcp.server;

import com.example.nettydemo.tcp.DemoMessage;

import io.netty.channel.Channel;

/**
 * Created by banshan
 */
public interface IClientListener {

    /**
     * 客户端建立连接
     *
     */
    void onClientConnect(Channel channel);

    /**
     * 客户端断开连接
     */
    void onClientDisConnect(Channel channel);

    /**
     * 接收到客户端消息
     */
    void onClientMessage(DemoMessage message, Channel channel);

}
