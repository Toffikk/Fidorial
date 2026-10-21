package fr.fidorial.scheduler;

import org.jspecify.annotations.Nullable;

/**
 * Something bound to a region of the world, on whose thread tasks can be scheduled: an
 * {@linkplain fr.fidorial.entity.Entity entity} or a {@linkplain fr.fidorial.world.Chunk chunk}.
 * <p>
 * Tasks follow the source: a task scheduled for an entity runs on whichever region owns the entity
 * when it runs, never while it is between worlds. Every accepted task gets exactly one of its run or
 * retired callbacks.
 *
 * @see RegionizedScheduler
 * @since 0.1.0
 */
public interface SchedulerSource {

    /**
     * Schedules a task to run on the region thread owning this source.
     *
     * <p>{@code retired} runs instead of {@code task} if the source is removed first, on the thread
     * removing it; it must not touch world state.</p>
     *
     * @param task    the task to run
     * @param retired the callback run if the source is removed first
     * @return {@code false} if the source is removed, in which case neither callback runs
     * @since 0.1.0
     */
    boolean execute(Runnable task, @Nullable Runnable retired);

    /**
     * Schedules a task to run on the region thread owning this source, after a delay.
     *
     * @param task       the task to run
     * @param retired    the callback run if the source is removed first
     * @param delayTicks the minimum number of ticks to wait before running the task
     * @return {@code false} if the source is removed, in which case neither callback runs
     * @see #execute(Runnable, Runnable)
     * @since 0.1.0
     */
    boolean executeDelayed(Runnable task, @Nullable Runnable retired, long delayTicks);

    /**
     * Schedules a task with no retired callback.
     *
     * @param task the task to run
     * @return {@code false} if the source is removed and the task was not scheduled
     * @see #execute(Runnable, Runnable)
     * @since 0.1.0
     */
    default boolean execute(final Runnable task) {
        return execute(task, null);
    }

    /**
     * Schedules a delayed task with no retired callback.
     *
     * @param task       the task to run
     * @param delayTicks the minimum number of ticks to wait before running the task
     * @return {@code false} if the source is removed and the task was not scheduled
     * @see #executeDelayed(Runnable, Runnable, long)
     * @since 0.1.0
     */
    default boolean executeDelayed(final Runnable task, final long delayTicks) {
        return executeDelayed(task, null, delayTicks);
    }

    /**
     * Checks whether the calling thread may safely touch this source.
     *
     * @return {@code true} if the calling thread is the region thread owning this source
     * @since 0.1.0
     */
    boolean isOwnedByCurrentThread();
}
