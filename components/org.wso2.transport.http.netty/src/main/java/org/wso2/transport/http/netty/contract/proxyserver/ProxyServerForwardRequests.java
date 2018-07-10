package org.wso2.transport.http.netty.contract.proxyserver;

import org.wso2.transport.http.netty.message.HTTPCarbonMessage;

import java.net.MalformedURLException;
import java.net.UnknownHostException;

/**
 * Created by bhashinee on 7/4/18.
 */
public interface ProxyServerForwardRequests {
    void forwardRequests() throws MalformedURLException, InterruptedException, UnknownHostException;
    HTTPCarbonMessage getInboundRequestMsg();
    void sendResponseAsBlocked(HTTPCarbonMessage httpCarbonMessage);
}

