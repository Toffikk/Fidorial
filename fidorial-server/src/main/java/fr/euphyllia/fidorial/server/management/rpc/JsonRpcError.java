package fr.euphyllia.fidorial.server.management.rpc;

import com.google.gson.JsonElement;
import org.jspecify.annotations.Nullable;

public record JsonRpcError(int code, String message, @Nullable JsonElement data) {
    public static final int PARSE_ERROR = -32700;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
}
