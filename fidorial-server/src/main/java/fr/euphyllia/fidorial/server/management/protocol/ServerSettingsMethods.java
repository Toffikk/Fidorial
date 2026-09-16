package fr.euphyllia.fidorial.server.management.protocol;

import com.google.gson.JsonPrimitive;
import fr.euphyllia.fidorial.server.FidorialServer;
import fr.euphyllia.fidorial.server.management.LiveServerSettings;
import fr.euphyllia.fidorial.server.management.rpc.MethodRegistry;
import fr.euphyllia.fidorial.server.management.rpc.RpcParams;
import fr.fidorial.entity.GameMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class ServerSettingsMethods {

    private ServerSettingsMethods() {
    }

    public static void register(final MethodRegistry registry) {
        boolGetSet(registry, "minecraft:serversettings/use_allowlist", "use",
                s -> s.whitelist0().enabled(),
                (s, v) -> s.whitelist0().enabled(v));

        intGetSet(registry, "minecraft:serversettings/max_players", "max",
                FidorialServer::maxPlayers,
                FidorialServer::maxPlayers);

        registry.register("minecraft:serversettings/motd", (server, _) ->
                new JsonPrimitive(PlainTextComponentSerializer.plainText().serialize(server.description())));
        registry.register("minecraft:serversettings/motd/set", (server, params) -> {
            final String text = RpcParams.of(params, "message").require("message").getAsString();
            final Component parsed = MiniMessage.miniMessage().deserialize(text);
            server.description(parsed);
            return new JsonPrimitive(text);
        });

        registry.register("minecraft:serversettings/view_distance", (server, _) ->
                new JsonPrimitive(server.config().viewDistance()));

        boolGetSet(registry, "minecraft:serversettings/autosave", "enable",
                s -> live(s).autosaveEnabled().get(), (s, v) -> live(s).autosaveEnabled().set(v));

        stringGetSet(registry, "minecraft:serversettings/difficulty", "difficulty",
                s -> live(s).difficulty().get(), (s, v) -> live(s).difficulty().set(v));

        boolGetSet(registry, "minecraft:serversettings/enforce_allowlist", "enforce",
                s -> live(s).enforceAllowlist().get(), (s, v) -> live(s).enforceAllowlist().set(v));

        intGetSet(registry, "minecraft:serversettings/pause_when_empty_seconds", "seconds",
                s -> live(s).pauseWhenEmptySeconds().get(), (s, v) -> live(s).pauseWhenEmptySeconds().set(v));

        intGetSet(registry, "minecraft:serversettings/player_idle_timeout", "seconds",
                s -> live(s).playerIdleTimeoutSeconds().get(), (s, v) -> live(s).playerIdleTimeoutSeconds().set(v));

        boolGetSet(registry, "minecraft:serversettings/allow_flight", "allow",
                s -> live(s).allowFlight().get(), (s, v) -> live(s).allowFlight().set(v));

        intGetSet(registry, "minecraft:serversettings/spawn_protection_radius", "radius",
                s -> live(s).spawnProtectionRadius().get(), (s, v) -> live(s).spawnProtectionRadius().set(v));

        boolGetSet(registry, "minecraft:serversettings/force_game_mode", "force",
                s -> live(s).forceGameMode().get(), (s, v) -> live(s).forceGameMode().set(v));

        registry.register("minecraft:serversettings/game_mode", (server, _) ->
                new JsonPrimitive(live(server).defaultGameMode().get().name().toLowerCase()));
        registry.register("minecraft:serversettings/game_mode/set", (server, params) -> {
            final String raw = RpcParams.of(params, "mode").require("mode").getAsString();
            final GameMode mode = GameMode.valueOf(raw.toUpperCase());
            live(server).defaultGameMode().set(mode);
            return new JsonPrimitive(raw);
        });

        intGetSet(registry, "minecraft:serversettings/simulation_distance", "distance",
                s -> live(s).simulationDistance().get(), (s, v) -> live(s).simulationDistance().set(v));

        boolGetSet(registry, "minecraft:serversettings/accept_transfers", "accept",
                s -> live(s).acceptTransfers().get(), (s, v) -> live(s).acceptTransfers().set(v));

        intGetSet(registry, "minecraft:serversettings/status_heartbeat_interval", "seconds",
                s -> live(s).statusHeartbeatIntervalSeconds().get(), (s, v) -> live(s).statusHeartbeatIntervalSeconds().set(v));

        intGetSet(registry, "minecraft:serversettings/operator_user_permission_level", "level",
                s -> live(s).operatorPermissionLevel().get(), (s, v) -> live(s).operatorPermissionLevel().set(v));

        boolGetSet(registry, "minecraft:serversettings/hide_online_players", "hide",
                s -> live(s).hideOnlinePlayers().get(), (s, v) -> live(s).hideOnlinePlayers().set(v));

        boolGetSet(registry, "minecraft:serversettings/status_replies", "enable",
                s -> live(s).statusRepliesEnabled().get(), (s, v) -> live(s).statusRepliesEnabled().set(v));

        intGetSet(registry, "minecraft:serversettings/entity_broadcast_range", "percentage_points",
                s -> live(s).entityBroadcastRangePercent().get(), (s, v) -> live(s).entityBroadcastRangePercent().set(v));
    }

    private static LiveServerSettings live(final FidorialServer server) {
        return server.liveSettings();
    }

    private interface Getter<T> { T get(FidorialServer server); }
    private interface Setter<T> { void set(FidorialServer server, T value); }

    private static void boolGetSet(final MethodRegistry registry, final String path, final String paramKey,
                                   final Getter<Boolean> getter, final Setter<Boolean> setter) {
        registry.register(path, (server, _) -> new JsonPrimitive(getter.get(server)));
        registry.register(path + "/set", (server, params) -> {
            final boolean value = RpcParams.of(params, paramKey).require(paramKey).getAsBoolean();
            setter.set(server, value);
            return new JsonPrimitive(value);
        });
    }

    private static void intGetSet(final MethodRegistry registry, final String path, final String paramKey,
                                  final Getter<Integer> getter, final Setter<Integer> setter) {
        registry.register(path, (server, _) -> new JsonPrimitive(getter.get(server)));
        registry.register(path + "/set", (server, params) -> {
            final int value = RpcParams.of(params, paramKey).require(paramKey).getAsInt();
            setter.set(server, value);
            return new JsonPrimitive(value);
        });
    }

    private static void stringGetSet(final MethodRegistry registry, final String path, final String paramKey,
                                     final Getter<String> getter, final Setter<String> setter) {
        registry.register(path, (server, _) -> new JsonPrimitive(getter.get(server)));
        registry.register(path + "/set", (server, params) -> {
            final String value = RpcParams.of(params, paramKey).require(paramKey).getAsString();
            setter.set(server, value);
            return new JsonPrimitive(value);
        });
    }
}
