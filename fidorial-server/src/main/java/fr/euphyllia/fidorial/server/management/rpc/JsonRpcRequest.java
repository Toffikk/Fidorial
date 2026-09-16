package fr.euphyllia.fidorial.server.management.rpc;

import com.google.gson.JsonElement;
import org.jspecify.annotations.Nullable;

public record JsonRpcRequest(String jsonRpc, @Nullable JsonElement id, String method, @Nullable JsonElement params) {
}
