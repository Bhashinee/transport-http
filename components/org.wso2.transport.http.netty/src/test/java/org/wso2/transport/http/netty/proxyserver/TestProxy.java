/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.transport.http.netty.proxyserver;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.transport.http.netty.common.ProxyServerConfiguration;
import org.wso2.transport.http.netty.config.ListenerConfiguration;
import org.wso2.transport.http.netty.config.SenderConfiguration;
import org.wso2.transport.http.netty.contentaware.listeners.EchoMessageListener;
import org.wso2.transport.http.netty.contract.HttpClientConnector;
import org.wso2.transport.http.netty.contract.HttpResponseFuture;
import org.wso2.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.transport.http.netty.contract.ProxyServerConnector;
import org.wso2.transport.http.netty.contract.ServerConnector;
import org.wso2.transport.http.netty.contract.ServerConnectorFuture;
import org.wso2.transport.http.netty.contractimpl.DefaultHttpWsConnectorFactory;
import org.wso2.transport.http.netty.listener.ProxyServerConnectorFuture;
import org.wso2.transport.http.netty.message.HTTPCarbonMessage;
import org.wso2.transport.http.netty.message.HttpCarbonRequest;
import org.wso2.transport.http.netty.message.HttpMessageDataStreamer;
import org.wso2.transport.http.netty.util.HTTPConnectorListener;
import org.wso2.transport.http.netty.util.TestUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.wso2.transport.http.netty.common.Constants.HTTP_HOST;
import static org.wso2.transport.http.netty.common.Constants.HTTP_METHOD;
import static org.wso2.transport.http.netty.common.Constants.HTTP_PORT;
import static org.wso2.transport.http.netty.common.Constants.HTTP_POST_METHOD;
import static org.wso2.transport.http.netty.common.Constants.HTTP_SCHEME;
import static org.wso2.transport.http.netty.common.Constants.PROTOCOL;

/**
 * A test for connecting to a proxy server over HTTP.
 */
public class TestProxy {

    private static HttpWsConnectorFactory httpConnectorFactory;
    private static ProxyServerConnector proxyServerConnector;
    private static ServerConnector serverConnector;
    private static HttpClientConnector httpClientConnector;
    private static final Logger log = LoggerFactory.getLogger(TestProxy.class);

    @BeforeClass
    public void setup() throws InterruptedException {
        httpConnectorFactory = new DefaultHttpWsConnectorFactory();
        ListenerConfiguration listenerConfiguration = getListenerConfiguration();
        proxyServerConnector = httpConnectorFactory.createProxyServerConnector(listenerConfiguration);
        ProxyServerConnectorFuture future = proxyServerConnector.start();
        future.setProxyConnectorListener(new TestProxyConnectorListener());

        serverConnector = httpConnectorFactory
                .createServerConnector(TestUtil.getDefaultServerBootstrapConfig(), getServerListenerConfiguration());
        ServerConnectorFuture serverFuture = serverConnector.start();
        serverFuture.setHttpConnectorListener(new EchoMessageListener());
        httpClientConnector = httpConnectorFactory.createHttpClientConnector(new HashMap<>(), getSenderConfigs());
    }

    private static ListenerConfiguration getServerListenerConfiguration() {
        ListenerConfiguration listenerConfiguration = ListenerConfiguration.getDefault();
        listenerConfiguration.setPort(TestUtil.SERVER_PORT3);
        return listenerConfiguration;
    }

    private static ListenerConfiguration getListenerConfiguration() {
        ListenerConfiguration listenerConfiguration = ListenerConfiguration.getDefault();
        listenerConfiguration.setPort(9096);
        return listenerConfiguration;
    }

    private static SenderConfiguration getSenderConfigs() {
        SenderConfiguration senderConfiguration = new SenderConfiguration();
        ProxyServerConfiguration proxyServerConfiguration = null;
        try {
            proxyServerConfiguration = new ProxyServerConfiguration("localhost", 9096);
        } catch (UnknownHostException e) {
            TestUtil.handleException("Failed to resolve host", e);
        }
        senderConfiguration.setScheme(HTTP_SCHEME);
        senderConfiguration.setProxyServerConfiguration(proxyServerConfiguration);
        return senderConfiguration;
    }

    @Test(description = "Tests the scenario of a client connecting to a https server through proxy.")
    public void testHttpsProxyServer() {
        String testValue = "Test";
        ByteBuffer byteBuffer = ByteBuffer.wrap(testValue.getBytes(Charset.forName("UTF-8")));
        HTTPCarbonMessage msg = new HttpCarbonRequest(
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, ""));
        msg.setProperty(HTTP_METHOD, HttpMethod.POST.toString());
        msg.setProperty(HTTP_PORT, TestUtil.SERVER_PORT3);
        msg.setProperty(PROTOCOL, HTTP_SCHEME);
        msg.setProperty(HTTP_HOST, TestUtil.TEST_HOST);
        msg.setProperty(HTTP_METHOD, HTTP_POST_METHOD);
        msg.addHttpContent(new DefaultLastHttpContent(Unpooled.wrappedBuffer(byteBuffer)));

        try {
            CountDownLatch latch = new CountDownLatch(1);
            HTTPConnectorListener listener = new HTTPConnectorListener(latch);
            HttpResponseFuture responseFuture = httpClientConnector.send(msg);
            responseFuture.setHttpConnectorListener(listener);

            latch.await(5, TimeUnit.SECONDS);

            HTTPCarbonMessage response = listener.getHttpResponseMessage();
            assertNotNull(response);
            String result = new BufferedReader(
                    new InputStreamReader(new HttpMessageDataStreamer(response).getInputStream())).lines()
                    .collect(Collectors.joining("\n"));
            assertEquals(testValue, result);
        } catch (Exception e) {
            TestUtil.handleException("Exception occurred while running testProxyServer", e);
        }
    }

    @AfterClass
    public void cleanUp() {
        httpClientConnector.close();
        serverConnector.stop();
        proxyServerConnector.stop();
        try {
            httpConnectorFactory.shutdown();
        } catch (InterruptedException e) {
            log.warn("Unable to shut down the httpConnectorFactory in proxy test case");
        }
    }
}
