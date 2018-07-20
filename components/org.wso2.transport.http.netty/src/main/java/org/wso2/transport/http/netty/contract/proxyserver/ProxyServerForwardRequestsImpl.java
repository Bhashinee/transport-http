package org.wso2.transport.http.netty.contract.proxyserver;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.contract.HttpResponseFuture;
import org.wso2.transport.http.netty.listener.ProxyServerOutboundHandler;
import org.wso2.transport.http.netty.message.HTTPCarbonMessage;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.wso2.transport.http.netty.common.Util.createHttpResponse;
import static org.wso2.transport.http.netty.common.Util.isLastHttpContent;
import static org.wso2.transport.http.netty.common.Util.setupChunkedRequest;

/**
 * This class is for forward requests to the backend.
 */
public class ProxyServerForwardRequestsImpl implements ProxyServerForwardRequests {
    private ChannelHandlerContext ctx;
    private Object msg;
    private Channel inboundChannel;
    private HttpRequest inboundRequest;
    private Channel outboundChannel = null;
    private String proxyPseudonym;
    private HTTPCarbonMessage inboundRequestMsg;
    private boolean headerWritten = false;
    private long contentLength = 0;
    private AtomicInteger writeCounter = new AtomicInteger(0);
    private static final Logger log = LoggerFactory.getLogger(ProxyServerForwardRequestsImpl.class);

    public ProxyServerForwardRequestsImpl(ChannelHandlerContext ctx, Object msg, Channel inboundChannel,
            HttpRequest inboundRequest, HTTPCarbonMessage inboundRequestMsg, Channel outboundChannel,
            String proxyPseudonym) {
        this.ctx = ctx;
        this.msg = msg;
        this.inboundChannel = inboundChannel;
        this.inboundRequest = inboundRequest;
        this.outboundChannel = outboundChannel;
        this.proxyPseudonym = proxyPseudonym;
        this.inboundRequestMsg = inboundRequestMsg;
    }

    public void forwardRequests() throws MalformedURLException, InterruptedException, UnknownHostException {
        InetSocketAddress reqSocket = resolveInetSocketAddress(inboundRequest);
        String host = reqSocket.getHostName();
        int port = reqSocket.getPort();

        OioEventLoopGroup group = new OioEventLoopGroup(1);
        Bootstrap clientBootstrap = new Bootstrap();
        clientBootstrap.group(group).channel(OioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .remoteAddress(new InetSocketAddress(host, port))
                .handler(new ProxyServerOutboundHandler(inboundChannel));
        ChannelFuture channelFuture = clientBootstrap.connect(host, port).sync();
        outboundChannel = channelFuture.channel();

        if (inboundRequest.method().equals(HttpMethod.CONNECT)) {
            // Once the connection is successful, send 200 OK to client.
            if (outboundChannel.isActive()) {
                ctx.channel().pipeline().remove(Constants.HTTP_DECODER);
                sendOk(inboundChannel, inboundRequest.protocolVersion());
                ctx.channel().pipeline().remove(Constants.HTTP_ENCODER);
                ctx.channel().pipeline().fireChannelActive();
            }
        } else {
            // This else block is for handling non https requests. Once the connection is successful
            // forward the incoming messages to backend.
            inboundRequest.setUri(new URL(inboundRequest.uri()).getPath());
            inboundRequest.headers().set(HttpHeaderNames.VIA, getViaHeader(inboundRequest));
            inboundRequest.headers().remove(HttpHeaderNames.PROXY_AUTHORIZATION);
            ByteBuf encodedRequest = getByteBuf(msg);
            outboundChannel.writeAndFlush(encodedRequest).addListener((ChannelFutureListener) chFuture -> {
                if (!chFuture.isSuccess()) {
                    log.error("Failed to write to the backend via proxy.");
                    chFuture.channel().close();
                }
                if (chFuture.isSuccess()) {
                    removeOtherHandlers(ctx);
                    ctx.channel().pipeline().fireChannelActive();
                    log.debug("Successfully wrote http headers to the backend via proxy");
                    writeContentToBackend(inboundRequestMsg.getHttpContent());
                }
            });
        }
    }

    public void sendResponseAsBlocked(HTTPCarbonMessage httpCarbonMessage) {
//        inboundChannel.writeAndFlush(httpCarbonMessage.getHttpContent()).addListener((ChannelFutureListener)
        // chFuture -> {
//            if (!chFuture.isSuccess()) {
//                log.error("------------ Failed ------------");
//                chFuture.channel().close();
//            }
//            if (chFuture.isSuccess()) {
//                log.debug("Successfully sent the response");
//            }
//        });
        boolean keepAlive = true;
        httpCarbonMessage.getHttpContentAsync().setMessageListener(httpContent ->
                inboundChannel.eventLoop().execute(() -> {
                    try {
                        writeOutboundResponse(httpCarbonMessage, keepAlive, httpContent);
                    } catch (Exception exception) {
                        String errorMsg = "Failed to send the outbound response : "
                                + exception.getMessage().toLowerCase(Locale.ENGLISH);
                        log.error(errorMsg, exception);
                        inboundRequestMsg.getHttpOutboundRespStatusFuture().notifyHttpListener(exception);
                    }
                }));
    }

    private void writeOutboundResponse(HTTPCarbonMessage outboundResponseMsg, boolean keepAlive,
            HttpContent httpContent) {

        ChannelFuture outboundChannelFuture;
        HttpResponseFuture outboundRespStatusFuture = inboundRequestMsg.getHttpOutboundRespStatusFuture();
        if (isLastHttpContent(httpContent)) {
            if (!headerWritten) {
                writeHeaders(outboundResponseMsg, keepAlive, outboundRespStatusFuture);
                outboundChannelFuture = writeOutboundResponseBody(httpContent);
                outboundChannelFuture.addListener((ChannelFutureListener) chFuture -> {
                    if (!chFuture.isSuccess()) {
                        log.error("------------ Failed Content ------------");
                        chFuture.channel().close();
                    }
                    if (chFuture.isSuccess()) {
                        log.error("Successfully sent the Content");
                    }
                });
            }
        }
    }

    public ChannelFuture writeOutboundResponseBody(HttpContent lastHttpContent) {
       // HttpResponseFuture outRespStatusFuture = inboundRequestMsg.getHttpOutboundRespStatusFuture();
        incrementWriteCount(writeCounter);
        ChannelFuture outboundChannelFuture = ctx.writeAndFlush(lastHttpContent);
        return outboundChannelFuture;
    }

    private void writeHeaders(HTTPCarbonMessage outboundResponseMsg, boolean keepAlive,
            HttpResponseFuture outboundRespStatusFuture) {
        setupChunkedRequest(outboundResponseMsg);
        incrementWriteCount(writeCounter);
        ChannelFuture outboundHeaderFuture = writeOutboundResponseHeaders(outboundResponseMsg, keepAlive);
        outboundHeaderFuture.addListener((ChannelFutureListener) chFuture -> {
            if (!chFuture.isSuccess()) {
                log.error("------------ Failed ------------");
                chFuture.channel().close();
            }
            if (chFuture.isSuccess()) {
                log.error("Successfully sent the headers");
            }
        });
        //addResponseWriteFailureListener(outboundRespStatusFuture, outboundHeaderFuture, writeCounter);
    }

    private int incrementWriteCount(AtomicInteger writeCounter) {
        return writeCounter.incrementAndGet();
    }

    private ChannelFuture writeOutboundResponseHeaders(HTTPCarbonMessage outboundResponseMsg, boolean keepAlive) {
        HttpResponse response = createHttpResponse(outboundResponseMsg,
                inboundRequest.protocolVersion().majorVersion() + "." + inboundRequest.protocolVersion().minorVersion(),
                "ballerina", keepAlive);
        headerWritten = true;
        return ctx.write(response);
    }

    /**
     * This function is for generating Via header.
     *
     * @param inboundRequest http inbound request
     * @return via header
     * @throws UnknownHostException If an error occurs while getting the host name
     */
    private String getViaHeader(HttpRequest inboundRequest) throws UnknownHostException {
        String viaHeader;
        String receivedBy;
        viaHeader = inboundRequest.headers().get(HttpHeaderNames.VIA);
        if (proxyPseudonym != null) {
            receivedBy = proxyPseudonym;
        } else {
            receivedBy = InetAddress.getLocalHost().getHostName();
        }
        String httpVersion =
                inboundRequest.protocolVersion().majorVersion() + "." + inboundRequest.protocolVersion()
                        .minorVersion();
        if (viaHeader == null) {
            viaHeader = httpVersion + " " + receivedBy;
        } else {
            viaHeader = viaHeader.concat(",").concat(httpVersion + " " + receivedBy);
        }
        return viaHeader;
    }

    private InetSocketAddress resolveInetSocketAddress(HttpRequest inboundRequest) throws MalformedURLException {
        InetSocketAddress address;
        if (HttpMethod.CONNECT.equals(inboundRequest.method())) {
            String parts[] = inboundRequest.uri().split(Constants.COLON);
            address = new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
        } else {
            URL url = new URL(inboundRequest.uri());
            address = new InetSocketAddress(url.getHost(), url.getPort());
        }
        return address;
    }

    private ByteBuf getByteBuf(Object msg) {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestEncoder());
        channel.writeOutbound(msg);
        return channel.readOutbound();
    }

    private void writeContentToBackend(HttpContent msg) {
        outboundChannel.writeAndFlush(msg.content()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.debug("Wrote the content to the backend via proxy.");
            }
            if (!future.isSuccess()) {
                log.error("Could not write the content to the backend via proxy.");
                future.channel().close();
            }
        });
    }

    /**
     * Removing other handlers except proxyServerInbound handler and default channel pipeline handler.
     *
     * @param ctx channel context
     */
    private void removeOtherHandlers(ChannelHandlerContext ctx) {
//        for (String handler : ctx.channel().pipeline().names()) {
//            if (!(PROXY_SERVER_INBOUND_HANDLER.equals(handler) || handler.contains(DEFAULT_CHANNEL_PIPELINE))) {
//                ctx.channel().pipeline().remove(handler);
//            }
//        }
        ctx.channel().pipeline().remove(Constants.HTTP_ENCODER);
        ctx.channel().pipeline().remove(Constants.HTTP_DECODER);
    }

    public Channel getOutboundChannel() {
        return outboundChannel;
    }

    @Override
    public HTTPCarbonMessage getInboundRequestMsg() {
        return inboundRequestMsg;
    }

    /**
     * Send 200 OK message to the client once the tcp connection is successfully established
     * between proxy server and backend server.
     *
     * @param channel channel
     * @param httpVersion http version
     */
    private static void sendOk(Channel channel, HttpVersion httpVersion) {
        FullHttpResponse response = new DefaultFullHttpResponse(httpVersion, HttpResponseStatus.OK);
        channel.writeAndFlush(response);
    }
}
