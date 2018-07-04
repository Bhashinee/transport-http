package org.wso2.transport.http.netty.contract;

/**
 * Created by bhashinee on 6/26/18.
 */
public interface PortBindingEventListenerForProxyServer {
    void onOpen(String serverConnectorId);
    void onClose(String serverConnectorId);
    void onError(Throwable throwable);
}
