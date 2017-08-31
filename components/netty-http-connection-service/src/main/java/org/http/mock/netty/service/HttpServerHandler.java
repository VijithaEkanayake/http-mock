/*
 * Copyright 2017 WSO2 Inc. (http://wso2.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.http.mock.netty.service;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Handler implementation for the server.
 */
@Sharable public class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private HttpRequest request;
    private final StringBuilder buf = new StringBuilder();

    @Override public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {

        request = msg;
        String uri = request.uri();
        QueryStringDecoder decoder = new QueryStringDecoder(uri);

        switch (decoder.path()) {
        case "/slow":
            slowResponse(ctx, decoder.parameters());
            break;
        case "/conclose":
            closeConnection(ctx, decoder.parameters());
            break;
        case "/echo":
            echoMessage(ctx, msg);
            break;
        default:
            resourceNotFound(ctx);
        }
    }

    private void echoMessage(ChannelHandlerContext ctx, FullHttpRequest msg) {

        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK, msg.content().copy());
        String contentType = msg.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType != null) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        }
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        ctx.write(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void resourceNotFound(ChannelHandlerContext ctx) {
        buf.setLength(0);
        buf.append("The server couldn't locate the requested resource\r\n");
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.NOT_FOUND,
                Unpooled.copiedBuffer(buf.toString(), CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void closeConnection(ChannelHandlerContext ctx, Map<String, List<String>> parameters) {
        delay(parameters);
        ctx.channel().close();
    }

    private void slowResponse(ChannelHandlerContext ctx, Map<String, List<String>> parameters) {

        buf.setLength(0);
        delay(parameters);

        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(buf.toString(), CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        //System.out.println("The content : " +buf.toString());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * @param parameters Map of Query params
     */
    public void delay(Map<String, List<String>> parameters) {

        String qVal = null;
        if (!parameters.isEmpty()) {
            for (Map.Entry<String, List<String>> p : parameters.entrySet()) {
                String key = p.getKey();
                if (key.equals("delay")) {
                    List<String> vals = p.getValue();
                    for (String val : vals) {
                        qVal = val;
                    }
                } else if (key.equals("randomdelay")) {
                    List<String> vals = p.getValue();
                    for (String val : vals) {
                        qVal = val;
                    }
                    randomDelay(Integer.parseInt(qVal));
                    return;
                }
            }
        }

        int delay = 0;
        if (qVal != null) {
            delay = Integer.parseInt(qVal);
            try {
                Thread.sleep(delay * 1000L);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        buf.append("The delay is set to : " + delay + "\r\n");
    }

    /**
     * @param upperRange The upper limit of the random value generated
     */
    private void randomDelay(int upperRange) {

        int delay;
        Random rand = new Random();
        delay = rand.nextInt(upperRange);

        try {
            Thread.sleep(delay * 1000L);
        } catch (InterruptedException e) {
            // ignore
        }
    }
}
