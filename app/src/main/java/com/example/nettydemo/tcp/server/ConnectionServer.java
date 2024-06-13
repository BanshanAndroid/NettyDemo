package com.example.nettydemo.tcp.server;

import android.content.Context;
import android.util.Log;

import com.example.nettydemo.tcp.DemoDecoder;
import com.example.nettydemo.tcp.DemoEncoder;
import com.example.nettydemo.tcp.DemoMessage;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * Created by banshan
 */
public class ConnectionServer {

    private static final String TAG = "netty";
    /**
     * 服务是否已经启动
     */
    private volatile boolean isServerStart = false;

    /**
     * 引导类
     * 配置线程池，IP地址，端口号，Channel，业务Handler
     */
    private ServerBootstrap mBootstrap;

    /**
     * Context
     */
    private Context context;

    EventLoopGroup bossGroup;

    EventLoopGroup workerGroup;
    /**
     * 解码
     * 读取数据，处理拆包，粘包，压缩
     */
    private DemoDecoder mDemoDecoder;
    /**
     * 编码
     */
    private DemoEncoder mDemoEncoder;

    private SimpleChannelHandler mHandler;

    private IClientListener clientListener;

    private int inetPort;

    private ChannelFuture channelFuture;

    private ConnectionServer() {

    }

    private static ConnectionServer engin = new ConnectionServer();

    /**
     * 获取引擎
     *
     * @return ConnectionEngin
     */
    public static ConnectionServer getEngin() {
        return engin;
    }


    /**
     * 初始化
     *
     * @param context 上下文
     */

    public void init(Context context, IClientListener listener, int inetPort) {
        this.inetPort = inetPort;
        this.context = context;
        this.clientListener = listener;
        try {
            if (isServerStart) {
                return;
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
            disConnect();
            bossGroup = new NioEventLoopGroup();
            workerGroup = new NioEventLoopGroup();
            //构建引导程序
            mBootstrap = new ServerBootstrap();
            //设置EventGroup
            mBootstrap.group(bossGroup, workerGroup);
            //设置Channel
            mBootstrap.channel(NioServerSocketChannel.class);
            mBootstrap.option(ChannelOption.SO_BACKLOG, 128);
            //设置的好处是禁用Nagle算法。表示不延迟立即发送
            //这个算法试图减少TCP包的数量和结构性开销，将多个较小的包组合较大的包进行发送。
            //这个算法收TCP延迟确认影响，会导致相继两次向链接发送请求包。
            mBootstrap.option(ChannelOption.TCP_NODELAY, false);
            mBootstrap.option(ChannelOption.SO_KEEPALIVE, true);
            mBootstrap.childHandler(new CustomChannelInitializer());
            channelFuture = mBootstrap.bind(inetPort).sync();
            if (channelFuture.isSuccess()) {
                isServerStart = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void checkServer() {
        if (isServerStart && !isServerOpen()) {
            disConnect();
            init(context, clientListener, inetPort);
        }
    }

    private boolean isServerOpen() {
        if (channelFuture != null && channelFuture.channel() != null && channelFuture.channel().isWritable()) {
            return true;
        }
        return false;
    }

    /**
     * 写数据
     *
     * @param object Object
     */
    public void writeMessage(final Object object, Channel channel) {
        if (channel != null && channel.isOpen() && channel.isActive()) {
            channel.writeAndFlush(object).addListener(new GenericFutureListener<Future<? super Void>>() {
                @Override
                public void operationComplete(Future<? super Void> future) {
                    writeLog("writeMessage success");
                }
            });
        }
    }

    /**
     * 关闭服务
     */
    public void disConnect() {
        try {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        isServerStart = false;
    }

    private void removeHandler(Channel channel) {
        try {
            channel.pipeline().remove(mDemoDecoder);
            channel.pipeline().remove(mHandler);
            channel.pipeline().remove(mDemoEncoder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 是否连接状态
     *
     * @return true 连接
     */
    public boolean isServerStart() {
        return isServerStart;
    }


    public class CustomChannelInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel socketChannel) {
            ChannelPipeline pipeline = socketChannel.pipeline();
            mDemoDecoder = new DemoDecoder();
            mDemoEncoder = new DemoEncoder();
            mHandler = new SimpleChannelHandler();
            pipeline.addLast(mDemoDecoder);
            pipeline.addLast(mDemoEncoder);
            pipeline.addLast(mHandler);
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            super.handlerAdded(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
        }
    }

    public class SimpleChannelHandler extends SimpleChannelInboundHandler<DemoMessage> {


        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, final DemoMessage message) {
            //接收来自服务端的消息
            Channel channel = channelHandlerContext.channel();
            notifyReceiveMessage(message, channel);
        }

        /**
         * 连接成功
         */
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            //链接成功
            notifyClientConnect(ctx.channel());
        }

        /**
         * 链接失败
         */
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            //链接失败
            inActive(ctx);
            notifyClientDisConnect(ctx.channel());
        }

        /**
         * 异常
         */
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            //异常处理
            inActive(ctx);
            if (cause != null) {
                writeLog(cause.getMessage());
                cause.printStackTrace();
            }
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            super.handlerAdded(ctx);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            super.userEventTriggered(ctx, evt);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            super.channelWritabilityChanged(ctx);
        }
    }

    private void inActive(ChannelHandlerContext ctx) {

        try {
            Channel channel = ctx.channel();
            removeHandler(channel);
            channel.close();
            ctx.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyClientConnect(Channel channel) {
        if (clientListener != null) {
            clientListener.onClientConnect(channel);
        }
    }


    private void notifyClientDisConnect(Channel channel) {
        if (clientListener != null) {
            clientListener.onClientDisConnect(channel);
        }
    }

    /**
     * 接收到一条消息
     *
     * @param message 消息
     */
    private void notifyReceiveMessage(DemoMessage message, Channel channel) {
        if (clientListener != null) {
            clientListener.onClientMessage(message, channel);
        }
    }

    private void writeLog(String info) {
        Log.d(TAG, info);
    }
}
