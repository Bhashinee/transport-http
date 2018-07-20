package org.wso2.transport.http.netty.proxyserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.ProxyConnectorListener;
import org.wso2.transport.http.netty.contract.proxyserver.ProxyServerForwardRequests;

import java.net.MalformedURLException;
import java.net.UnknownHostException;

/**
 * BallerinaProxyConnectorListener for test cases.
 */
public class TestProxyConnectorListener implements ProxyConnectorListener {
    private static final Logger log = LoggerFactory.getLogger(TestProxyConnectorListener.class);

    @Override
    public void onError(Throwable throwable) {
        log.error("Error in http proxy endpoint", throwable);
    }

    @Override
    public void onMessage(ProxyServerForwardRequests proxyServerForwardRequests) {
        try {
            proxyServerForwardRequests.forwardRequests();
        } catch (MalformedURLException | InterruptedException | UnknownHostException e) {
            log.error("Unable to forward requests to the backend");
        }
    }
}
