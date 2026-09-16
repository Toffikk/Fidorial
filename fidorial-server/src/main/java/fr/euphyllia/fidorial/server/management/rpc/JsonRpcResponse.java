package fr.euphyllia.fidorial.server.management.rpc;

import com.google.gson.JsonElement;
import org.jspecify.annotations.Nullable;

public record JsonRpcResponse(String jsonRpc, @Nullable JsonElement id, @Nullable JsonElement result, @Nullable JsonRpcError error) {

    public static JsonRpcResponse ok(final @Nullable JsonElement id, final JsonElement result) {
        return new JsonRpcResponse("2.0", id, result, null);
    }
    public static JsonRpcResponse fail(final @Nullable JsonElement id, final JsonRpcError error) {
        return new JsonRpcResponse("2.0", id, null, error);
    }
}
