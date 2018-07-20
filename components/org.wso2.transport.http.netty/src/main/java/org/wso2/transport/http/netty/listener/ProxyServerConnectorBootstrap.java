/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.transport.http.netty.listener;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.common.Util;
import org.wso2.transport.http.netty.config.RequestSizeValidationConfig;
import org.wso2.transport.http.netty.contract.ProxyServerConnector;
import org.wso2.transport.http.netty.contract.ServerConnectorException;
import org.wso2.transport.http.netty.contractimpl.ProxyServerConnectorFutureImpl;

import java.net.InetSocketAddress;

/**
 * Bootstrap for proxy server.
 */
public class ProxyServerConnectorBootstrap {
    private static final Logger log = LoggerFactory.getLogger(ProxyServerConnectorBootstrap.class);
    private ServerBootstrap serverBootstrap;
    private ChannelGroup allChannels;
    private boolean initialized = false;
    private ProxyServerInitializer proxyServerInitializer;

    public ProxyServerConnectorBootstrap(ChannelGroup allChannels) {
        serverBootstrap = new ServerBootstrap();
        proxyServerInitializer = new ProxyServerInitializer();
        proxyServerInitializer.setAllChannels(allChannels);
        serverBootstrap.childHandler(proxyServerInitializer);
        this.allChannels = allChannels;
        initialized = true;
    }

    private ChannelFuture bindInterface(HTTPServerConnector serverConnector) {
        if (!initialized) {
            log.error("ProxyServerConnectorBootstrap is not initialized");
            return null;
        }
        return serverBootstrap.bind(new InetSocketAddress(serverConnector.getHost(), serverConnector.getPort()));
    }

    private boolean unBindInterface(HTTPServerConnector serverConnector) throws InterruptedException {
        if (!initialized) {
            log.error("ProxyServerConnectorBootstrap is not initialized");
            return false;
        }
        //Remove cached channels and close them.
        ChannelFuture future = serverConnector.getChannelFuture();
        if (future != null) {
            ChannelFuture channelFuture = future.channel().close();
            channelFuture.sync();
            log.info("HttpConnectorListener stopped listening on host " + serverConnector.getHost()
                    + " and port " + serverConnector.getPort());
            return true;
        }
        return false;
    }

    class HTTPServerConnector implements ProxyServerConnector {

        private final Logger log = LoggerFactory.getLogger(ProxyServerConnectorBootstrap.HTTPServerConnector.class);
        private ChannelFuture channelFuture;
        private ProxyServerConnectorFuture proxyServerConnectorFuture;
        private ProxyServerConnectorBootstrap proxyServerConnectorBootstrap;
        private String host;
        private int port;
        private String connectorID;

        HTTPServerConnector(String id, ProxyServerConnectorBootstrap proxyServerConnectorBootstrap, String host,
                int port) {
            this.proxyServerConnectorBootstrap = proxyServerConnectorBootstrap;
            this.host = host;
            this.port = port;
            this.connectorID = id;
            proxyServerInitializer.setInterfaceId(id);
        }

        @Override
        public ProxyServerConnectorFuture start() {
            channelFuture = bindInterface(this);
            proxyServerConnectorFuture = new ProxyServerConnectorFutureImpl(channelFuture, allChannels);
            channelFuture.addListener(channelFuture -> {
                if (channelFuture.isSuccess()) {
                    log.info("Proxy Interface starting on host " + this.getHost() + " and port " + this.getPort());
                    proxyServerConnectorFuture.notifyPortBindingEvent(this.connectorID);
                } else {
                    proxyServerConnectorFuture.notifyPortBindingError(channelFuture.cause());
                }
            });
            proxyServerInitializer.setProxyServerConnectorFuture(proxyServerConnectorFuture);
            return proxyServerConnectorFuture;
        }

        @Override
        public boolean stop() {
            boolean connectorStopped = false;
            try {
                connectorStopped = proxyServerConnectorBootstrap.unBindInterface(this);
                if (connectorStopped) {
                    proxyServerConnectorFuture.notifyPortUnbindingEvent(this.connectorID);
                }
            } catch (InterruptedException e) {
                log.error("Couldn't close the port", e);
                return false;
            } catch (ServerConnectorException e) {
                log.error("Error in notifying life cycle event listener", e);
            }

            return connectorStopped;
        }

        @Override
        public String getConnectorID() {
            return this.connectorID;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }
        private ChannelFuture getChannelFuture() {
            return channelFuture;
        }
    }
    public HTTPServerConnector getServerConnector(String host, int port) {
        String serverConnectorId = Util.createServerConnectorID(host, port);
        return new ProxyServerConnectorBootstrap.HTTPServerConnector(serverConnectorId, this, host, port);
    }

    public void addThreadPools(EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class);
    }

    public void addHeaderAndEntitySizeValidation(RequestSizeValidationConfig requestSizeValidationConfig) {
        proxyServerInitializer.setReqSizeValidationConfig(requestSizeValidationConfig);
    }

    public void addSocketConfiguration(ServerBootstrapConfiguration serverBootstrapConfiguration) {
        // Set other serverBootstrap parameters
        serverBootstrap.option(ChannelOption.SO_BACKLOG, serverBootstrapConfiguration.getSoBackLog());
        serverBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, serverBootstrapConfiguration.getConnectTimeOut());
        serverBootstrap.option(ChannelOption.SO_RCVBUF, serverBootstrapConfiguration.getReceiveBufferSize());

        serverBootstrap.childOption(ChannelOption.TCP_NODELAY, serverBootstrapConfiguration.isTcpNoDelay());
        serverBootstrap.childOption(ChannelOption.SO_RCVBUF, serverBootstrapConfiguration.getReceiveBufferSize());
        serverBootstrap.childOption(ChannelOption.SO_SNDBUF, serverBootstrapConfiguration.getSendBufferSize());

        if (log.isDebugEnabled()) {
            log.debug(String.format("Netty Server Socket BACKLOG %d", serverBootstrapConfiguration.getSoBackLog()));
            log.debug(String.format("Netty Server Socket TCP_NODELAY %s", serverBootstrapConfiguration.isTcpNoDelay()));
            log.debug(String.format("Netty Server Socket CONNECT_TIMEOUT_MILLIS %d",
                    serverBootstrapConfiguration.getConnectTimeOut()));
            log.debug(String.format("Netty Server Socket SO_RCVBUF %d",
                    serverBootstrapConfiguration.getReceiveBufferSize()));
            log.debug(String.format("Netty Server Socket SO_SNDBUF %d",
                    serverBootstrapConfiguration.getSendBufferSize()));
        }
    }
}
