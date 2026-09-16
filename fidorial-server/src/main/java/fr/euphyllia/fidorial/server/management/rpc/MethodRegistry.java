package fr.euphyllia.fidorial.server.management.rpc;

import com.google.gson.JsonElement;
import fr.euphyllia.fidorial.server.FidorialServer;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MethodRegistry {

    @FunctionalInterface
    public interface MethodHandler {
        JsonElement handle(FidorialServer server, @Nullable JsonElement params);
    }

    private final Map<String, MethodHandler> methods = new ConcurrentHashMap<>();

    public void register(final String path, final MethodHandler handler) {
        methods.put(path, handler);
    }

    public @Nullable MethodHandler get(final String path) {
        return methods.get(path);
    }

    public java.util.Set<String> names() {
        return methods.keySet();
    }
}
