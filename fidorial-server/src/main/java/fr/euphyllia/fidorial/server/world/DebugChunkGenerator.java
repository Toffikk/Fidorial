package fr.euphyllia.fidorial.server.world;

import fr.euphyllia.fidorial.server.world.chunk.BlockState;
import fr.euphyllia.fidorial.server.world.chunk.BlockStateProperties;
import fr.euphyllia.fidorial.server.world.chunk.ChunkColumn;
import fr.fidorial.registry.keys.BlockTypeKeys;
import fr.fidorial.world.block.BlockRegistry;
import fr.fidorial.world.block.BlockType;
import net.kyori.adventure.key.Key;

import java.util.ArrayList;
import java.util.List;

public final class DebugChunkGenerator implements ChunkGenerator {

    private static final BlockState BARRIER = BlockState.of(BlockTypeKeys.BARRIER.key());
    private static final int DISPLAY_Y = 70;
    private static final int BARRIER_Y = 60;

    private final List<BlockState> states;
    private final int gridWidth;
    private final int gridHeight;
    private final int minY;
    private final int height;
    private final Key biome;

    private DebugChunkGenerator(final List<BlockState> states, final int minY, final int height, final Key biome) {
        this.states = states;
        this.gridWidth = (int) Math.ceil(Math.sqrt(states.size()));
        this.gridHeight = (int) Math.ceil(states.size() / (double) Math.max(1, gridWidth));
        this.minY = minY;
        this.height = height;
        this.biome = biome;
    }

    public static DebugChunkGenerator create(final BlockRegistry registry, final int minY, final int height, final Key biome) {
        final List<BlockState> flat = new ArrayList<>();
        for (final BlockType type : registry.types()) {
            final Key key = type.key();
            final BlockState[] chunkStates = BlockStateProperties.statesOf(key);
            if (chunkStates == null) {
                continue;
            }
            final int count = Math.min(chunkStates.length, type.stateCount());
            for (int ordinal = 0; ordinal < count; ordinal++) {
                flat.add(chunkStates[ordinal]);
            }
        }
        return new DebugChunkGenerator(flat, minY, height, biome);
    }

    @Override
    public ChunkColumn generate(final int chunkX, final int chunkZ) {
        final ChunkColumn chunk = new ChunkColumn(chunkX, chunkZ, minY, height, BlockState.of(BlockTypeKeys.AIR.key()), biome);
        final int baseX = chunkX << 4;
        final int baseZ = chunkZ << 4;
        for (int lx = 0; lx < 16; lx++) {
            final int worldX = baseX + lx;
            for (int lz = 0; lz < 16; lz++) {
                final int worldZ = baseZ + lz;
                chunk.setBlock(lx, BARRIER_Y, lz, BARRIER);
                chunk.setBlock(lx, DISPLAY_Y, lz, getBlockState(worldX, worldZ));
            }
        }

        return chunk;
    }

    private BlockState getBlockState(final int worldX, final int worldZ) {
        if (worldX <= 0 || worldZ <= 0 || worldX % 2 == 0 || worldZ % 2 == 0) {
            return BlockState.of(BlockTypeKeys.AIR.key());
        }
        final int gx = worldX / 2;
        final int gz = worldZ / 2;
        if (gx > gridWidth || gz > gridHeight) {
            return BlockState.of(BlockTypeKeys.AIR.key());
        }
        final int index = Math.abs(gx * gridWidth + gz);
        return index < states.size() ? states.get(index) : BlockState.of(BlockTypeKeys.AIR.key());
    }

    @Override
    public ChunkGeneratorConfig describeForSave() {
        return new ChunkGeneratorConfig.Debug();
    }

    @Override
    public int minY() {
        return this.minY;
    }

    @Override
    public int height() {
        return this.height;
    }
}
