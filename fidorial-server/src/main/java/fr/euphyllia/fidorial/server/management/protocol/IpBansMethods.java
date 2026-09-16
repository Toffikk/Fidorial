package fr.euphyllia.fidorial.server.management.protocol;

import com.google.common.net.InetAddresses;
import com.google.gson.*;
import fr.euphyllia.fidorial.server.FidorialServer;
import fr.euphyllia.fidorial.server.management.json.JsonSchema;
import fr.euphyllia.fidorial.server.management.rpc.MethodRegistry;
import fr.euphyllia.fidorial.server.management.rpc.RpcParams;
import fr.euphyllia.fidorial.server.moderation.FidorialBanManager;
import fr.fidorial.moderation.BanEntry;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class IpBansMethods {

    private IpBansMethods() {
    }

    public static void register(final MethodRegistry registry) {
        registry.register("minecraft:ip_bans", (server, _) -> snapshot(server));

        registry.register("minecraft:ip_bans/set", (server, params) -> {
            final FidorialBanManager bans = server.ban0();
            clearIpBans(bans);
            for (final JsonElement el : RpcParams.of(params, "banlist").require("banlist").getAsJsonArray()) {
                addOne(bans, el.getAsJsonObject());
            }
            return snapshot(server);
        });

        registry.register("minecraft:ip_bans/add", (server, params) -> {
            final FidorialBanManager bans = server.ban0();
            for (final JsonElement el : RpcParams.of(params, "add").require("add").getAsJsonArray()) {
                addOne(bans, el.getAsJsonObject());
            }
            return snapshot(server);
        });

        registry.register("minecraft:ip_bans/remove", (server, params) -> {
            final FidorialBanManager bans = server.ban0();
            for (final JsonElement el : RpcParams.of(params, "ip").require("ip").getAsJsonArray()) {
                bans.pardon(InetAddresses.forString(el.getAsString()));
            }
            return snapshot(server);
        });

        registry.register("minecraft:ip_bans/clear", (server, _) -> {
            clearIpBans(server.ban0());
            return snapshot(server);
        });
    }

    private static void clearIpBans(final FidorialBanManager bans) {
        for (final BanEntry.Address entry : List.copyOf(bans.ipBans().toList())) {
            bans.pardon(entry.address());
        }
    }

    private static void addOne(final FidorialBanManager bans, final JsonObject entry) {
        if (!entry.has("ip")) {
            throw new IllegalArgumentException("incoming_ip_ban without 'ip' isn't supported yet (no player-IP resolution wired)");
        }
        final InetAddress ip = InetAddresses.forString(entry.get("ip").getAsString());
        final UUID source = JsonSchema.readSource(entry);
        bans.ban(new BanEntry.Address(ip, null, JsonSchema.readReason(entry), source, Instant.now(), JsonSchema.readExpires(entry)));
    }

    private static JsonArray snapshot(final FidorialServer server) {
        final JsonArray out = new JsonArray();
        server.ban0().ipBans().forEach(entry -> out.add(JsonSchema.ipBan(entry)));
        return out;
    }
}
