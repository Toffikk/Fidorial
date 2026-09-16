package fr.euphyllia.fidorial.server.permission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OperatorList {

    public static final int DEFAULT_PERMISSION_LEVEL = 4;

    private static final ComponentLogger LOGGER = ComponentLogger.logger(OperatorList.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<UUID, Entry> operators = new ConcurrentHashMap<>();

    public OperatorList(final Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (final Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            final List<Entry> entries = GSON.fromJson(reader, new TypeToken<List<Entry>>() {}.getType());
            operators.clear();
            if (entries != null) {
                for (final Entry entry : entries) {
                    operators.put(entry.uuid, entry.normalized());
                }
            }
            LOGGER.info("There are currently {} op", operators.size());
        } catch (final Exception e) {
            LOGGER.error("Unable to read {}", file, e);
        }
    }

    public synchronized void save() {
        try (final Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(new ArrayList<>(operators.values()), writer);
        } catch (final IOException e) {
            LOGGER.error("Unable to save {}", file, e);
        }
    }

    public boolean isOp(final UUID uuid) {
        return operators.containsKey(uuid);
    }

    public boolean setOp(final UUID uuid, final String name, final boolean value) {
        return setOp(uuid, name, value, DEFAULT_PERMISSION_LEVEL, false);
    }

    public boolean setOp(
            final UUID uuid,
            final String name,
            final boolean value,
            final int permissionLevel,
            final boolean bypassesPlayerLimit
    ) {
        Objects.requireNonNull(uuid, "uuid");
        final boolean[] changed = {false};

        if (value) {
            operators.compute(uuid, (id, existing) -> {
                final Entry next = new Entry(id, name, permissionLevel, bypassesPlayerLimit);
                changed[0] = existing == null || !existing.equals(next);
                return next;
            });
        } else {
            changed[0] = operators.remove(uuid) != null;
        }

        if (changed[0]) {
            save();
        }
        return changed[0];
    }

    /**
     * @return the current permission level for an op, or {@link #DEFAULT_PERMISSION_LEVEL}
     * if {@code uuid} is not currently an op
     */
    public int permissionLevel(final UUID uuid) {
        final Entry entry = operators.get(uuid);
        return entry == null ? DEFAULT_PERMISSION_LEVEL : entry.permissionLevel;
    }

    public boolean bypassesPlayerLimit(final UUID uuid) {
        final Entry entry = operators.get(uuid);
        return entry != null && entry.bypassesPlayerLimit;
    }

    /**
     * @return an immutable snapshot of the current operator list
     */
    public List<Entry> entries() {
        return List.copyOf(operators.values());
    }

    public void clear() {
        if (operators.isEmpty()) {
            return;
        }
        operators.clear();
        save();
    }

    public record Entry(UUID uuid, String name, int permissionLevel, boolean bypassesPlayerLimit) {

        Entry(final UUID uuid, final String name) {
            this(uuid, name, 0, false);
        }

        Entry normalized() {
            return permissionLevel == 0
                    ? new Entry(uuid, name, DEFAULT_PERMISSION_LEVEL, bypassesPlayerLimit)
                    : this;
        }
    }
}
