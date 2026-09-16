package fr.euphyllia.fidorial.server.management;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.euphyllia.fidorial.server.FidorialServer;
import fr.euphyllia.fidorial.server.entity.player.ServerPlayer;
import fr.euphyllia.fidorial.server.management.json.JsonSchema;
import fr.euphyllia.fidorial.server.management.protocol.ServerMethods;
import fr.fidorial.moderation.BanEntry;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broadcasts minecraft:notification/* events to connected management sessions.
 */
public final class ManagementNotifier {

    private static final Gson GSON = new Gson();

    private final Set<Channel> sessions = ConcurrentHashMap.newKeySet();

    public void addSession(final Channel channel) {
        sessions.add(channel);
    }

    public void removeSession(final Channel channel) {
        sessions.remove(channel);
    }

    public void notifyServerStarted() {
        broadcast("minecraft:notification/server/started");
    }

    public void notifyServerStopping() {
        broadcast("minecraft:notification/server/stopping");
    }

    public void notifyServerSaving() {
        broadcast("minecraft:notification/server/saving");
    }

    public void notifyServerSaved() {
        broadcast("minecraft:notification/server/saved");
    }

    public void notifyServerStatus(final FidorialServer server) {
        broadcastWith("minecraft:notification/server/status", ServerMethods.status(server));
    }

    public void notifyWorldUpgradeStarted() {
        broadcast("minecraft:notification/world/upgrade_started");
    }

    public void notifyWorldUpgradeFinished() {
        broadcast("minecraft:notification/world/upgrade_finished");
    }

    public void notifyWorldUpgradeProgress(final double progress) {
        broadcastWith("minecraft:notification/world/upgrade_progress", new com.google.gson.JsonPrimitive(progress));
    }

    public void notifyWorldUpgradeFailed(final String reason) {
        broadcastWith("minecraft:notification/world/upgrade_failed", new com.google.gson.JsonPrimitive(reason));
    }

    public void notifyPlayerJoined(final ServerPlayer player) {
        broadcastWith("minecraft:notification/players/joined", JsonSchema.player(player));
    }

    public void notifyPlayerLeft(final ServerPlayer player) {
        broadcastWith("minecraft:notification/players/left", JsonSchema.player(player));
    }

    public void notifyOperatorAdded(final UUID id, final String name, final int permissionLevel, final boolean bypassesPlayerLimit) {
        final JsonObject operator = new JsonObject();
        operator.add("player", JsonSchema.player(id, name == null ? "" : name));
        operator.addProperty("permissionLevel", permissionLevel);
        operator.addProperty("bypassesPlayerLimit", bypassesPlayerLimit);
        broadcastWith("minecraft:notification/operators/added", operator);
    }

    public void notifyOperatorRemoved(final UUID id, final String name) {
        final JsonObject operator = new JsonObject();
        operator.add("player", JsonSchema.player(id, name == null ? "" : name));
        broadcastWith("minecraft:notification/operators/removed", operator);
    }

    public void notifyAllowlistAdded(final UUID id, final String name) {
        broadcastWith("minecraft:notification/allowlist/added", JsonSchema.player(id, name));
    }

    public void notifyAllowlistRemoved(final UUID id, final String name) {
        broadcastWith("minecraft:notification/allowlist/removed", JsonSchema.player(id, name));
    }

    public void notifyIpBanAdded(final BanEntry.Address entry) {
        broadcastWith("minecraft:notification/ip_bans/added", JsonSchema.ipBan(entry));
    }

    public void notifyIpBanRemoved(final String ip) {
        broadcastWith("minecraft:notification/ip_bans/removed", new com.google.gson.JsonPrimitive(ip));
    }

    public void notifyBanAdded(final BanEntry.Profile entry) {
        broadcastWith("minecraft:notification/bans/added", JsonSchema.userBan(entry));
    }

    public void notifyBanRemoved(final UUID id, final String name) {
        broadcastWith("minecraft:notification/bans/removed", JsonSchema.player(id, name));
    }

    public void notifyGameRuleUpdated(final net.kyori.adventure.key.Key key, final GameRuleRegistry.Value value) {
        final JsonObject rule = new JsonObject();
        rule.addProperty("key", key.asString());
        switch (value) {
            case GameRuleRegistry.BoolValue b -> {
                rule.addProperty("type", "boolean");
                rule.addProperty("value", b.value());
            }
            case GameRuleRegistry.IntValue i -> {
                rule.addProperty("type", "integer");
                rule.addProperty("value", i.value());
            }
        }
        broadcastWith("minecraft:notification/gamerules/updated", rule);
    }

    private void broadcast(final String method) {
        final JsonObject notification = envelope(method);
        notification.add("params", new JsonArray());
        send(notification);
    }

    private void broadcastWith(final String method, final com.google.gson.JsonElement singleParam) {
        final JsonObject notification = envelope(method);
        final JsonArray params = new JsonArray();
        params.add(singleParam);
        notification.add("params", params);
        send(notification);
    }

    private JsonObject envelope(final String method) {
        final JsonObject o = new JsonObject();
        o.addProperty("jsonrpc", "2.0");
        o.addProperty("method", method);
        return o;
    }

    private void send(final JsonObject notification) {
        if (sessions.isEmpty()) {
            return;
        }
        final String json = GSON.toJson(notification);
        for (final Channel ch : sessions) {
            if (ch.isActive()) {
                ch.writeAndFlush(new TextWebSocketFrame(json));
            }
        }
    }
}
