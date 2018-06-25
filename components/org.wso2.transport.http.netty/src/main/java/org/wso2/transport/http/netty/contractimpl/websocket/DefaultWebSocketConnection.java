package org.wso2.transport.http.netty.contractimpl.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.contract.websocket.WebSocketConnection;
import org.wso2.transport.http.netty.contract.websocket.WebSocketFrameType;
import org.wso2.transport.http.netty.listener.MessageQueueHandler;

import java.nio.ByteBuffer;

/**
 * Default implementation of {@link WebSocketConnection}.
 */
public class DefaultWebSocketConnection implements WebSocketConnection {

    private final ChannelHandlerContext ctx;
    private final WebSocketInboundFrameHandler frameHandler;
    private MessageQueueHandler messageQueueHandler;
    private final boolean secure;
    private WebSocketFrameType continuationFrameType;
    private boolean closeFrameSent;
    private int closeInitiatedStatusCode;
    private String id;
    private String negotiatedSubProtocol;

    public DefaultWebSocketConnection(ChannelHandlerContext ctx, WebSocketInboundFrameHandler frameHandler,
                                      MessageQueueHandler messageQueueHandler, boolean secure,
                                      String negotiatedSubProtocol) {
        this.ctx = ctx;
        this.id = WebSocketUtil.getChannelId(ctx);
        this.frameHandler = frameHandler;
        this.messageQueueHandler = messageQueueHandler;
        this.secure = secure;
        this.negotiatedSubProtocol = negotiatedSubProtocol;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public boolean isOpen() {
        return this.ctx.channel().isOpen();
    }

    @Override
    public boolean isSecure() {
        return this.secure;
    }

    @Override
    public String getNegotiatedSubProtocol() {
        return negotiatedSubProtocol;
    }

    @Override
    public void readNextFrame() {
        messageQueueHandler.readNextFrame();
    }

    @Override
    public void startReadingFrames() {
        ctx.pipeline().remove(Constants.MESSAGE_QUEUE_HANDLER);
        ctx.channel().config().setAutoRead(true);
    }

    @Override
    public void stopReadingFrames() {
        ctx.channel().config().setAutoRead(false);
        ctx.pipeline().addBefore(Constants.WEBSOCKET_FRAME_HANDLER, Constants.MESSAGE_QUEUE_HANDLER,
                                 messageQueueHandler = new MessageQueueHandler());
    }

    @Override
    public ChannelFuture pushText(String text) {
        return pushText(text, true);
    }

    @Override
    public ChannelFuture pushText(String text, boolean finalFrame) {
        if (continuationFrameType == WebSocketFrameType.BINARY) {
            throw new IllegalStateException("Cannot interrupt WebSocket binary frame continuation");
        }
        if (closeFrameSent) {
            throw new IllegalStateException("Already sent close frame. Cannot push text data!");
        }
        if (continuationFrameType != null) {
            if (finalFrame) {
                continuationFrameType = null;
            }
            return ctx.writeAndFlush(new ContinuationWebSocketFrame(finalFrame, 0, text));
        }
        if (!finalFrame) {
            continuationFrameType = WebSocketFrameType.TEXT;
        }
        return ctx.writeAndFlush(new TextWebSocketFrame(finalFrame, 0, text));
    }

    @Override
    public ChannelFuture pushBinary(ByteBuffer data) {
        return pushBinary(data, true);
    }

    @Override
    public ChannelFuture pushBinary(ByteBuffer data, boolean finalFrame) {
        if (continuationFrameType == WebSocketFrameType.TEXT) {
            throw new IllegalStateException("Cannot interrupt WebSocket text frame continuation");
        }
        if (closeFrameSent) {
            throw new IllegalStateException("Already sent close frame. Cannot push binary data!");
        }
        if (continuationFrameType != null) {
            if (finalFrame) {
                continuationFrameType = null;
            }
            return ctx.writeAndFlush(new ContinuationWebSocketFrame(finalFrame, 0, getNettyBuf(data)));
        }
        if (!finalFrame) {
            continuationFrameType = WebSocketFrameType.BINARY;
        }
        return ctx.writeAndFlush(new BinaryWebSocketFrame(finalFrame, 0, getNettyBuf(data)));
    }

    @Override
    public ChannelFuture ping(ByteBuffer data) {
        return ctx.writeAndFlush(new PingWebSocketFrame(getNettyBuf(data)));
    }

    @Override
    public ChannelFuture pong(ByteBuffer data) {
        return ctx.writeAndFlush(new PongWebSocketFrame(getNettyBuf(data)));
    }

    @Override
    public ChannelFuture initiateConnectionClosure(int statusCode, String reason) {
        if (closeFrameSent) {
            throw new IllegalStateException("Already sent close frame. Cannot send close frame again!");
        }
        closeFrameSent = true;
        closeInitiatedStatusCode = statusCode;
        ChannelPromise closePromise = ctx.newPromise();
        ctx.writeAndFlush(new CloseWebSocketFrame(statusCode, reason)).addListener(future -> {
            frameHandler.setClosePromise(closePromise);
            Throwable cause = future.cause();
            if (!future.isSuccess() && cause != null) {
                ctx.close().addListener(closeFuture -> closePromise.setFailure(cause));
            }
        });
        return closePromise;
    }

    @Override
    public ChannelFuture finishConnectionClosure(int statusCode, String reason) {
        if (!frameHandler.isCloseFrameReceived()) {
            throw new IllegalStateException("Cannot finish a connection closure without receiving a close frame");
        }
        ChannelPromise channelPromise = ctx.newPromise();
        ctx.writeAndFlush(new CloseWebSocketFrame(statusCode, reason)).addListener(future -> {
            Throwable cause = future.cause();
            if (!future.isSuccess() && cause != null) {
                ctx.close().addListener(closeFuture -> channelPromise.setFailure(cause));
                return;
            }
            ctx.close().addListener(closeFuture -> channelPromise.setSuccess());
        });
        return channelPromise;
    }

    @Override
    public ChannelFuture terminateConnection() {
        frameHandler.setCloseInitialized(true);
        return ctx.close();
    }

    @Override
    public ChannelFuture terminateConnection(int statusCode, String reason) {
        ChannelPromise closePromise = ctx.newPromise();
        ctx.writeAndFlush(new CloseWebSocketFrame(statusCode, reason)).addListener(writeFuture -> {
            frameHandler.setCloseInitialized(true);
            Throwable writeCause = writeFuture.cause();
            if (writeFuture.isSuccess() && writeCause != null) {
                closePromise.setFailure(writeCause);
                ctx.close();
                return;
            }
            ctx.close().addListener(closeFuture -> {
                Throwable closeCause = closeFuture.cause();
                if (!closeFuture.isSuccess() && closeCause != null) {
                    closePromise.setFailure(closeCause);
                } else {
                    closePromise.setSuccess();
                }
            });

        });
        return closePromise;
    }

    int getCloseInitiatedStatusCode() {
        return this.closeInitiatedStatusCode;
    }

    private ByteBuf getNettyBuf(ByteBuffer buffer) {
        return Unpooled.wrappedBuffer(buffer);
    }
}
