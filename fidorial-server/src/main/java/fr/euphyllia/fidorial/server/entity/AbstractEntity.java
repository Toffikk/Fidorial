package fr.euphyllia.fidorial.server.entity;

import fr.euphyllia.fidorial.server.FidorialServer;
import fr.euphyllia.fidorial.server.entity.mob.AbstractMob;
import fr.euphyllia.fidorial.server.network.ClientConnection;
import fr.euphyllia.fidorial.server.network.protocol.packet.ClientboundPacket;
import fr.euphyllia.fidorial.server.network.protocol.packet.clientbound.play.ClientboundAddEntityPacket;
import fr.euphyllia.fidorial.server.network.protocol.packet.clientbound.play.ClientboundEntityPositionSyncPacket;
import fr.euphyllia.fidorial.server.network.protocol.packet.clientbound.utils.LocationPositionData;
import fr.euphyllia.fidorial.server.network.protocol.packet.clientbound.utils.PositionData;
import fr.euphyllia.fidorial.server.world.ServerWorld;
import fr.fidorial.entity.Entity;
import fr.fidorial.entity.EntityType;
import fr.fidorial.math.Location;
import fr.fidorial.world.ChunkPos;
import fr.fidorial.world.World;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

public abstract class AbstractEntity implements Entity {

    public static final ComponentLogger LOGGER = ComponentLogger.logger(AbstractEntity.class);

    private final int entityId;
    private final EntityType type;
    private final AtomicBoolean removed = new AtomicBoolean(false);
    private UUID uuid;
    private volatile Location location;
    private final EntityTaskQueue tasks = new EntityTaskQueue();
    private volatile boolean inWorld;

    protected AbstractEntity(final int entityId, final UUID uuid, final EntityType type, final Location location) {
        this.entityId = entityId;
        this.uuid = uuid;
        this.type = type;
        this.location = location;
    }

    @Override
    public final int entityId() {
        return entityId;
    }

    @Override
    public UUID uuid() {
        return uuid;
    }

    @Override
    public Component displayName() {
        return Component.translatable("entity." + type.key().asString().replace(':', '.'));
    }

    public final void restoreUuid(final UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public final EntityType type() {
        return type;
    }

    @Override
    public final World world() {
        return location.world();
    }

    @Override
    public final Location location() {
        return location;
    }

    public void setLocation(final Location location) {
        this.location = location;
    }

    @Override
    public final boolean isRemoved() {
        return removed.get();
    }

    final void setInWorld(final boolean inWorld) {
        this.inWorld = inWorld;
    }

    final void runScheduledTasks() {
        tasks.runDue(this);
    }

    public final void retireTasks() {
        tasks.retire(this);
    }

    @Override
    public FidorialServer server() {
        return FidorialServer.getInstance();
    }

    @Override
    public final void remove() {
        if (removed.compareAndSet(false, true)) {
            tasks.retire(this);
            onRemoved();
        }
    }

    protected void onRemoved() {
    }

    public void tick(final long currentTick) {
    }

    public final void sendToTrackers(final ClientboundPacket packet) {
        server().entities().tracker().sendToViewers(this, packet);
    }

    public void sendSpawnPackets(final ClientConnection connection) {
        connection.send(ClientboundAddEntityPacket.of(this));
    }

    @Override
    public final boolean equals(final Object o) {
        return o instanceof final AbstractEntity other && other.entityId == entityId;
    }

    @Override
    public final int hashCode() {
        return Integer.hashCode(entityId);
    }

    @Override
    public String toString() {
        return type.key() + "#" + entityId;
    }

    @Override
    public CompletableFuture<Boolean> teleport(final Location location) {
        if (isRemoved() || !(location.world() instanceof final ServerWorld target)) {
            return CompletableFuture.completedFuture(false);
        }
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        final boolean scheduled = runOrExecute(() -> {
            if (!(world() instanceof final ServerWorld from)) {
                result.complete(false);
                return;
            }
            final ChunkPos fromChunk = chunk();

            if (from == target) {
                try {
                    setLocation(location);
                    target.entityMoved(this, fromChunk, location.chunk());
                    finishTeleport(location);
                    result.complete(true);
                } catch (final Exception exception) {
                    LOGGER.error("An error occurred while teleporting the entity : ", exception);
                    result.complete(false);
                }
                return;
            }

            from.removeEntity(this);
            if (this instanceof AbstractMob) {
                server().workers().regionizer().removeTicket(from.key(), fromChunk);
            }

            final ChunkPos destChunk = location.chunk();
            final boolean arrivalScheduled = target.scheduler().execute(target.key(), destChunk, () -> {
                try {
                    setLocation(location);
                    target.addEntity(this);
                    if (this instanceof AbstractMob) {
                        server().workers().regionizer().addTicket(target.key(), destChunk);
                    }
                    finishTeleport(location);
                    result.complete(true);
                } catch (final Exception exception) {
                    LOGGER.error("An error occurred while teleporting the entity : ", exception);
                    result.complete(false);
                }
            });

            if (!arrivalScheduled) {
                LOGGER.warn("{} was removed from {} but could not be scheduled to arrive in {}", this, from.key(), target.key());
                retireTasks();
                result.complete(false);
            }
        }, () -> result.complete(false));

        if (!scheduled) {
            result.complete(false);
        }

        return result;
    }

    private void finishTeleport(final Location location) {
        sendToTrackers(new ClientboundEntityPositionSyncPacket(
                entityId(),
                new PositionData.LinearPositionPath(LocationPositionData.vec3(location)),
                LocationPositionData.floatRotation(location),
                false));
        server().entities().tracker().update(this, server().players().online());
    }

    @Override
    public HoverEvent<HoverEvent.ShowEntity> asHoverEvent(final UnaryOperator<HoverEvent.ShowEntity> op) {
        return HoverEvent.showEntity(op.apply(HoverEvent.ShowEntity.showEntity(type().key(), uuid(), displayName())));
    }

    @Override
    public final boolean execute(final Runnable task, final @Nullable Runnable retired) {
        return tasks.submit(task, retired, 0L);
    }

    @Override
    public final boolean executeDelayed(final Runnable task, final @Nullable Runnable retired, final long delayTicks) {
        return tasks.submit(task, retired, delayTicks);
    }

    /**
     * Runs the task now if the current thread owns this entity and nothing is queued ahead of it,
     * otherwise schedules it.
     */
    @ApiStatus.Internal // plugins can shoot themselves in the foot with this
    public final boolean runOrExecute(final Runnable task, final @Nullable Runnable retired) {
        if (isOwnedByCurrentThread() && tasks.canRunInline()) {
            task.run();
            return true;
        }
        return execute(task, retired);
    }

    @Override
    public boolean isOwnedByCurrentThread() {
        return inWorld && !isRemoved() && server().scheduler().isOwnedByCurrentThread(world().key(), chunk());
    }
}
