package com.example.nettydemo.tcp;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;

/**
 * Created by banshan
 */
public class ProtocolUtils {

    public static void writeDemoMessage(ByteBuf byteBuf, DemoMessage message) {
        try {
            //每个字符串之前需要加一个int，用于指定这个字符串的长度！！！
            byteBuf.writeIntLE(message.getMessageSize());
            byteBuf.writeBytes(message.getBody().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getString(ByteBuf byteBuf, int length) {
        byte[] keyBytes = new byte[length];
        byteBuf.readBytes(keyBytes);
        try {
            return new String(keyBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
