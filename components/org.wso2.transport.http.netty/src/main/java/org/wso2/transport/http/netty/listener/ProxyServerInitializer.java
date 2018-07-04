/*
 *  Copyright (c) 2018 WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.wso2.transport.http.netty.listener;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.config.RequestSizeValidationConfig;
import org.wso2.transport.http.netty.contract.ServerConnectorFuture;

/**
 * Channel initializer for proxy server.
 */
public class ProxyServerInitializer extends ChannelInitializer<SocketChannel> {
    private static final Logger log = LoggerFactory.getLogger(ProxyServerInitializer.class);
    private RequestSizeValidationConfig reqSizeValidationConfig;
    private String proxyUserName = null;
    private String proxyPassword = null;
    private String proxyPseudonym = null;
    private ChannelGroup allChannels;
    private String interfaceId;
    private ProxyServerConnectorFuture proxyServerConnectorFuture;


    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Initializing source channel pipeline");
        }

        ChannelPipeline proxyPipeline = ch.pipeline();
        proxyPipeline.addLast(Constants.HTTP_DECODER, new HttpRequestDecoder(reqSizeValidationConfig.getMaxUriLength(),
                reqSizeValidationConfig.getMaxHeaderSize(), reqSizeValidationConfig.getMaxChunkSize()));
        if (proxyUserName != null && !proxyUserName.isEmpty()) {
            proxyPipeline
                    .addLast("ProxyAuthorizationHandler", new ProxyAuthorizationHandler(proxyUserName, proxyPassword));
        }
        proxyPipeline.addLast("ProxyInboundHandler", new ProxyServerInboundHandler(null, proxyServerConnectorFuture));

    }

    void setProxyServerUserName(String userName) {
        this.proxyUserName = proxyUserName;
    }

    void setProxyServerPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    void setProxyPseudonym(String proxyPseudonym) {
        this.proxyPseudonym = proxyPseudonym;
    }

    void setAllChannels(ChannelGroup allChannels) {
        this.allChannels = allChannels;
    }

    void setInterfaceId(String interfaceId) {
        this.interfaceId = interfaceId;
    }

    public void setProxyServerConnectorFuture(ProxyServerConnectorFuture serverConnectorFuture) {
        this.proxyServerConnectorFuture = serverConnectorFuture;
    }

    void setReqSizeValidationConfig(RequestSizeValidationConfig reqSizeValidationConfig) {
        this.reqSizeValidationConfig = reqSizeValidationConfig;
    }
}
