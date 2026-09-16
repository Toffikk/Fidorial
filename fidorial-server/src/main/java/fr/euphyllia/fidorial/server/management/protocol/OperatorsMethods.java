package fr.euphyllia.fidorial.server.management.protocol;

import com.google.gson.*;
import fr.euphyllia.fidorial.server.FidorialServer;
import fr.euphyllia.fidorial.server.management.json.JsonSchema;
import fr.euphyllia.fidorial.server.management.rpc.MethodRegistry;
import fr.euphyllia.fidorial.server.management.rpc.RpcParams;
import fr.euphyllia.fidorial.server.permission.OperatorList;

import java.util.UUID;

public final class OperatorsMethods {

    private OperatorsMethods() {
    }

    public static void register(final MethodRegistry registry) {
        registry.register("minecraft:operators", (server, _) -> snapshot(server));

        registry.register("minecraft:operators/set", (server, params) -> {
            server.operators().clear();
            for (final JsonElement el : RpcParams.of(params, "operators").require("operators").getAsJsonArray()) {
                addOne(server, el.getAsJsonObject());
            }
            return snapshot(server);
        });

        registry.register("minecraft:operators/add", (server, params) -> {
            for (final JsonElement el : RpcParams.of(params, "add").require("add").getAsJsonArray()) {
                addOne(server, el.getAsJsonObject());
            }
            return snapshot(server);
        });

        registry.register("minecraft:operators/remove", (server, params) -> {
            for (final JsonElement el : RpcParams.of(params, "remove").require("remove").getAsJsonArray()) {
                final UUID id = JsonSchema.playerId(el.getAsJsonObject());
                if (id != null) server.operators().setOp(id, null, false);
            }
            return snapshot(server);
        });

        registry.register("minecraft:operators/clear", (server, _) -> {
            server.operators().clear();
            return snapshot(server);
        });
    }

    private static void addOne(final FidorialServer server, final JsonObject entry) {
        final JsonObject playerObj = entry.getAsJsonObject("player");
        final UUID id = JsonSchema.playerId(playerObj);
        final String name = JsonSchema.playerName(playerObj);
        if (id == null) {
            throw new IllegalArgumentException("operator entries require a player 'id'");
        }

        final int permissionLevel = entry.has("permissionLevel")
                ? entry.get("permissionLevel").getAsInt()
                : OperatorList.DEFAULT_PERMISSION_LEVEL;

        final boolean bypassesPlayerLimit = entry.has("bypassesPlayerLimit")
                ? entry.get("bypassesPlayerLimit").getAsBoolean()
                : server.operators().bypassesPlayerLimit(id);

        server.operators().setOp(id, name, true, permissionLevel, bypassesPlayerLimit);
    }

    private static JsonArray snapshot(final FidorialServer server) {
        final JsonArray out = new JsonArray();
        for (final OperatorList.Entry entry : server.operators().entries()) {
            out.add(operator(entry));
        }
        return out;
    }

    private static JsonObject operator(final OperatorList.Entry entry) {
        final JsonObject o = new JsonObject();
        o.add("player", JsonSchema.player(entry.uuid(), entry.name() == null ? "" : entry.name()));
        o.addProperty("permissionLevel", entry.permissionLevel());
        o.addProperty("bypassesPlayerLimit", entry.bypassesPlayerLimit());
        return o;
    }
}
