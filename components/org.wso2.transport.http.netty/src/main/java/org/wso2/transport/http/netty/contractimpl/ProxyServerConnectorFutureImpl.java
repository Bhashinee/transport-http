package org.wso2.transport.http.netty.contractimpl;

import io.netty.channel.ChannelFuture;
import io.netty.channel.group.ChannelGroup;
import org.wso2.transport.http.netty.contract.HttpConnectorListener;
import org.wso2.transport.http.netty.contract.PortBindingEventListener;
import org.wso2.transport.http.netty.contract.PortBindingEventListenerForProxyServer;
import org.wso2.transport.http.netty.contract.ServerConnector;
import org.wso2.transport.http.netty.contract.ServerConnectorException;
import org.wso2.transport.http.netty.listener.ProxyServerConnectorFuture;
import org.wso2.transport.http.netty.message.HTTPCarbonMessage;

/**
 * Created by bhashinee on 6/26/18.
 */
public class ProxyServerConnectorFutureImpl implements ProxyServerConnectorFuture {

    private PortBindingEventListenerForProxyServer portBindingEventListener;
    private String openingServerConnectorId;
    private String closingServerConnectorId;
    private Throwable connectorInitException;
    private ChannelFuture nettyBindFuture;
    private ChannelGroup allChannels;
    private HttpConnectorListener httpConnectorListener;

    public ProxyServerConnectorFutureImpl(ChannelFuture nettyBindFuture, ChannelGroup allChannels) {
        this.nettyBindFuture = nettyBindFuture;
        this.allChannels = allChannels;
    }

    public void setHttpConnectorListener(HttpConnectorListener httpConnectorListener) {
        this.httpConnectorListener = httpConnectorListener;
    }

    @Override
    public void notifyHttpListener(HTTPCarbonMessage httpMessage) throws ServerConnectorException {
        if (httpConnectorListener == null) {
            throw new ServerConnectorException("HTTP connector listener is not set");
        }
        httpConnectorListener.onMessage(httpMessage);
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
                e.printStackTrace();
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
