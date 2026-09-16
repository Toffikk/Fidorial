package fr.euphyllia.fidorial.server.management.protocol;

import fr.euphyllia.fidorial.server.FidorialServer;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.util.List;

public final class ManagementHandshakeHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final FidorialServer server;

    public ManagementHandshakeHandler(final FidorialServer server) {
        this.server = server;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest request) {
        final var config = server.config();

        final String origin = request.headers().get(HttpHeaderNames.ORIGIN);
        final List<String> allowed = config.managementServerAllowedOrigins();
        if (allowed.isEmpty() || origin == null || !allowed.contains(origin)) {
            reject(ctx, request);
            return;
        }

        final String secret = config.managementServerSecret();
        final String bearer = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        final String wsProtocol = request.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL);

        final boolean okBearer = bearer != null && bearer.equals("Bearer " + secret);
        final boolean okProtocol = wsProtocol != null && matchesProtocolSecret(wsProtocol, secret);

        if (!okBearer && !okProtocol) {
            reject(ctx, request);
            return;
        }

        if (okProtocol) {
            request.headers().set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, "minecraft-v1");
        }
        ctx.fireChannelRead(request.retain());
    }

    private static boolean matchesProtocolSecret(final String header, final String secret) {
        final String[] tokens = header.split(",");
        if (tokens.length != 2) {
            return false;
        }
        return tokens[0].trim().equals("minecraft-v1") && tokens[1].trim().equals(secret);
    }

    private void reject(final ChannelHandlerContext ctx, final FullHttpRequest request) {
        final FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.UNAUTHORIZED);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
