package fr.euphyllia.fidorial.server.management;

import net.kyori.adventure.key.Key;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GameRuleRegistry {

    public sealed interface Value permits BoolValue, IntValue {
    }
    public record BoolValue(boolean value) implements Value {
    }
    public record IntValue(int value) implements Value {
    }

    private final Map<Key, Value> rules = new ConcurrentHashMap<>();

    public GameRuleRegistry() {
        rules.put(Key.key("do_daylight_cycle"), new BoolValue(true));
        rules.put(Key.key("do_weather_cycle"), new BoolValue(true));
        rules.put(Key.key("keep_inventory"), new BoolValue(false));
        rules.put(Key.key("mob_griefing"), new BoolValue(true));
        rules.put(Key.key("random_tick_speed"), new IntValue(3));
    }

    public Map<Key, Value> all() {
        return Map.copyOf(rules);
    }

    public Value update(final Key key, final Value value) {
        rules.compute(key, (k, existing) -> {
            if (existing == null) {
                throw new IllegalArgumentException("Unknown game rule: " + k.asString());
            }
            if (existing.getClass() != value.getClass()) {
                throw new IllegalArgumentException(
                        "Game rule " + k.asString() + " is " + typeName(existing) + ", not " + typeName(value));
            }
            return value;
        });
        return rules.get(key);
    }

    private static String typeName(final Value v) {
        return v instanceof BoolValue ? "boolean" : "integer";
    }
}
