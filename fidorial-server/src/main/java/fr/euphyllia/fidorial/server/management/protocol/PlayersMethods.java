package fr.euphyllia.fidorial.server.management.protocol;

import com.google.gson.*;
import fr.euphyllia.fidorial.server.FidorialServer;
import fr.euphyllia.fidorial.server.entity.player.ServerPlayer;
import fr.euphyllia.fidorial.server.management.json.JsonSchema;
import fr.euphyllia.fidorial.server.management.rpc.MethodRegistry;
import fr.euphyllia.fidorial.server.management.rpc.RpcParams;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class PlayersMethods {

    private PlayersMethods() {
    }

    public static void register(final MethodRegistry registry) {
        registry.register("minecraft:players", (server, _) -> {
            final JsonArray out = new JsonArray();
            server.players().forEach(p -> out.add(JsonSchema.player(p)));
            return out;
        });

        registry.register("minecraft:players/kick", (server, params) -> {
            final JsonArray requests = RpcParams.of(params, "kick").require("kick").getAsJsonArray();
            final List<ServerPlayer> kicked = Collections.synchronizedList(new ArrayList<>());
            final List<CompletableFuture<@Nullable Void>> pending = new ArrayList<>();

            for (final JsonElement el : requests) {
                final JsonObject obj = el.getAsJsonObject();
                final ServerPlayer target = resolve(server, obj.getAsJsonObject("player"));
                if (target == null) continue;

                final Component reason = obj.has("message")
                        ? JsonSchema.messageToComponent(obj.getAsJsonObject("message"))
                        : Component.translatable("multiplayer.disconnect.kicked");

                final CompletableFuture<@Nullable Void> done = new CompletableFuture<>();
                pending.add(done);
                target.connection().execute(() -> {
                    target.kick(reason);
                    kicked.add(target);
                    done.complete(null);
                });
            }

            CompletableFuture.allOf(pending.toArray(new CompletableFuture[0])).join();

            final JsonArray out = new JsonArray();
            kicked.forEach(p -> out.add(JsonSchema.player(p)));
            return out;
        });
    }

    private static @Nullable ServerPlayer resolve(final FidorialServer server, final JsonObject spec) {
        final String idStr = spec.has("id") ? spec.get("id").getAsString() : null;
        if (idStr != null) {
            return (ServerPlayer) server.player(UUID.fromString(idStr)).orElse(null);
        }
        if (spec.has("name")) {
            return (ServerPlayer) server.player(spec.get("name").getAsString()).orElse(null);
        }
        return null;
    }
}
