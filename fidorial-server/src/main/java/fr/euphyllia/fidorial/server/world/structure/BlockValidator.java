package fr.euphyllia.fidorial.server.world.structure;

import fr.euphyllia.fidorial.server.world.chunk.BlockState;
import fr.fidorial.registry.RegistryKey;
import fr.fidorial.registry.TypedKey;
import net.kyori.adventure.key.Key;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@FunctionalInterface
public interface BlockValidator {

    @Nullable BlockState resolve(Key name, Map<String, String> properties);

    static BlockValidator permissive() {
        return (name, properties) -> BlockState.withProperties(TypedKey.withRegistry(RegistryKey.BLOCK, name), properties);
    }
}
