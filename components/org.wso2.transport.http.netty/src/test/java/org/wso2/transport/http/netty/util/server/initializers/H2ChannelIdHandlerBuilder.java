/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.transport.http.netty.util.server.initializers;

import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Settings;

import static io.netty.handler.logging.LogLevel.INFO;

public final class H2ChannelIdHandlerBuilder
    extends AbstractHttp2ConnectionHandlerBuilder<H2ChannelIdHandler, H2ChannelIdHandlerBuilder> {

    private static final Http2FrameLogger logger = new Http2FrameLogger(INFO, H2ChannelIdHandler.class);

    public H2ChannelIdHandlerBuilder() {
        frameLogger(logger);
    }

    @Override
    public H2ChannelIdHandler build() {
        return super.build();
    }

    @Override
    protected H2ChannelIdHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                       Http2Settings initialSettings) {
        H2ChannelIdHandler handler = new H2ChannelIdHandler(decoder, encoder, initialSettings);
        frameListener(handler);
        return handler;
    }
}
