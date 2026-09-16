package fr.euphyllia.fidorial.server.management.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.euphyllia.fidorial.server.FidorialServer;
import fr.euphyllia.fidorial.server.management.json.JsonSchema;
import fr.euphyllia.fidorial.server.management.rpc.MethodRegistry;
import fr.euphyllia.fidorial.server.management.rpc.RpcParams;
import fr.euphyllia.fidorial.server.moderation.FidorialWhitelist;
import fr.fidorial.entity.PlayerProfile;

import java.util.List;
import java.util.UUID;

public final class AllowlistMethods {

    private AllowlistMethods() {
    }

    public static void register(final MethodRegistry registry) {
        registry.register("minecraft:allowlist", (server, _) -> snapshot(server));

        registry.register("minecraft:allowlist/set", (server, params) -> {
            final FidorialWhitelist whitelist = server.whitelist0();
            final JsonArray incoming = RpcParams.of(params, "players").require("players").getAsJsonArray();
            for (final PlayerProfile p : List.copyOf(whitelist.entries().toList())) {
                whitelist.remove(p.uuid());
            }
            for (final JsonElement el : incoming) {
                addOne(whitelist, el.getAsJsonObject());
            }
            return snapshot(server);
        });

        registry.register("minecraft:allowlist/add", (server, params) -> {
            final FidorialWhitelist whitelist = server.whitelist0();
            for (final JsonElement el : RpcParams.of(params, "add").require("add").getAsJsonArray()) {
                addOne(whitelist, el.getAsJsonObject());
            }
            return snapshot(server);
        });

        registry.register("minecraft:allowlist/remove", (server, params) -> {
            final FidorialWhitelist whitelist = server.whitelist0();
            for (final JsonElement el : RpcParams.of(params, "remove").require("remove").getAsJsonArray()) {
                final JsonObject playerObj = el.getAsJsonObject();
                final UUID id = JsonSchema.playerId(playerObj);
                if (id != null) whitelist.remove(id);
            }
            return snapshot(server);
        });

        registry.register("minecraft:allowlist/clear", (server, _) -> {
            final FidorialWhitelist whitelist = server.whitelist0();
            for (final PlayerProfile p : List.copyOf(whitelist.entries().toList())) {
                whitelist.remove(p.uuid());
            }
            return snapshot(server);
        });
    }

    private static void addOne(final FidorialWhitelist whitelist, final JsonObject playerObj) {
        final UUID id = JsonSchema.playerId(playerObj);
        final String name = JsonSchema.playerName(playerObj);
        if (id == null) {
            throw new IllegalArgumentException("Allowlist entries require a player 'id' (name-only resolution isn't wired up yet)");
        }
        whitelist.add(new PlayerProfile(id, name == null ? "" : name, List.of()));
    }

    private static JsonArray snapshot(final FidorialServer server) {
        final JsonArray out = new JsonArray();
        server.whitelist0().entries().forEach(p -> out.add(JsonSchema.player(p.uuid(), p.name())));
        return out;
    }
}
