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

package org.wso2.transport.http.netty.config;

/**
 * Configurations for proxy listener.
 */
public class ProxyListenerConfiguration {
    private String host = "0.0.0.0";
    private int port = 9090;
    private String proxyServerUserName;
    private String proxyServerPassword;
    private String proxyServerPseudonym;
    private long socketIdleTimeout;
    private String serverHeader = "wso2-http-transport";
    private boolean httpTraceLogEnabled;
    private boolean httpAccessLogEnabled;
    private RequestSizeValidationConfig requestSizeValidationConfig = new RequestSizeValidationConfig();

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setProxyServerUserName(String proxyUserName) {
        this.proxyServerUserName = proxyUserName;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyServerPassword = proxyPassword;
    }

    public void setProxyPseudonym(String proxyPseudonym) {
        this.proxyServerPseudonym = proxyPseudonym;
    }

    public int getPort() {
        return port;
    }

    public String getProxyServerUserName() {
        return proxyServerUserName;
    }

    public String getProxyPassword() {
        return proxyServerPassword;
    }

    public String getProxyPseudonym() {
        return proxyServerPseudonym;
    }

    public RequestSizeValidationConfig getRequestSizeValidationConfig() {
        return requestSizeValidationConfig;
    }

    public void setRequestSizeValidationConfig(RequestSizeValidationConfig requestSizeValidationConfig) {
        this.requestSizeValidationConfig = requestSizeValidationConfig;
    }

    public long getSocketIdleTimeout() {
        return socketIdleTimeout;
    }

    public void setSocketIdleTimeout(int socketIdleTimeout) {
        this.socketIdleTimeout = socketIdleTimeout;
    }

    public void setServerHeader(String serverHeader) {
        this.serverHeader = serverHeader;
    }

    public String getServerHeader() {
        return serverHeader;
    }

    public void setHttpTraceLogEnabled(boolean httpTraceLogEnabled) {
        this.httpTraceLogEnabled = httpTraceLogEnabled;
    }
    public boolean isHttpTraceLogEnabled() {
        return httpTraceLogEnabled;
    }

    public void setHttpAccessLogEnabled(boolean httpAccessLogEnabled) {
        this.httpAccessLogEnabled = httpAccessLogEnabled;
    }

    public boolean isHttpAccessLogEnabled() {
        return httpAccessLogEnabled;
    }

}
