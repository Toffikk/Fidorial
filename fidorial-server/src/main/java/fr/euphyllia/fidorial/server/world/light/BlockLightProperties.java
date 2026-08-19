package fr.euphyllia.fidorial.server.world.light;

import fr.euphyllia.fidorial.server.registry.data.BlockStateLightProperties;
import fr.euphyllia.fidorial.server.world.chunk.BlockState;
import fr.euphyllia.fidorial.server.world.chunk.BlockState.LightProperties;
import fr.fidorial.world.block.BlockData;
import fr.fidorial.world.block.BlockRegistry;
import fr.fidorial.world.block.BlockType;
import fr.fidorial.world.block.Blocks;
import org.jspecify.annotations.Nullable;

public class BlockLightProperties {

    public static final int OPAQUE = 15;

    private static final LightProperties AIR_PROPS = new LightProperties(0, 0);
    private static final LightProperties UNKNOWN_PROPS = new LightProperties(OPAQUE, 0);

    public static int opacity(final BlockState state) {
        return propsOf(state).opacity();
    }

    public static boolean occludes(final BlockState state) {
        return propsOf(state).opacity() >= OPAQUE;
    }

    public static int emission(final BlockState state) {
        return propsOf(state).emission();
    }

    private static LightProperties propsOf(final BlockState state) {
        final LightProperties cached = state.lightProperties();
        if (cached != null) {
            return cached;
        }
        if (state.isAir()) {
            state.setLightProperties(AIR_PROPS);
            return AIR_PROPS;
        }

        final BlockRegistry registry;
        try {
            registry = Blocks.registry();
        } catch (final IllegalStateException _) {
            // registry not bootstrapped yet
            return UNKNOWN_PROPS;
        }

        final BlockData data = resolve(registry, state);
        final LightProperties props = data == null
                ? UNKNOWN_PROPS
                : new LightProperties(
                BlockStateLightProperties.opacity(data.networkId()),
                BlockStateLightProperties.emission(data.networkId()));

        state.setLightProperties(props);
        return props;
    }

    private static @Nullable BlockData resolve(final BlockRegistry registry, final BlockState state) {
        final BlockType type = registry.type(state.name()).orElse(null);
        return type == null ? null : type.dataOrNull(state.properties());
    }
}
