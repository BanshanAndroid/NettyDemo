package com.example.nettydemo.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * @author banshan
 * Describe: 编码
 */
public class DemoEncoder extends MessageToByteEncoder<DemoMessage> {

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, DemoMessage message, ByteBuf byteBuf) {
        ProtocolUtils.writeDemoMessage(byteBuf, message);
    }

}
