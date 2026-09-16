package fr.euphyllia.fidorial.server.management.protocol;

import com.google.gson.*;
import fr.euphyllia.fidorial.server.FidorialServer;
import fr.euphyllia.fidorial.server.management.json.JsonSchema;
import fr.euphyllia.fidorial.server.management.rpc.MethodRegistry;
import fr.euphyllia.fidorial.server.management.rpc.RpcParams;
import fr.euphyllia.fidorial.server.moderation.FidorialBanManager;
import fr.fidorial.moderation.BanEntry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BansMethods {

    private BansMethods() {
    }

    public static void register(final MethodRegistry registry) {
        registry.register("minecraft:bans", (server, _) -> snapshot(server));

        registry.register("minecraft:bans/set", (server, params) -> {
            final FidorialBanManager bans = server.ban0();
            clearProfileBans(bans);
            for (final JsonElement el : RpcParams.of(params, "bans").require("bans").getAsJsonArray()) {
                addOne(bans, el.getAsJsonObject());
            }
            return snapshot(server);
        });

        registry.register("minecraft:bans/add", (server, params) -> {
            final FidorialBanManager bans = server.ban0();
            for (final JsonElement el : RpcParams.of(params, "add").require("add").getAsJsonArray()) {
                addOne(bans, el.getAsJsonObject());
            }
            return snapshot(server);
        });

        registry.register("minecraft:bans/remove", (server, params) -> {
            final FidorialBanManager bans = server.ban0();
            for (final JsonElement el : RpcParams.of(params, "remove").require("remove").getAsJsonArray()) {
                final UUID id = JsonSchema.playerId(el.getAsJsonObject());
                if (id != null) bans.pardon(id);
            }
            return snapshot(server);
        });

        registry.register("minecraft:bans/clear", (server, _) -> {
            clearProfileBans(server.ban0());
            return snapshot(server);
        });
    }

    private static void clearProfileBans(final FidorialBanManager bans) {
        for (final BanEntry.Profile entry : List.copyOf(bans.profileBans().toList())) {
            bans.pardon(entry.uuid());
        }
    }

    private static void addOne(final FidorialBanManager bans, final JsonObject entry) {
        final JsonObject playerObj = entry.getAsJsonObject("player");
        final UUID id = JsonSchema.playerId(playerObj);
        final String name = JsonSchema.playerName(playerObj);
        if (id == null) {
            throw new IllegalArgumentException("user_ban entries require a player 'id'");
        }
        bans.ban(new BanEntry.Profile(
                id, name,
                JsonSchema.readReason(entry),
                JsonSchema.readSource(entry),
                Instant.now(),
                JsonSchema.readExpires(entry)));
    }

    private static JsonArray snapshot(final FidorialServer server) {
        final JsonArray out = new JsonArray();
        server.ban0().profileBans().forEach(entry -> out.add(JsonSchema.userBan(entry)));
        return out;
    }
}
