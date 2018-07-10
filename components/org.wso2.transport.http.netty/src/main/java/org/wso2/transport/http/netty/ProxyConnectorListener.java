package org.wso2.transport.http.netty;

import org.wso2.transport.http.netty.contract.proxyserver.ProxyServerForwardRequests;
import org.wso2.transport.http.netty.message.HTTPCarbonMessage;

/**
 * Proxy connector listener interface.
 */
public interface ProxyConnectorListener {

    /**
     * Each error event triggered by connector ends up here.
     * @param throwable contains the error details of the event.
     */
    void onError(Throwable throwable);

    /**
     * Gets notified for events on a http message.
     *
     * @param proxyServerForwardRequests contains the state change information of the event.
     */
    void onMessage(ProxyServerForwardRequests proxyServerForwardRequests);

    void onMessage(HTTPCarbonMessage httpCarbonMessage);


}
