package org.wso2.transport.http.netty;

import org.wso2.transport.http.netty.message.HTTPCarbonMessage;

/**
 * Created by bhashinee on 6/28/18.
 */
public interface ProxyConnectorListener {

    void onError(Throwable throwable);

    void onMessage(HTTPCarbonMessage httpMessage);

}
