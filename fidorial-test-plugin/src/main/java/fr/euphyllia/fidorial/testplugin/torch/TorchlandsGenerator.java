package fr.euphyllia.fidorial.testplugin.torch;

import fr.euphyllia.fidorial.testplugin.terrain.PerlinNoise;
import fr.euphyllia.fidorial.testplugin.terrain.TestBiomes;
import fr.euphyllia.fidorial.testplugin.terrain.TestDimensionTypes;
import fr.fidorial.registry.RegistryKey;
import fr.fidorial.registry.TypedKey;
import fr.fidorial.world.biome.BiomeDefinition;
import fr.fidorial.world.block.BlockType;
import fr.fidorial.world.dimension.DimensionTypeDefinition;
import fr.fidorial.world.generation.GeneratedChunk;
import fr.fidorial.world.generation.GenerationDescriptor;
import fr.fidorial.world.generation.WorldGenerator;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Map;

/**
 * One huge cave with no skylight, lit by torches, glowstone, shroomlight and lava.
 *
 * <p>Four zones, each its own biome: sulfur springs and copper grottos with golden water lakes,
 * crimson hollows and magma rifts with lava lakes. Sulfur veins run through all the rock. The
 * spawn platform around the origin carries a lit return portal.</p>
 */
public final class TorchlandsGenerator implements WorldGenerator {

    static final int PLATFORM_Y = 70;

    /**
     * Where a destination portal is searched down from: below the lowest possible ceiling.
     */
    static final int SCAN_TOP = 114;

    /**
     * The spawn platform portal: two wide, three tall, running along X at z = 0.
     */
    static final Portal RETURN_PORTAL = Portal.rectangle(TorchPortals.WORLD, 0, PLATFORM_Y + 2, 0, 1, 0, 2, 3);

    private static final int FLOOR_BASE = 56;
    private static final int FLOOR_AMPLITUDE = 30;
    private static final int CEILING_BASE = 150;
    private static final int CEILING_AMPLITUDE = 26;
    private static final int FLUID_LEVEL = 46;
    private static final int DEEPSLATE_LEVEL = 30;
    private static final int MIN_GAP = 10;
    private static final int PLATFORM_RADIUS = 5;
    private static final double PILLAR_THRESHOLD = 0.45;
    private static final double ZONE_THRESHOLD = 0.12;

    private static final TypedKey<BlockType> BEDROCK = block("bedrock");
    private static final TypedKey<BlockType> DEEPSLATE = block("deepslate");
    private static final TypedKey<BlockType> TUFF = block("tuff");
    private static final TypedKey<BlockType> SAND = block("sand");
    private static final TypedKey<BlockType> WATER = block("water");
    private static final TypedKey<BlockType> LAVA = block("lava");
    private static final TypedKey<BlockType> TORCH = block("torch");
    private static final TypedKey<BlockType> LIGHT = block("light");
    private static final TypedKey<BlockType> GLOWSTONE = block("glowstone");
    private static final TypedKey<BlockType> SULFUR = block("sulfur");
    private static final TypedKey<BlockType> POLISHED_SULFUR = block("polished_sulfur");
    private static final TypedKey<BlockType> SULFUR_BRICKS = block("sulfur_bricks");
    private static final TypedKey<BlockType> POTENT_SULFUR = block("potent_sulfur");
    private static final TypedKey<BlockType> SULFUR_SPIKE = block("sulfur_spike");
    private static final TypedKey<BlockType> RAW_COPPER_BLOCK = block("raw_copper_block");
    private static final TypedKey<BlockType> DEEPSLATE_COPPER_ORE = block("deepslate_copper_ore");
    private static final TypedKey<BlockType> COPPER_BLOCK = block("copper_block");
    private static final TypedKey<BlockType> EXPOSED_COPPER = block("exposed_copper");
    private static final TypedKey<BlockType> WEATHERED_COPPER = block("weathered_copper");
    private static final TypedKey<BlockType> OXIDIZED_COPPER = block("oxidized_copper");
    private static final TypedKey<BlockType> NETHERRACK = block("netherrack");
    private static final TypedKey<BlockType> CRIMSON_NYLIUM = block("crimson_nylium");
    private static final TypedKey<BlockType> NETHER_WART_BLOCK = block("nether_wart_block");
    private static final TypedKey<BlockType> SHROOMLIGHT = block("shroomlight");
    private static final TypedKey<BlockType> CRIMSON_ROOTS = block("crimson_roots");
    private static final TypedKey<BlockType> CRIMSON_FUNGUS = block("crimson_fungus");
    private static final TypedKey<BlockType> CINNABAR = block("cinnabar");
    private static final TypedKey<BlockType> MAGMA_BLOCK = block("magma_block");
    private static final TypedKey<BlockType> BASALT = block("basalt");
    private static final TypedKey<BlockType> SMOOTH_BASALT = block("smooth_basalt");
    private static final TypedKey<BlockType> BLACKSTONE = block("blackstone");
    private static final TypedKey<BlockType> GILDED_BLACKSTONE = block("gilded_blackstone");

    private static final List<String> SPIKE_UP = List.of("tip", "frustum", "base");

    private final long seed;
    private final PerlinNoise floorNoise;
    private final PerlinNoise ceilingNoise;
    private final PerlinNoise detailNoise;
    private final PerlinNoise pillarNoise;
    private final PerlinNoise heatNoise;
    private final PerlinNoise mineralNoise;

    public TorchlandsGenerator(final long seed) {
        this.seed = seed;
        this.floorNoise = new PerlinNoise(seed);
        this.ceilingNoise = new PerlinNoise(seed * 31 + 7);
        this.detailNoise = new PerlinNoise(seed * 17 + 3);
        this.pillarNoise = new PerlinNoise(seed * 13 + 11);
        this.heatNoise = new PerlinNoise(seed * 7 + 5);
        this.mineralNoise = new PerlinNoise(seed * 3 + 1);
    }

    private enum Zone {
        SULFUR_SPRINGS(TestBiomes.SULFUR_SPRINGS.key(), SAND, TUFF, SULFUR, SAND, WATER, GLOWSTONE),
        COPPER_GROTTO(TestBiomes.COPPER_GROTTO.key(), DEEPSLATE_COPPER_ORE, DEEPSLATE, DEEPSLATE, SAND, WATER, GLOWSTONE),
        CRIMSON_HOLLOW(TestBiomes.CRIMSON_HOLLOW.key(), NETHERRACK, NETHERRACK, NETHER_WART_BLOCK, CINNABAR, LAVA, SHROOMLIGHT),
        MAGMA_RIFTS(TestBiomes.MAGMA_RIFTS.key(), BASALT, BLACKSTONE, BASALT, SMOOTH_BASALT, LAVA, GLOWSTONE);

        private final TypedKey<BiomeDefinition> biome;
        private final TypedKey<BlockType> filler;
        private final TypedKey<BlockType> rock;
        private final TypedKey<BlockType> ceiling;
        private final TypedKey<BlockType> shore;
        private final TypedKey<BlockType> fluid;
        private final TypedKey<BlockType> lamp;

        Zone(final TypedKey<BiomeDefinition> biome, final TypedKey<BlockType> filler, final TypedKey<BlockType> rock,
             final TypedKey<BlockType> ceiling, final TypedKey<BlockType> shore, final TypedKey<BlockType> fluid,
             final TypedKey<BlockType> lamp) {
            this.biome = biome;
            this.filler = filler;
            this.rock = rock;
            this.ceiling = ceiling;
            this.shore = shore;
            this.fluid = fluid;
            this.lamp = lamp;
        }
    }

    @Override
    public void generate(final GeneratedChunk chunk) {
        final int minY = chunk.minY();
        final int topY = minY + chunk.height() - 1;
        final int baseX = chunk.chunkX() << 4;
        final int baseZ = chunk.chunkZ() << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int worldX = baseX + x;
                final int worldZ = baseZ + z;
                final boolean platform = Math.abs(worldX) <= PLATFORM_RADIUS && Math.abs(worldZ) <= PLATFORM_RADIUS;
                final Zone zone = platform ? Zone.SULFUR_SPRINGS : zoneAt(worldX, worldZ);
                final int floor = platform ? PLATFORM_Y : floorHeight(worldX, worldZ);
                final int ceiling = platform
                        ? Math.max(ceilingHeight(worldX, worldZ), PLATFORM_Y + 30)
                        : ceilingHeight(worldX, worldZ);
                final boolean solid = !platform
                        && (ceiling - floor < MIN_GAP || pillarNoise.fbm(worldX * 0.03, worldZ * 0.03, 2) > PILLAR_THRESHOLD);
                final long hash = hash(worldX, 0, worldZ);

                chunk.setBlock(x, minY, z, BEDROCK);
                chunk.setBlock(x, topY, z, BEDROCK);
                for (int y = minY + 1; y < topY; y++) {
                    if (solid || y < floor - 3 || y > ceiling) {
                        chunk.setBlock(x, y, z, rock(zone, worldX, y, worldZ));
                    } else if (y < floor) {
                        chunk.setBlock(x, y, z, zone.filler);
                    } else if (y == floor) {
                        chunk.setBlock(x, y, z, platform ? platformTop(worldX, worldZ) : floorTop(zone, floor, hash));
                    } else if (y == ceiling) {
                        chunk.setBlock(x, y, z, zone.ceiling);
                    } else if (y <= FLUID_LEVEL) {
                        chunk.setBlock(x, y, z, zone.fluid);
                    }
                }

                if (!solid && !platform) {
                    decorateFloor(chunk, zone, x, z, floor, hash);
                    decorateCeiling(chunk, zone, x, z, ceiling, hash >>> 20);
                }

                for (int y = minY; y <= topY; y++) {
                    chunk.setBiome(x, y, z, zone.biome);
                }
            }
        }

        placeReturnPortal(chunk, baseX, baseZ);
    }

    private Zone zoneAt(final int worldX, final int worldZ) {
        final double heat = heatNoise.fbm(worldX * 0.0025, worldZ * 0.0025, 2);
        if (heat > ZONE_THRESHOLD) {
            return Zone.SULFUR_SPRINGS;
        }
        if (heat < -ZONE_THRESHOLD) {
            return Zone.MAGMA_RIFTS;
        }
        return mineralNoise.fbm(worldX * 0.004, worldZ * 0.004, 2) > 0 ? Zone.CRIMSON_HOLLOW : Zone.COPPER_GROTTO;
    }

    private int floorHeight(final int worldX, final int worldZ) {
        final double hills = floorNoise.fbm(worldX * 0.006, worldZ * 0.006, 4);
        final double detail = detailNoise.fbm(worldX * 0.04, worldZ * 0.04, 2);
        return FLOOR_BASE + (int) Math.round(hills * FLOOR_AMPLITUDE + detail * 6);
    }

    private int ceilingHeight(final int worldX, final int worldZ) {
        final double dome = ceilingNoise.fbm(worldX * 0.005, worldZ * 0.005, 3);
        final double drips = detailNoise.fbm(worldX * 0.09, worldZ * 0.09, 2);
        return CEILING_BASE + (int) Math.round(dome * CEILING_AMPLITUDE - Math.abs(drips) * 8);
    }

    /**
     * Deep rock: deepslate low down, the zone's rock above, with sulfur veins everywhere.
     */
    private TypedKey<BlockType> rock(final Zone zone, final int worldX, final int y, final int worldZ) {
        final long hash = hash(worldX, y, worldZ);
        if (Math.floorMod(hash, 17) == 0) {
            return SULFUR;
        }
        if (y < DEEPSLATE_LEVEL) {
            return DEEPSLATE;
        }
        return switch (zone) {
            case COPPER_GROTTO -> Math.floorMod(hash, 13) == 1 ? RAW_COPPER_BLOCK : zone.rock;
            case CRIMSON_HOLLOW -> Math.floorMod(hash, 9) == 1 ? CINNABAR : zone.rock;
            case MAGMA_RIFTS -> Math.floorMod(hash, 15) == 1 ? MAGMA_BLOCK : zone.rock;
            case SULFUR_SPRINGS -> Math.floorMod(hash, 5) == 1 ? SULFUR : zone.rock;
        };
    }

    private static TypedKey<BlockType> floorTop(final Zone zone, final int floor, final long hash) {
        if (floor <= FLUID_LEVEL + 1) {
            return zone.shore;
        }
        final int pick = Math.floorMod(hash >>> 12, 8);
        return switch (zone) {
            case SULFUR_SPRINGS -> pick == 0 ? POTENT_SULFUR : SULFUR;
            case COPPER_GROTTO -> switch (pick & 3) {
                case 0 -> EXPOSED_COPPER;
                case 1 -> WEATHERED_COPPER;
                case 2 -> OXIDIZED_COPPER;
                default -> pick == 7 ? COPPER_BLOCK : RAW_COPPER_BLOCK;
            };
            case CRIMSON_HOLLOW -> pick < 6 ? CRIMSON_NYLIUM : NETHERRACK;
            case MAGMA_RIFTS -> pick < 3 ? MAGMA_BLOCK : pick < 6 ? BLACKSTONE : GILDED_BLACKSTONE;
        };
    }

    private static TypedKey<BlockType> platformTop(final int worldX, final int worldZ) {
        return Math.abs(worldX) == PLATFORM_RADIUS || Math.abs(worldZ) == PLATFORM_RADIUS ? SULFUR_BRICKS : POLISHED_SULFUR;
    }

    private static void decorateFloor(final GeneratedChunk chunk, final Zone zone, final int x, final int z,
                                      final int floor, final long hash) {
        if (floor <= FLUID_LEVEL) {
            if (zone.fluid == WATER && Math.floorMod(hash, 41) == 0) {
                chunk.setBlock(x, floor, z, GLOWSTONE);
            }
            return;
        }
        if (floor == FLUID_LEVEL + 1) {
            return;
        }
        if (Math.floorMod(hash, 43) == 0) {
            chunk.setBlock(x, floor + 1, z, TORCH);
            return;
        }
        if (Math.floorMod(hash, 179) == 3) {
            final int height = 1 + Math.floorMod(hash >>> 8, 3);
            for (int y = floor + 1; y <= floor + height; y++) {
                chunk.setBlock(x, y, z, zone.lamp);
            }
            chunk.setBlock(x, floor + height + 1, z, TORCH);
            return;
        }
        final int roll = Math.floorMod(hash >>> 4, 24);
        switch (zone) {
            case SULFUR_SPRINGS -> {
                if (roll < 3) {
                    spike(chunk, x, floor + 1, z, 1 + Math.floorMod(hash >>> 16, 3), "up");
                }
            }
            case COPPER_GROTTO -> {
                if (roll == 0) {
                    chunk.setBlock(x, floor + 1, z, RAW_COPPER_BLOCK);
                }
            }
            case CRIMSON_HOLLOW -> {
                if (roll < 5) {
                    chunk.setBlock(x, floor + 1, z, roll == 0 ? CRIMSON_FUNGUS : CRIMSON_ROOTS);
                }
            }
            case MAGMA_RIFTS -> {
                if (roll == 0) {
                    chunk.setBlock(x, floor + 1, z, MAGMA_BLOCK);
                }
            }
        }
    }

    private static void decorateCeiling(final GeneratedChunk chunk, final Zone zone, final int x, final int z,
                                        final int ceiling, final long hash) {
        if (Math.floorMod(hash, 37) == 5) {
            chunk.setBlock(x, ceiling - 1, z, zone.lamp);
            if (Math.floorMod(hash >>> 6, 2) == 0) {
                chunk.setBlock(x, ceiling - 2, z, zone.lamp);
            }
        } else if (zone == Zone.SULFUR_SPRINGS && Math.floorMod(hash, 7) == 1) {
            spike(chunk, x, ceiling - 1, z, 1 + Math.floorMod(hash >>> 10, 3), "down");
        }
    }

    /**
     * A sulfur spike grown from {@code y} upwards or downwards, thickest at its root.
     */
    private static void spike(final GeneratedChunk chunk, final int x, final int y, final int z,
                              final int length, final String direction) {
        final int step = direction.equals("up") ? 1 : -1;
        for (int i = 0; i < length; i++) {
            final String thickness = SPIKE_UP.get(length - 1 - i);
            chunk.setBlock(x, y + i * step, z, SULFUR_SPIKE, Map.of(
                    "thickness", thickness,
                    "vertical_direction", direction,
                    "waterlogged", "false"));
        }
    }

    private static void placeReturnPortal(final GeneratedChunk chunk, final int baseX, final int baseZ) {
        for (final PortalBlock block : RETURN_PORTAL.frame()) {
            placeIfInChunk(chunk, baseX, baseZ, block, TORCH);
        }
        for (final PortalBlock block : RETURN_PORTAL.interior()) {
            placeIfInChunk(chunk, baseX, baseZ, block, LIGHT);
        }
    }

    private static void placeIfInChunk(final GeneratedChunk chunk, final int baseX, final int baseZ,
                                       final PortalBlock block, final TypedKey<BlockType> type) {
        final int x = block.x() - baseX;
        final int z = block.z() - baseZ;
        if (x >= 0 && x < 16 && z >= 0 && z < 16) {
            chunk.setBlock(x, block.y(), z, type);
        }
    }

    private long hash(final int worldX, final int y, final int worldZ) {
        long h = seed ^ (worldX * 0x9E3779B97F4A7C15L) ^ (worldZ * 0xC2B2AE3D27D4EB4FL) ^ (y * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }

    private static TypedKey<BlockType> block(final String name) {
        return TypedKey.withRegistry(RegistryKey.BLOCK, name);
    }

    @Override
    public DimensionTypeDefinition dimensionType() {
        return TestDimensionTypes.TORCHLANDS;
    }

    @Override
    public GenerationDescriptor describeForSave() {
        return GenerationDescriptor.noise(TestDimensionTypes.TORCHLANDS.key().key(), Key.key("fidorial", "torchlands"));
    }
}
