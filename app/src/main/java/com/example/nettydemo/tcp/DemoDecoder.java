package com.example.nettydemo.tcp;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;


/**
 * @author banshan
 * Describe: 解码
 */
public class DemoDecoder extends ByteToMessageDecoder {

    private volatile static byte[] LENGTH_BYTES = new byte[4];
    private static final int MAX_PACK_SIZE = 1024 * 1024 * 64;

    private volatile static int packetSize = 0;
    /**
     * 在工作线程中回调
     */
    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) {
        //记录读取的位置
        byteBuf.markReaderIndex();

        //判断缓冲区是否可读
        if (!byteBuf.isReadable()) {
            byteBuf.resetReaderIndex();
            return;
        }

        //如果可读消息不足四个字节，那么重置指针位置，返回
        if (byteBuf.readableBytes() < LENGTH_BYTES.length) {
            byteBuf.resetReaderIndex();
            return;
        }
        byteBuf.readBytes(LENGTH_BYTES);
        Unpack unpack = new Unpack(LENGTH_BYTES);
        packetSize = unpack.popInt();
        if (packetSize < 0 || packetSize > MAX_PACK_SIZE) {
            throw new RuntimeException("Invalid packet, size = " + packetSize);
        }

        //如果可读字节数不足，那么重置指针位置，返回
        if (byteBuf.readableBytes() < packetSize - LENGTH_BYTES.length) {
            byteBuf.resetReaderIndex();
            return;
        }
        ByteBuf readBytes = byteBuf.readBytes(packetSize);

        DemoMessage message = new DemoMessage();
        message.setMessageSize(packetSize);
        message.setBody(ProtocolUtils.getString(readBytes, packetSize));

        packetSize = 0;
        list.add(message);
    }
}
