package fr.euphyllia.fidorial.server.entity;

import fr.euphyllia.fidorial.server.FidorialServer;
import fr.euphyllia.fidorial.server.entity.mob.AbstractMovingMob;
import fr.euphyllia.fidorial.server.entity.player.ServerPlayer;
import fr.euphyllia.fidorial.server.world.ServerWorld;
import fr.euphyllia.fidorial.server.world.ServerWorldManager;
import fr.fidorial.scheduler.RegionTickHandler;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.util.List;

public final class EntityTickHandler implements RegionTickHandler {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(EntityTickHandler.class);

    private final ServerWorldManager worldManager;
    private final FidorialServer server;

    public EntityTickHandler(final ServerWorldManager worldManager, final FidorialServer fidorialServer) {
        this.worldManager = worldManager;
        this.server = fidorialServer;
    }

    @Override
    public void tick(final Key worldId, final int sectionX, final int sectionZ, final long currentTick) {
        final ServerWorld world = worldManager.world(worldId);
        if (world == null) {
            return;
        }
        final EntityTracker tracker = server.entities().tracker();
        final List<ServerPlayer> players = server.players().online();

        for (final AbstractEntity entity : world.entityManager().inSection(sectionX, sectionZ)) {
            if (entity.isRemoved()) {
                continue;
            }
            try {
                entity.runScheduledTasks();
                if (!entity.isOwnedByCurrentThread()) {
                    continue; // a task moved or removed it
                }
                entity.tick(currentTick);
                if (entity instanceof final AbstractMovingMob mob) {
                    server.debug().entities().tick(mob);
                }
            } catch (final Throwable t) {
                LOGGER.error("Error during tick of {}", entity, t);
            }
            if (!players.isEmpty() && EntityTracker.shouldUpdate(entity, currentTick)) {
                try {
                    tracker.update(entity, players);
                } catch (final Throwable t) {
                    LOGGER.error("Error while tracking {}", entity, t);
                }
            }
        }
    }
}
