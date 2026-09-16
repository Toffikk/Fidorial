package fr.euphyllia.fidorial.server.management.protocol;

import com.google.gson.*;
import fr.euphyllia.fidorial.server.management.GameRuleRegistry;
import fr.euphyllia.fidorial.server.management.rpc.MethodRegistry;
import fr.euphyllia.fidorial.server.management.rpc.RpcParams;
import net.kyori.adventure.key.Key;

public final class GameRulesMethods {

    private GameRulesMethods() {
    }

    public static void register(final MethodRegistry registry) {
        registry.register("minecraft:gamerules", (server, _) -> {
            final JsonArray out = new JsonArray();
            server.gameRules().all().forEach((key, value) -> out.add(toTyped(key, value)));
            return out;
        });

        registry.register("minecraft:gamerules/update", (server, params) -> {
            final JsonObject rule = RpcParams.of(params, "gamerule").require("gamerule").getAsJsonObject();
            final Key key = Key.key(rule.get("key").getAsString());
            final JsonElement rawValue = rule.get("value");

            final GameRuleRegistry.Value parsed = rawValue.getAsJsonPrimitive().isBoolean()
                    ? new GameRuleRegistry.BoolValue(rawValue.getAsBoolean())
                    : new GameRuleRegistry.IntValue(rawValue.getAsInt());

            final GameRuleRegistry.Value updated = server.gameRules().update(key, parsed);
            return toTyped(key, updated);
        });
    }

    private static JsonObject toTyped(final Key key, final GameRuleRegistry.Value value) {
        final JsonObject o = new JsonObject();
        o.addProperty("key", key.asString());
        switch (value) {
            case GameRuleRegistry.BoolValue b -> {
                o.addProperty("type", "boolean");
                o.addProperty("value", b.value());
            }
            case GameRuleRegistry.IntValue i -> {
                o.addProperty("type", "integer");
                o.addProperty("value", i.value());
            }
        }
        return o;
    }
}
