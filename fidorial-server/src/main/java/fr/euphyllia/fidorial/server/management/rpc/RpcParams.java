package fr.euphyllia.fidorial.server.management.rpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RpcParams {

    private final Map<String, JsonElement> byName;

    private RpcParams(final Map<String, JsonElement> byName) {
        this.byName = byName;
    }

    public static RpcParams of(final @Nullable JsonElement params, final String... orderedNames) {
        final Map<String, JsonElement> map = new LinkedHashMap<>();
        if (params == null || params.isJsonNull()) {
            return new RpcParams(map);
        }
        if (params.isJsonArray()) {
            final JsonArray array = params.getAsJsonArray();
            for (int i = 0; i < array.size() && i < orderedNames.length; i++) {
                map.put(orderedNames[i], array.get(i));
            }
        } else if (params.isJsonObject()) {
            final JsonObject object = params.getAsJsonObject();
            for (final String name : orderedNames) {
                if (object.has(name)) {
                    map.put(name, object.get(name));
                }
            }
        }
        return new RpcParams(map);
    }

    public @Nullable JsonElement get(final String name) {
        return byName.get(name);
    }

    public JsonElement require(final String name) {
        final JsonElement value = byName.get(name);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("Missing required param: " + name);
        }
        return value;
    }
}
