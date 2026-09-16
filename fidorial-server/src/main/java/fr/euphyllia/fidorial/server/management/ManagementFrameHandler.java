package fr.euphyllia.fidorial.server.management.protocol;

import com.google.gson.*;
import fr.euphyllia.fidorial.server.FidorialServer;
import fr.euphyllia.fidorial.server.management.ManagementNotifier;
import fr.euphyllia.fidorial.server.management.rpc.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ManagementFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(ManagementFrameHandler.class);
    private static final Gson GSON = new Gson();
    private static final JsonElement SCHEMA = loadSchema();

    private final FidorialServer server;
    private final ManagementNotifier notifier;
    private final MethodRegistry registry = new MethodRegistry();

    public ManagementFrameHandler(final FidorialServer server, final ManagementNotifier notifier) {
        this.server = server;
        this.notifier = notifier;
        AllowlistMethods.register(registry);
        BansMethods.register(registry);
        IpBansMethods.register(registry);
        PlayersMethods.register(registry);
        OperatorsMethods.register(registry);
        ServerMethods.register(registry);
        ServerSettingsMethods.register(registry);
        GameRulesMethods.register(registry);
        registry.register("rpc.discover", (_, _) -> SCHEMA);
    }

    private static JsonElement loadSchema() {
        try (final InputStream in = ManagementFrameHandler.class.getResourceAsStream("/management/json-rpc-api-schema.json")) {
            if (in == null) {
                LOGGER.error("Bundled management API schema resource is missing; rpc.discover will fail.");
                return JsonNull.INSTANCE;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (final Exception e) {
            LOGGER.error("Unable to load the bundled management API schema", e);
            return JsonNull.INSTANCE;
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        notifier.addSession(ctx.channel());
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        notifier.removeSession(ctx.channel());
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final TextWebSocketFrame frame) {
        JsonElement id = null;
        final JsonRpcRequest request;
        try {
            final JsonObject json = JsonParser.parseString(frame.text()).getAsJsonObject();
            id = json.has("id") ? json.get("id") : null;
            request = new JsonRpcRequest(
                    json.get("jsonrpc").getAsString(),
                    id,
                    json.get("method").getAsString(),
                    json.has("params") ? json.get("params") : null);
        } catch (final Exception malformed) {
            reply(ctx, JsonRpcResponse.fail(id, new JsonRpcError(JsonRpcError.PARSE_ERROR, "Parse error", null)));
            return;
        }

        final MethodRegistry.MethodHandler handler = registry.get(request.method());
        if (handler == null) {
            reply(ctx, JsonRpcResponse.fail(request.id(), new JsonRpcError(
                    JsonRpcError.METHOD_NOT_FOUND, "Method not found: " + request.method(), null)));
            return;
        }

        try {
            final JsonElement result = handler.handle(server, request.params());
            reply(ctx, JsonRpcResponse.ok(request.id(), result));
        } catch (final IllegalArgumentException badParams) {
            reply(ctx, JsonRpcResponse.fail(request.id(), new JsonRpcError(
                    JsonRpcError.INVALID_PARAMS, badParams.getMessage(), null)));
        } catch (final Exception internal) {
            LOGGER.error("Management RPC {} failed", request.method(), internal);
            reply(ctx, JsonRpcResponse.fail(request.id(), new JsonRpcError(
                    JsonRpcError.INTERNAL_ERROR, "Internal error", null)));
        }
    }

    private void reply(final ChannelHandlerContext ctx, final JsonRpcResponse response) {
        ctx.writeAndFlush(new TextWebSocketFrame(GSON.toJson(response)));
    }
}
