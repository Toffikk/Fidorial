package fr.euphyllia.fidorial.server.management.json;

import com.google.gson.*;
import fr.euphyllia.fidorial.server.entity.player.ServerPlayer;
import fr.fidorial.entity.PlayerProfile;
import fr.fidorial.moderation.BanEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public final class JsonSchema {

    private JsonSchema() {
    }

    public static JsonObject player(final UUID id, final String name) {
        final JsonObject o = new JsonObject();
        o.addProperty("id", id.toString());
        o.addProperty("name", name);
        return o;
    }

    public static JsonObject player(final ServerPlayer p) {
        return player(p.uuid(), p.name());
    }

    public static JsonObject player(final PlayerProfile profile) {
        return player(profile.uuid(), profile.name());
    }

    public static @Nullable UUID playerId(final JsonObject player) {
        return player.has("id") ? UUID.fromString(player.get("id").getAsString()) : null;
    }

    public static @Nullable String playerName(final JsonObject player) {
        return player.has("name") ? player.get("name").getAsString() : null;
    }

    public static JsonObject message(final Component component) {
        final JsonObject o = new JsonObject();
        if (component instanceof final TranslatableComponent t) {
            o.addProperty("translatable", t.key());
            if (!t.arguments().isEmpty()) {
                final JsonArray params = new JsonArray();
                t.arguments().forEach(arg -> params.add(PlainTextComponentSerializer.plainText().serialize(arg.asComponent())));
                o.add("translatableParams", params);
            }
        } else {
            o.addProperty("literal", PlainTextComponentSerializer.plainText().serialize(component));
        }
        return o;
    }

    public static Component messageToComponent(final JsonObject message) {
        if (message.has("translatable")) {
            final String key = message.get("translatable").getAsString();
            TranslatableComponent result = Component.translatable(key);
            if (message.has("translatableParams")) {
                for (final JsonElement el : message.getAsJsonArray("translatableParams")) {
                    result = result.arguments(Component.text(el.getAsString()));
                }
            }
            return result;
        }
        if (message.has("literal")) {
            return Component.text(message.get("literal").getAsString());
        }
        return Component.empty();
    }

    public static JsonObject userBan(final BanEntry.Profile entry) {
        final JsonObject o = new JsonObject();
        o.add("player", player(entry.uuid(), entry.name() == null ? "" : entry.name()));
        if (entry.reason() != null) o.add("reason", message(entry.reason()));
        if (entry.source() != null) o.addProperty("source", entry.source().toString());
        if (entry.expires() != null) o.addProperty("expires", entry.expires().toString());
        return o;
    }

    public static JsonObject ipBan(final BanEntry.Address entry) {
        final JsonObject o = new JsonObject();
        o.addProperty("ip", entry.address().getHostAddress());
        if (entry.reason() != null) o.add("reason", message(entry.reason()));
        if (entry.source() != null) o.addProperty("source", entry.source().toString());
        if (entry.expires() != null) o.addProperty("expires", entry.expires().toString());
        return o;
    }

    public static @Nullable Component readReason(final JsonObject obj) {
        return obj.has("reason") ? messageToComponent(obj.getAsJsonObject("reason")) : null;
    }

    public static @Nullable UUID readSource(final JsonObject obj) {
        return obj.has("source") ? UUID.fromString(obj.get("source").getAsString()) : null;
    }

    public static @Nullable Instant readExpires(final JsonObject obj) {
        return obj.has("expires") ? Instant.parse(obj.get("expires").getAsString()) : null;
    }

    public static JsonObject version(final String name, final int protocol) {
        final JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("protocol", protocol);
        return o;
    }
}
