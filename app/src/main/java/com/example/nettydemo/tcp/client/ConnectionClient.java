package com.example.nettydemo.tcp.client;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.nettydemo.UIScheduler;
import com.example.nettydemo.tcp.DemoDecoder;
import com.example.nettydemo.tcp.DemoEncoder;
import com.example.nettydemo.tcp.DemoMessage;

import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
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
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * Created by banshan
 */
public class ConnectionClient {

    private static final String TAG = "netty";

    private static final long TIME_DELAY_CONNECT = 3000;

    private static final int RECONNECT_TIMEOUT = 3;
    /**
     * 是否已连接
     */
    private volatile boolean isConnected = false;
    /**
     * 正在连接
     */
    private volatile boolean connecting = false;
    /**
     * 主动断开状态，
     */
    private volatile boolean disConnected = false;
    /**
     * 是否连接失败
     */
    private volatile boolean connectFailed = false;
    /**
     * 负责管理EventLoop
     * 集成自 ExecutorService 可以理解为线程池
     */
    private EventLoopGroup mGroup;
    /**
     * 引导类
     * 配置线程池，IP地址，端口号，Channel，业务Handler
     */
    private Bootstrap mBootstrap;
    private Channel mChannel;

    private ChannelHandlerContext mChannelHandlerContext;

    /**
     * 解码
     * 读取数据，处理拆包，粘包，压缩
     */
    private DemoDecoder mDemoDecoder;
    /**
     * 编码
     */
    private DemoEncoder mDemoEncoder;

    private CustomHandler mCustomHandler;

    private IdleStateHandler mIdleStateHandler;

    IConnectionListener connectionListener;

    private ConnectionClient() {

    }

    private static final ConnectionClient engin = new ConnectionClient();

    private int reconnectIndex = 0;
    private final Runnable mReconnectTask = () -> {
        connect();
    };

    /**
     * 获取引擎
     */
    public static ConnectionClient getConnectionClient() {
        return engin;
    }


    /**
     * 初始化
     */
    public void init(@NonNull String ip, int port, IConnectionListener listener) {
        reconnectIndex = 0;
        this.connectionListener = listener;
        this.disConnected = false;
        try {
            if (isConnected) {
                notifyConnect();
                return;
            }
            if (mGroup != null) {
                mGroup.shutdownGracefully();
            }
            disConnect(false);
            //构建线程池
            mGroup = new NioEventLoopGroup();
            //构建引导程序
            mBootstrap = new Bootstrap();
            //设置EventGroup
            mBootstrap.group(mGroup);
            //设置Channel
            mBootstrap.channel(NioSocketChannel.class);
            //设置的好处是禁用Nagle算法。表示不延迟立即发送
            //这个算法试图减少TCP包的数量和结构性开销，将多个较小的包组合较大的包进行发送。
            //这个算法收TCP延迟确认影响，会导致相继两次向链接发送请求包。
            mBootstrap.option(ChannelOption.TCP_NODELAY, false);
            mBootstrap.option(ChannelOption.SO_KEEPALIVE, true);
            mBootstrap.remoteAddress(ip, port);
            mBootstrap.handler(new CustomChannelInitializer());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 连接
     */
    public void connect() {
        try {
            if (connectionListener != null) {
                connectionListener.onStartConnect();
            }
            if (mBootstrap == null || isConnected || connecting) {
                return;
            }
            connectFailed = false;
            connecting = true;
            final ChannelFuture channelFuture = mBootstrap.connect();
            channelFuture.addListener(new FutureListener() {
                @Override
                public void operationComplete(Future future) {
                    connecting = false;
                    final boolean isSuccess = future.isSuccess();
                    if (isSuccess) {
                        mChannel = channelFuture.channel();
                        disConnected = false;
                        isConnected = true;
                        connectFailed = false;
                    } else {
                        isConnected = false;
                        connectFailed = true;
                        reconnectIndex ++;
                        if (reconnectIndex > RECONNECT_TIMEOUT) {
                            notifyConnectFailure();
                            return;
                        }
                        UIScheduler.getUIScheduler().removeRunnable(mReconnectTask);
                        UIScheduler.getUIScheduler().postRunnableDelayed(mReconnectTask, TIME_DELAY_CONNECT);
                    }
                }
            });
        } catch (Exception e) {
            connecting = false;
            e.printStackTrace();
        }
    }

    /**
     * 写数据
     *
     * @param object Object
     */
    public void writeMessage(final Object object) {
        if (mChannel != null && mChannel.isOpen() && mChannel.isActive()) {
            mChannel.writeAndFlush(object).addListener(new GenericFutureListener<Future<? super Void>>() {
                @Override
                public void operationComplete(Future<? super Void> future) {
                    DemoMessage message = (DemoMessage) object;
                    writeLog("writeMessage success " + message.getBody());
                }
            });
        }
    }

    /**
     * 断开连接
     *
     * @param onPurpose true 主动
     */
    public void disConnect(boolean onPurpose) {
        disConnected = onPurpose;
        isConnected = false;
        connecting = false;
        try {
            if (mGroup != null) {
                mGroup.shutdownGracefully();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (mChannel != null) {
                mChannel.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (mChannelHandlerContext != null) {
                mChannelHandlerContext.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeHandler() {
        try {
            mChannel.pipeline().remove(mDemoDecoder);
            mChannel.pipeline().remove(mCustomHandler);
            mChannel.pipeline().remove(mDemoEncoder);
            mChannel.pipeline().remove(mIdleStateHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 是否连接状态
     *
     * @return true 连接
     */
    public boolean isConnected() {
        return isConnected && mChannel.isActive();
    }

    public class CustomChannelInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel socketChannel) {
            ChannelPipeline pipeline = socketChannel.pipeline();
            mDemoDecoder = new DemoDecoder();
            mDemoEncoder = new DemoEncoder();
            mCustomHandler = new CustomHandler();
            mIdleStateHandler = new IdleStateHandler(0, 90, 0, TimeUnit.SECONDS);
            pipeline.addLast(mIdleStateHandler);
            pipeline.addLast(mDemoDecoder);
            pipeline.addLast(mDemoEncoder);
            pipeline.addLast(mCustomHandler);
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

    public class CustomHandler extends SimpleChannelInboundHandler<DemoMessage> {

        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, final DemoMessage message) {
            //接收来自服务端的消息
            notifyReceiveMessage(message);
        }

        /**
         * 连接成功
         */
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            //链接成功
            isConnected = true;
            notifyConnect();
            mChannelHandlerContext = ctx;
        }

        /**
         * 链接失败
         */
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            //链接失败
            inActive(ctx);
            isConnected = false;
            connecting = false;
            notifyDisConnect();
            if (!disConnected) {
                notifyReInit();
            }
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
            isConnected = false;
            connecting = false;
            notifyDisConnect();
            notifyReInit();
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            super.handlerAdded(ctx);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            super.userEventTriggered(ctx, evt);
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent e = (IdleStateEvent) evt;
                switch (e.state()) {
                    case WRITER_IDLE:
                        // TODO: 2024/6/12 心跳包
                        break;
                    default:
                        break;
                }
            }
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            super.channelWritabilityChanged(ctx);
        }
    }

    private void inActive(ChannelHandlerContext ctx) {

        removeHandler();
        try {
            Channel channel = ctx.channel();
            channel.close();
            ctx.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyConnectFailure() {
        runOnUi(() -> {
            if (connectionListener != null) {
                connectionListener.onConnectFailure();
            }
        });
    }

    /**
     * 通过建立TCP连接
     */
    private void notifyConnect() {
        runOnUi(() -> {
            reconnectIndex = 0;
            if (connectionListener != null) {
                connectionListener.onConnect();
            }
        });
    }

    /**
     * 通知TCP连接断开
     */
    private void notifyDisConnect() {
        runOnUi(() -> {
            reconnectIndex = 0;
            if (connectionListener != null) {
                connectionListener.onDisConnect(disConnected);
            }
        });
    }

    /**
     * 通过主进程接收到一条消息
     *
     * @param message 消息
     */
    private void notifyReceiveMessage(DemoMessage message) {
        runOnUi(() -> {
            if (connectionListener != null) {
                connectionListener.onReceiveMessage(message);
            }
        });
    }

    /**
     * 通知重新连接
     */
    private void notifyReInit() {
        runOnUiDelay(() -> {
            if (connectionListener != null) {
                connectionListener.onReInit();
            }
        }, TIME_DELAY_CONNECT);

    }

    private void runOnUi(Runnable runnable) {
        UIScheduler.getUIScheduler().postRunnable(runnable);
    }

    private void runOnUiDelay(Runnable runnable, long delay) {
        UIScheduler.getUIScheduler().postRunnableDelayed(runnable, delay);
    }

    private void writeLog(String info) {
        Log.d(TAG, info);
    }
}
