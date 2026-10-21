package fr.euphyllia.fidorial.server.entity;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Tasks bound to one entity, drained by its tick on whichever region owns it.
 * Every accepted task gets exactly one of its run or retired callbacks.
 */
public final class EntityTaskQueue {

    private record Incoming(Runnable task, @Nullable Runnable retired, long delayTicks) {
    }

    private record Scheduled(Runnable task, @Nullable Runnable retired, long executeAtTick, long sequence) {
        static final Comparator<Scheduled> ORDER = Comparator
                .comparingLong(Scheduled::executeAtTick)
                .thenComparingLong(Scheduled::sequence);
    }

    private final MultiThreadedQueue<Incoming> incoming = new MultiThreadedQueue<>();
    private final PriorityQueue<Scheduled> scheduled = new PriorityQueue<>(Scheduled.ORDER);
    private long tick;
    private long sequence;
    private boolean draining;
    private boolean retired;

    /**
     * {@return false if the queue is retired, in which case neither callback will run}
     */
    boolean submit(final Runnable task, final @Nullable Runnable retired, final long delayTicks) {
        return incoming.add(new Incoming(task, retired, Math.max(0L, delayTicks)));
    }

    synchronized boolean canRunInline() {
        transfer();
        return !retired && !draining && scheduled.isEmpty();
    }

    synchronized void runDue(final AbstractEntity owner) {
        if (retired) {
            return;
        }
        tick++;
        transfer();
        draining = true;
        try {
            Scheduled due;
            while (!retired && (due = scheduled.peek()) != null && due.executeAtTick() <= tick) {
                if (!owner.isOwnedByCurrentThread()) {
                    return; // moved or removed by a task: the rest stay queued for the next owner
                }
                scheduled.poll();
                try {
                    due.task().run();
                } catch (final Throwable t) {
                    AbstractEntity.LOGGER.error("Error in a task of {}", owner, t);
                }
            }
        } finally {
            draining = false;
        }
    }

    synchronized void retire(final AbstractEntity owner) {
        if (retired) {
            return;
        }
        retired = true;
        Scheduled pending;
        while ((pending = scheduled.poll()) != null) {
            runRetired(owner, pending.retired());
        }
        incoming.drain(next -> runRetired(owner, next.retired()), true);
    }

    private void transfer() {
        Incoming next;
        while ((next = incoming.poll()) != null) {
            scheduled.add(new Scheduled(next.task(), next.retired(), tick + next.delayTicks(), sequence++));
        }
    }

    private static void runRetired(final AbstractEntity owner, final @Nullable Runnable retired) {
        if (retired == null) {
            return;
        }
        try {
            retired.run();
        } catch (final Throwable t) {
            AbstractEntity.LOGGER.error("Error in a retired callback of {}", owner, t);
        }
    }
}
