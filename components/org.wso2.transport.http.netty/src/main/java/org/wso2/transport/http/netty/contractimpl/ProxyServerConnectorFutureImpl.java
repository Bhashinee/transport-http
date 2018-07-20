package org.wso2.transport.http.netty.contractimpl;

import io.netty.channel.ChannelFuture;
import io.netty.channel.group.ChannelGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.ProxyConnectorListener;
import org.wso2.transport.http.netty.contract.PortBindingEventListenerForProxyServer;
import org.wso2.transport.http.netty.contract.ServerConnectorException;
import org.wso2.transport.http.netty.contract.proxyserver.ProxyServerForwardRequests;
import org.wso2.transport.http.netty.listener.ProxyServerConnectorFuture;

/**
 * A class for notifying ballerina....
 */
public class ProxyServerConnectorFutureImpl implements ProxyServerConnectorFuture {

    private PortBindingEventListenerForProxyServer portBindingEventListener;
    private String openingServerConnectorId;
    private String closingServerConnectorId;
    private Throwable connectorInitException;
    private ChannelFuture nettyBindFuture;
    private ChannelGroup allChannels;
    private ProxyConnectorListener proxyConnectorListener;
    private static final Logger log = LoggerFactory.getLogger(ProxyServerConnectorFutureImpl.class);


    public ProxyServerConnectorFutureImpl() {}

    public ProxyServerConnectorFutureImpl(ChannelFuture nettyBindFuture, ChannelGroup allChannels) {
        this.nettyBindFuture = nettyBindFuture;
        this.allChannels = allChannels;
    }

    @Override
    public void setProxyConnectorListener(ProxyConnectorListener proxyConnectorListener) {
        this.proxyConnectorListener = proxyConnectorListener;
    }

    @Override
    public void notifyHttpListener(ProxyServerForwardRequests proxyServerForwardRequests)
            throws ServerConnectorException {
        if (proxyConnectorListener == null) {
            throw new ServerConnectorException("HTTP connector listener is not set");
        }
        proxyConnectorListener.onMessage(proxyServerForwardRequests);
    }

    @Override
    public void notifyPortBindingEvent(String serverConnectorId) {
        if (portBindingEventListener == null) {
            this.openingServerConnectorId = serverConnectorId;
        } else {
            portBindingEventListener.onOpen(serverConnectorId);
        }
    }

    @Override
    public void notifyPortUnbindingEvent(String serverConnectorId) throws ServerConnectorException {
        if (portBindingEventListener == null) {
            this.closingServerConnectorId = serverConnectorId;
        } else {
            portBindingEventListener.onClose(serverConnectorId);
        }
    }

    @Override
    public void notifyPortBindingError(Throwable throwable) {
        if (portBindingEventListener == null) {
            this.connectorInitException = throwable;
        }  else {
            portBindingEventListener.onError(throwable);
        }
    }

    @Override
    public void setPortBindingEventListener(
            PortBindingEventListenerForProxyServer portBindingEventListenerForProxyServer) {
        this.portBindingEventListener = portBindingEventListenerForProxyServer;
        if (openingServerConnectorId != null) {
            notifyPortBindingEvent(openingServerConnectorId);
            openingServerConnectorId = null;
        }
        if (closingServerConnectorId != null) {
            try {
                notifyPortUnbindingEvent(closingServerConnectorId);
            } catch (ServerConnectorException e) {
                log.error("Unable to bind to a port");
            }
        }
        if (connectorInitException != null) {
            notifyPortBindingError(connectorInitException);
            connectorInitException = null;
        }
    }

    @Override
    public void sync() throws InterruptedException {
        ChannelFuture bindFuture = nettyBindFuture.sync();
        if (this.allChannels != null && bindFuture.channel() != null) {
            this.allChannels.add(bindFuture.channel());
        }
    }
}
