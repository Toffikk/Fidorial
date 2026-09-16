package fr.euphyllia.fidorial.server.management.protocol;

import com.google.gson.*;
import fr.euphyllia.fidorial.server.FidorialServer;
import fr.euphyllia.fidorial.server.management.json.JsonSchema;
import fr.euphyllia.fidorial.server.management.rpc.MethodRegistry;
import fr.euphyllia.fidorial.server.management.rpc.RpcParams;
import net.kyori.adventure.text.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ServerMethods {

    private ServerMethods() {
    }

    public static void register(final MethodRegistry registry) {
        registry.register("minecraft:server/status", (server, _) -> status(server));

        registry.register("minecraft:server/save", (server, params) -> {
            final boolean flush = RpcParams.of(params, "flush").require("flush").getAsBoolean();
            Thread.ofVirtual().start(() -> {
                try {
                    if (flush) {
                        server.worldManager().saveAll();
                    } else {
                        server.worldManager().saveDirty();
                    }
                } catch (final IOException e) {
                    FidorialServer.LOGGER.error("Management-triggered save failed", e);
                }
            });
            return new JsonPrimitive(true);
        });

        registry.register("minecraft:server/stop", (server, _) -> {
            Thread.ofVirtual().start(server::shutdown);
            return new JsonPrimitive(true);
        });

        registry.register("minecraft:server/system_message", (server, params) -> {
            final JsonObject systemMessage = RpcParams.of(params, "message").require("message").getAsJsonObject();
            final Component text = JsonSchema.messageToComponent(systemMessage.getAsJsonObject("message"));

            final List<UUIDName> targets = new ArrayList<>();
            if (systemMessage.has("receivingPlayers")) {
                for (final JsonElement el : systemMessage.getAsJsonArray("receivingPlayers")) {
                    final JsonObject p = el.getAsJsonObject();
                    targets.add(new UUIDName(JsonSchema.playerId(p), JsonSchema.playerName(p)));
                }
            }

            if (targets.isEmpty()) {
                server.players().forEach(pl -> pl.sendMessage(text));
            } else {
                server.players().stream()
                        .filter(pl -> targets.stream().anyMatch(t ->
                                (t.id() != null && t.id().equals(pl.uuid())) ||
                                        (t.name() != null && t.name().equalsIgnoreCase(pl.name()))))
                        .forEach(pl -> pl.sendMessage(text));
            }
            return new JsonPrimitive(true);
        });
    }

    private record UUIDName(UUID id, String name) {
    }

    public static JsonObject status(final FidorialServer server) {
        final JsonObject o = new JsonObject();
        o.addProperty("started", server.isRunning());
        final JsonArray players = new JsonArray();
        server.players().forEach(p -> players.add(JsonSchema.player(p)));
        o.add("players", players);
        o.add("version", JsonSchema.version(server.brandName() + " " + server.minecraftVersion(), server.protocolVersion()));
        return o;
    }
}
