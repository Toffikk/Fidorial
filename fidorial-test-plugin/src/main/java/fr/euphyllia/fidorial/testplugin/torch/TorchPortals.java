package fr.euphyllia.fidorial.testplugin.torch;

import fr.euphyllia.fidorial.testplugin.TestPlugin;
import fr.fidorial.entity.player.Player;
import fr.fidorial.event.EventBus;
import fr.fidorial.event.block.BlockBreakEvent;
import fr.fidorial.event.block.BlockPlaceEvent;
import fr.fidorial.event.player.PlayerMoveEvent;
import fr.fidorial.event.server.ServerStartedEvent;
import fr.fidorial.math.Location;
import fr.fidorial.math.Position;
import fr.fidorial.world.ChunkPos;
import fr.fidorial.world.World;
import fr.fidorial.world.WorldSpec;
import fr.fidorial.world.block.BlockData;
import fr.fidorial.world.block.Blocks;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Torch portals to the Torchlands.
 *
 * <p>Build a nether-portal-shaped frame out of torches (corners optional), then place a redstone
 * torch on the bottom row inside it. The interior fills with invisible light blocks. Portals link
 * by coordinates like nether portals: walking in lands you at the portal nearest the same X/Z in
 * the other world, and one is built there if there is none within {@value #LINK_RADIUS} blocks.
 * Breaking a frame torch puts the portal out.</p>
 *
 * <p>Lit portals are only kept in memory, so they go dark (but keep their light blocks) across a
 * restart; the Torchlands spawn portal is re-registered on every start.</p>
 */
public final class TorchPortals {

    public static final Key WORLD = Key.key("fidorial", "torchlands");

    private static final long SEED = 20260716L;

    private static final Set<Key> FRAME_BLOCKS = Set.of(
            Key.key("torch"), Key.key("wall_torch"), Key.key("soul_torch"), Key.key("soul_wall_torch"));
    private static final Set<Key> IGNITERS = Set.of(Key.key("redstone_torch"), Key.key("redstone_wall_torch"));
    private static final Set<Key> NOT_GROUND = Set.of(Key.key("water"), Key.key("lava"), Key.key("light"));

    private static final int MIN_WIDTH = 2;
    private static final int MIN_HEIGHT = 3;
    private static final int MAX_SIZE = 21;
    private static final int LINK_RADIUS = 16;
    private static final int HEADROOM = 5;

    private static final long COOLDOWN_MILLIS = 3_000L;

    private final TestPlugin plugin;
    private final Map<PortalBlock, Portal> byInterior = new ConcurrentHashMap<>();
    private final Map<UUID, Key> homes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public TorchPortals(final TestPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(final EventBus events) {
        events.subscribe(ServerStartedEvent.class, _ -> createWorld());
        events.subscribe(BlockPlaceEvent.class, this::onPlace);
        events.subscribe(BlockBreakEvent.class, this::onBreak);
        events.subscribe(PlayerMoveEvent.class, this::onMove);
    }

    private void createWorld() {
        if (plugin.server().worlds().get(WORLD).isEmpty()) {
            plugin.server().worlds().create(WorldSpec.builder(WORLD)
                    .seed(SEED)
                    .generator(new TorchlandsGenerator(SEED))
                    .build());
        }
        track(TorchlandsGenerator.RETURN_PORTAL);
    }

    private void onPlace(final BlockPlaceEvent event) {
        final BlockData placed = event.blockData();
        if (placed == null || !IGNITERS.contains(placed.key().key())) {
            return;
        }
        final World world = event.world();
        final Position at = event.position();
        final Portal portal = detect(world, at.blockX(), at.blockY(), at.blockZ());
        if (portal == null) {
            return;
        }

        event.setCancelled(true);
        fill(world, portal, stateId("minecraft:light[level=15]"));
        track(portal);
        event.player().playSound(Sound.sound(Key.key("block.respawn_anchor.charge"), Sound.Source.BLOCK, 1.0F, 1.2F));
        plugin.msg(event.player(), "<gold>[TestPlugin] The torch portal flares to life!</gold>");
    }

    private void onBreak(final BlockBreakEvent event) {
        final Position at = event.position();
        final PortalBlock broken = new PortalBlock(event.world().key(), at.blockX(), at.blockY(), at.blockZ());
        byInterior.values().stream()
                .filter(portal -> portal.frame().contains(broken))
                .findFirst()
                .ifPresent(portal -> {
                    portal.interior().forEach(byInterior::remove);
                    fill(event.world(), portal, stateId("minecraft:air"));
                    event.player().playSound(Sound.sound(Key.key("block.fire.extinguish"), Sound.Source.BLOCK, 1.0F, 1.0F));
                });
    }

    private void onMove(final PlayerMoveEvent event) {
        final Location to = event.to();
        final Key world = to.world().key();
        final Portal feet = byInterior.get(new PortalBlock(world, to.blockX(), to.blockY(), to.blockZ()));
        final Portal portal = feet != null
                ? feet
                : byInterior.get(new PortalBlock(world, to.blockX(), to.blockY() + 1, to.blockZ()));
        if (portal == null) {
            return;
        }

        final Player player = event.player();
        final long now = System.currentTimeMillis();
        final Long until = cooldowns.get(player.uuid());
        if (until != null && until > now) {
            return;
        }
        cooldowns.put(player.uuid(), now + COOLDOWN_MILLIS);

        final Key destination;
        if (world.equals(WORLD)) {
            final Key home = homes.get(player.uuid());
            destination = home != null ? home : plugin.server().worlds().defaultWorld().orElse(Key.key("overworld"));
        } else {
            homes.put(player.uuid(), world);
            destination = WORLD;
        }
        travel(player, portal, destination);
    }

    /**
     * Sends a player to the portal linked to {@code from} in another world, building it if needed.
     */
    private void travel(final Player player, final Portal from, final Key destinationKey) {
        final World destination = plugin.server().worlds().get(destinationKey).orElse(null);
        if (destination == null) {
            plugin.msg(player, "<red>[TestPlugin] " + destinationKey.asString() + " is not loaded.</red>");
            return;
        }
        final PortalBlock anchor = from.anchor();
        destination.scheduler().execute(destinationKey, ChunkPos.fromBlock(anchor.x(), anchor.z()), () -> {
            final Portal linked = nearby(destinationKey, anchor.x(), anchor.z());
            if (linked != null) {
                player.teleport(linked.arrival(destination));
                return;
            }
            build(destination, anchor.x(), anchor.z()).thenAccept(built -> {
                track(built);
                player.teleport(built.arrival(destination));
            });
        });
    }

    private @Nullable Portal nearby(final Key world, final int x, final int z) {
        for (final Map.Entry<PortalBlock, Portal> entry : byInterior.entrySet()) {
            final PortalBlock block = entry.getKey();
            if (block.world().equals(world)
                    && Math.abs(block.x() - x) <= LINK_RADIUS
                    && Math.abs(block.z() - z) <= LINK_RADIUS) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Builds a lit 2x3 portal along X on the first ground found below the world's scan ceiling,
     * on a small platform with room to step out.
     */
    private static CompletableFuture<Portal> build(final World world, final int x, final int z) {
        final int ground = findGround(world, x, z);
        final Portal portal = Portal.rectangle(world.key(), x, ground + 2, z, 1, 0, 2, 3);

        final Map<PortalBlock, Integer> plan = new HashMap<>();
        final int floor = stateId("minecraft:polished_sulfur");
        final int air = stateId("minecraft:air");
        for (int bx = x - 1; bx <= x + 2; bx++) {
            for (int bz = z - 2; bz <= z + 2; bz++) {
                plan.put(new PortalBlock(world.key(), bx, ground, bz), floor);
                for (int by = ground + 1; by <= ground + HEADROOM; by++) {
                    plan.put(new PortalBlock(world.key(), bx, by, bz), air);
                }
            }
        }
        final int torch = stateId("minecraft:torch");
        final int light = stateId("minecraft:light[level=15]");
        portal.frame().forEach(block -> plan.put(block, torch));
        portal.interior().forEach(block -> plan.put(block, light));

        final List<CompletableFuture<Boolean>> writes = new ArrayList<>(plan.size());
        plan.forEach((block, state) ->
                writes.add(world.setBlockStateId(Position.block(block.x(), block.y(), block.z()), state)));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).thenApply(_ -> portal);
    }

    private static int findGround(final World world, final int x, final int z) {
        final int top = world.key().equals(WORLD)
                ? TorchlandsGenerator.SCAN_TOP
                : world.minY() + world.height() - HEADROOM - 2;
        for (int y = top; y > world.minY(); y--) {
            if (isGround(world, x, y, z) && hasHeadroom(world, x, y, z)) {
                return y;
            }
        }
        return world.minY() + world.height() / 2;
    }

    private static boolean hasHeadroom(final World world, final int x, final int y, final int z) {
        for (int above = 1; above <= HEADROOM; above++) {
            if (!isAir(world, x, y + above, z)) {
                return false;
            }
        }
        return true;
    }

    private void track(final Portal portal) {
        portal.interior().forEach(block -> byInterior.put(block, portal));
    }

    private static void fill(final World world, final Portal portal, final int stateId) {
        for (final PortalBlock block : portal.interior()) {
            world.setBlockStateId(Position.block(block.x(), block.y(), block.z()), stateId);
        }
    }

    /**
     * Finds the portal whose bottom interior row contains the given block, along X then Z.
     */
    private static @Nullable Portal detect(final World world, final int x, final int y, final int z) {
        final Portal alongX = detect(world, x, y, z, 1, 0);
        return alongX != null ? alongX : detect(world, x, y, z, 0, 1);
    }

    private static @Nullable Portal detect(final World world, final int x, final int y, final int z,
                                           final int dx, final int dz) {
        if (!isFrame(world, x, y - 1, z)) {
            return null;
        }

        int toStart = 0;
        while (toStart < MAX_SIZE && isAir(world, x - (toStart + 1) * dx, y, z - (toStart + 1) * dz)) {
            toStart++;
        }
        final int startX = x - toStart * dx;
        final int startZ = z - toStart * dz;

        int width = 0;
        while (width <= MAX_SIZE && isAir(world, startX + width * dx, y, startZ + width * dz)) {
            width++;
        }
        int height = 0;
        while (height <= MAX_SIZE && isAir(world, startX, y + height, startZ)) {
            height++;
        }
        if (width < MIN_WIDTH || width > MAX_SIZE || height < MIN_HEIGHT || height > MAX_SIZE) {
            return null;
        }

        final Portal candidate = Portal.rectangle(world.key(), startX, y, startZ, dx, dz, width, height);
        for (final PortalBlock block : candidate.frame()) {
            if (!isFrame(world, block.x(), block.y(), block.z())) {
                return null;
            }
        }
        for (final PortalBlock block : candidate.interior()) {
            if (!isAir(world, block.x(), block.y(), block.z())) {
                return null;
            }
        }
        return candidate;
    }

    private static boolean isFrame(final World world, final int x, final int y, final int z) {
        return world.blockKeyAt(Position.block(x, y, z))
                .map(type -> FRAME_BLOCKS.contains(type.key()))
                .orElse(false);
    }

    private static boolean isGround(final World world, final int x, final int y, final int z) {
        return world.blockKeyAt(Position.block(x, y, z))
                .map(type -> !NOT_GROUND.contains(type.key()))
                .orElse(false);
    }

    private static boolean isAir(final World world, final int x, final int y, final int z) {
        return world.blockKeyAt(Position.block(x, y, z)).isEmpty();
    }

    private static int stateId(final String state) {
        return Objects.requireNonNull(Blocks.data(state), state).networkId();
    }
}
