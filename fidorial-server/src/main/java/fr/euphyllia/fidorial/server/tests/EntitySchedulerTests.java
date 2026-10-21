package fr.euphyllia.fidorial.server.tests;

import fr.euphyllia.fidorial.server.FidorialServer;
import fr.euphyllia.fidorial.server.entity.player.ServerPlayer;
import fr.euphyllia.fidorial.server.world.ServerWorld;
import fr.fidorial.entity.Entity;
import fr.fidorial.entity.GameMode;
import fr.fidorial.entity.player.Player;
import fr.fidorial.math.Location;
import fr.fidorial.testing.ScenarioTestHelper;
import fr.fidorial.testing.annotation.ScenarioTest;
import fr.fidorial.world.World;
import fr.fidorial.world.WorldSpec;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unused")
public final class EntitySchedulerTests {

    private static final int TELEPORTS = 20;
    private static final int TASKS = 20;

    @ScenarioTest(timeoutTicks = 200)
    public static void queuedTeleportsApplyInOrder(final ScenarioTestHelper helper) {
        final World home = helper.world();
        final World away = crossWorldDestination();
        final Player player = helper.summonPlayer("TeleportStorm", spawn(home), GameMode.SURVIVAL);

        final List<CompletableFuture<Boolean>> futures = new CopyOnWriteArrayList<>();
        final AtomicReference<Location> last = new AtomicReference<>();

        helper.sequence()
                .execute(() -> onForeignThread(() -> {
                    final ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int i = 0; i < TELEPORTS; i++) {
                        final World world = (i & 1) == 0 ? away : home;
                        final Location to = Location.of(world,
                                random.nextInt(-512, 512) + 0.5, 70, random.nextInt(-512, 512) + 0.5, 0f, 0f);
                        last.set(to);
                        futures.add(player.teleport(to));
                    }
                }))
                .waitUntil(() -> futures.size() == TELEPORTS && futures.stream().allMatch(CompletableFuture::isDone),
                        "Expected every teleport future to complete")
                .execute(() -> helper.assertTrue(futures.stream().allMatch(future -> future.join()),
                        "Expected every teleport to succeed"))
                .waitUntil(() -> player.location().equals(last.get()),
                        "Expected the last queued teleport to be the one applied")
                .execute(() -> helper.assertTrue(homes(player) == 1,
                        "Expected the player in exactly one world, found " + homes(player)))
                .build();
    }

    @ScenarioTest(timeoutTicks = 100)
    public static void tasksQueuedBehindTransferRunInDestination(final ScenarioTestHelper helper) {
        final World away = crossWorldDestination();
        final Player player = helper.summonPlayer("InTransit", spawn(helper.world()), GameMode.SURVIVAL);
        final Location destination = Location.of(away, 5.5, 70, 5.5, 0f, 0f);

        final AtomicInteger accepted = new AtomicInteger();
        final AtomicInteger callbacks = new AtomicInteger();
        final AtomicInteger doubled = new AtomicInteger();
        final AtomicInteger inDestination = new AtomicInteger();
        final AtomicInteger notOwned = new AtomicInteger();

        helper.sequence()
                .execute(() -> onForeignThread(() -> {
                    player.teleport(destination);
                    for (int i = 0; i < TASKS; i++) {
                        final AtomicBoolean once = new AtomicBoolean();
                        final boolean queued = player.execute(() -> {
                            if (!once.compareAndSet(false, true)) doubled.incrementAndGet();
                            if (!player.isOwnedByCurrentThread()) notOwned.incrementAndGet();
                            if (player.world().equals(away)) inDestination.incrementAndGet();
                            callbacks.incrementAndGet();
                        }, () -> {
                            if (!once.compareAndSet(false, true)) doubled.incrementAndGet();
                            callbacks.incrementAndGet();
                        });
                        if (queued) accepted.incrementAndGet();
                    }
                }))
                .waitUntil(() -> accepted.get() == TASKS && callbacks.get() == TASKS,
                        "Expected every accepted task to get a callback")
                .execute(() -> {
                    helper.assertTrue(doubled.get() == 0, "Expected no task to get two callbacks, got " + doubled.get());
                    helper.assertTrue(notOwned.get() == 0, "Expected every task to run on the owning region, " + notOwned.get() + " did not");
                    helper.assertTrue(inDestination.get() == TASKS,
                            "Expected tasks queued behind the teleport to run in the destination, got " + inDestination.get());
                })
                .build();
    }

    @ScenarioTest(timeoutTicks = 40)
    public static void teleportFromInsideTheQueueDoesNotNest(final ScenarioTestHelper helper) {
        final World world = helper.world();
        final Player player = helper.summonPlayer("NoNesting", spawn(world), GameMode.SURVIVAL);
        final Location target = Location.of(world, 8.5, 70, 8.5, 0f, 0f);

        final AtomicReference<CompletableFuture<Boolean>> future = new AtomicReference<>();
        final AtomicBoolean ranInline = new AtomicBoolean();

        helper.sequence()
                .execute(() -> onForeignThread(() -> player.execute(() -> {
                    final CompletableFuture<Boolean> teleport = player.teleport(target);
                    ranInline.set(teleport.isDone());
                    future.set(teleport);
                })))
                .waitUntil(() -> future.get() != null && future.get().isDone(), "Expected the teleport to complete")
                .execute(() -> {
                    helper.assertTrue(!ranInline.get(), "Expected a teleport issued while draining to queue, not run inline");
                    helper.assertTrue(future.get().join(), "Expected the teleport to succeed");
                })
                .waitUntil(() -> player.location().equals(target), "Expected the player at the target")
                .build();
    }

    @ScenarioTest(timeoutTicks = 60)
    public static void removalRetiresPendingTasks(final ScenarioTestHelper helper) {
        final ServerPlayer player = (ServerPlayer) helper.summonPlayer("Removed", spawn(helper.world()), GameMode.SURVIVAL);

        final AtomicInteger ran = new AtomicInteger();
        final AtomicInteger retired = new AtomicInteger();

        helper.sequence()
                .execute(() -> {
                    for (int i = 0; i < TASKS; i++) {
                        player.executeDelayed(ran::incrementAndGet, retired::incrementAndGet, 1_000L);
                    }
                    player.execute(() -> {
                        ((ServerWorld) player.world()).removeEntity(player);
                        player.remove();
                    });
                })
                .waitUntil(player::isRemoved, "Expected the player to be removed")
                .execute(() -> {
                    helper.assertTrue(ran.get() == 0 && retired.get() == TASKS,
                            "Expected pending tasks to be retired, ran=" + ran.get() + " retired=" + retired.get());
                    helper.assertTrue(!player.execute(() -> {
                    }, null), "Expected submits after removal to be refused");
                })
                .build();
    }

    @ScenarioTest(timeoutTicks = 100)
    public static void respawnDuringCrossWorldTeleport(final ScenarioTestHelper helper) {
        final World home = helper.world();
        final World away = crossWorldDestination();
        final Player player = helper.summonPlayer("RespawnRace", spawn(home), GameMode.SURVIVAL);

        final AtomicReference<CompletableFuture<Boolean>> teleport = new AtomicReference<>();
        final AtomicReference<CompletableFuture<Boolean>> respawn = new AtomicReference<>();

        helper.sequence()
                .execute(() -> {
                    teleport.set(player.teleport(Location.of(away, 5.5, 70, 5.5, 0f, 0f)));
                    player.kill(player);
                    respawn.set(player.respawn());
                })
                .waitUntil(() -> teleport.get().isDone() && respawn.get().isDone(),
                        "Expected both the teleport and the respawn to complete")
                .execute(() -> helper.assertTrue(homes(player) == 1,
                        "Expected the player in exactly one world, found " + homes(player)))
                .build();
    }

    private static int homes(final Entity entity) {
        int count = 0;
        for (final ServerWorld world : FidorialServer.getInstance().worlds().manager().worlds()) {
            if (world.entityManager().byUuid(entity.uuid()) == entity) {
                count++;
            }
        }
        return count;
    }

    private static Location spawn(final World world) {
        return Location.of(world, 0.5, 65, 0.5, 0f, 0f);
    }

    private static void onForeignThread(final Runnable action) {
        final Thread thread = new Thread(action, "scheduler-test-foreign-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private static World crossWorldDestination() {
        final FidorialServer server = FidorialServer.getInstance();
        final Key key = Key.key("scenario_test", "cross_world_destination");
        final World world = server.worlds().manager().world(key);
        return world != null ? world : server.worlds().create(WorldSpec.builder(key).build());
    }
}
