package org.example.wechat.common.util.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Netty WebSocket 启动器
 */
public class NettyWebSocketStarter {

    private static final Logger logger = LoggerFactory.getLogger(NettyWebSocketStarter.class);

    private static final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private static final EventLoopGroup workGroup = new NioEventLoopGroup();

    public static void main(String[] args) {
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.DEBUG))
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) throws Exception {
                            ChannelPipeline pipeline = channel.pipeline();

                            // HTTP 编解码器
                            pipeline.addLast(new HttpServerCodec());

                            // 聚合 HTTP 请求为 FullHttpRequest
                            pipeline.addLast(new HttpObjectAggregator(64 * 1024));

                            // 心跳检测：读超时 60s，写超时 30s
                            pipeline.addLast(new IdleStateHandler(60, 30, 0, TimeUnit.SECONDS));

                            // 自定义心跳处理器
                            pipeline.addLast(new HandlerHeartBeat());

                            // WebSocket 协议升级，路径为 /ws
                            pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));

                            // 自定义 WebSocket 消息处理器
                            pipeline.addLast(new HandlerWebSocket());
                        }
                    });

            ChannelFuture channelFuture = serverBootstrap.bind(5051).sync();
            logger.info("Netty WebSocket 启动成功，端口：5051");
            channelFuture.channel().closeFuture().sync();

        } catch (Exception e) {
            logger.error("启动 Netty 失败", e);
        } finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }
    }
}