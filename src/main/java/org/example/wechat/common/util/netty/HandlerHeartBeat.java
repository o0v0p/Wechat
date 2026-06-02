package org.example.wechat.common.util.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty 心跳处理器
 *
 * 原代码问题：
 * 1. READER_IDLE 超时时发送 TextWebSocketFrame("heart") ——
 *    这是在客户端长时间没发数据时，服务端主动发文本帧没有意义，
 *    正确行为是：服务端写空闲时主动发 Ping，读空闲超时则关闭连接。
 * 2. 没有 exceptionCaught，异常会向上传播导致 pipeline 异常。
 */
public class HandlerHeartBeat extends ChannelDuplexHandler {

    private static final Logger logger = LoggerFactory.getLogger(HandlerHeartBeat.class);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (!(evt instanceof IdleStateEvent)) {
            super.userEventTriggered(ctx, evt);
            return;
        }

        IdleStateEvent event = (IdleStateEvent) evt;

        if (event.state() == IdleState.READER_IDLE) {
            // 修复：读超时说明客户端已断开或网络异常，直接关闭连接
            // 原代码在这里发 TextWebSocketFrame("heart") 是错误的——
            // 客户端都不响应了，发帧没有意义
            logger.warn("读超时（客户端无响应），关闭连接: channel={}", ctx.channel().remoteAddress());
            ctx.close();

        } else if (event.state() == IdleState.WRITER_IDLE) {
            // 修复：写空闲时发 WebSocket 标准 Ping 帧（RFC 6455）
            // 原代码用 TextWebSocketFrame 发心跳是应用层自定义协议，
            // 标准做法是用 PingWebSocketFrame，客户端会自动回 Pong
            logger.debug("写空闲，发送 Ping: channel={}", ctx.channel().remoteAddress());
            ctx.writeAndFlush(new PingWebSocketFrame(
                    Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4})
            ));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Netty 心跳处理器异常，关闭连接: channel={}, error={}",
                ctx.channel().remoteAddress(), cause.getMessage());
        ctx.close();
    }
}