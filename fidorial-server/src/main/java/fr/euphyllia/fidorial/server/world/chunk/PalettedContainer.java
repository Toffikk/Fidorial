package fr.euphyllia.fidorial.server.world.chunk;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public final class PalettedContainer<T> {

    private final ObjectArrayList<T> palette = new ObjectArrayList<>();
    private final Object2IntOpenHashMap<T> lookup = new Object2IntOpenHashMap<>();
    private final int[] data;
    private final int minBits;
    private @Nullable T lastValue = null;
    private int lastIndex = -1;

    private final StampedLock lock = new StampedLock();

    private volatile Object[] paletteArray = new Object[0];

    public PalettedContainer(final int size, final int minBits, final T fill) {
        this.data = new int[size];
        this.minBits = minBits;
        this.lookup.defaultReturnValue(-1);
        indexOf(fill);
    }

    public static <T> PalettedContainer<T> fromNbt(final int size, final int minBits, final List<T> palette, final long @Nullable [] data) {
        final PalettedContainer<T> c = new PalettedContainer<>(size, minBits, palette.getFirst());
        for (int i = 1; i < palette.size(); i++) {
            c.indexOf(palette.get(i));
        }
        if (data != null && data.length > 0 && palette.size() > 1) {
            final int bits = BitPacking.bitsFor(palette.size(), minBits);
            final int[] indices = BitPacking.unpack(data, bits, size);
            System.arraycopy(indices, 0, c.data, 0, size);
        }
        return c;
    }

    private int indexOf(final T value) {
        if (value == lastValue) {
            return lastIndex;
        }
        final int i = lookup.getInt(value);
        if (i != -1) {
            lastValue = value;
            lastIndex = i;
            return i;
        }
        final int next = palette.size();
        palette.add(value);
        lookup.put(value, next);
        paletteArray = palette.toArray();
        lastValue = value;
        lastIndex = next;
        return next;
    }

    public void set(final int index, final T value) {
        final long stamp = lock.writeLock();
        try {
            data[index] = indexOf(value);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public T get(final int index) {
        final long stamp = lock.tryOptimisticRead();
        final Object[] pal = paletteArray;
        final int di = data[index];
        if (stamp != 0L && lock.validate(stamp) && di >= 0 && di < pal.length) {
            @SuppressWarnings("unchecked")
            final T v = (T) pal[di];
            return v;
        }

        final long rs = lock.readLock();
        try {
            return palette.get(data[index]);
        } finally {
            lock.unlockRead(rs);
        }
    }

    public T getAndSet(final int index, final T value) {
        final long stamp = lock.writeLock();
        try {
            final T previous = palette.get(data[index]);
            data[index] = indexOf(value);
            return previous;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public List<T> palette() {
        @SuppressWarnings("unchecked")
        final T[] pal = (T[]) paletteArray;
        return List.of(pal);
    }

    public boolean isSingleValue() {
        return paletteArray.length == 1;
    }

    public int bitsPerEntry() {
        return BitPacking.bitsFor(paletteArray.length, minBits);
    }

    public long[] packedGlobal(final int bits, final ToIntFunction<T> mapper) {
        long stamp = lock.tryOptimisticRead();
        Object[] pal = paletteArray;
        int[] snapshot = data.clone();
        if (stamp == 0L || !lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                pal = paletteArray;
                snapshot = data.clone();
            } finally {
                lock.unlockRead(stamp);
            }
        }

        @SuppressWarnings("unchecked")
        final T[] typedPal = (T[]) pal;
        final int[] global = new int[snapshot.length];
        for (int i = 0; i < snapshot.length; i++) {
            global[i] = mapper.applyAsInt(typedPal[snapshot[i]]);
        }
        return BitPacking.pack(global, bits);
    }

    public long @Nullable [] packedData() {
        long stamp = lock.tryOptimisticRead();
        int paletteSize = paletteArray.length;
        int[] snapshot = data.clone();
        if (stamp == 0L || !lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                paletteSize = paletteArray.length;
                snapshot = data.clone();
            } finally {
                lock.unlockRead(stamp);
            }
        }

        if (paletteSize == 1) return null;
        return BitPacking.pack(snapshot, BitPacking.bitsFor(paletteSize, minBits));
    }

    public boolean contains(final Predicate<T> test) {
        final Object[] pal = paletteArray;
        for (final Object value : pal) {
            @SuppressWarnings("unchecked")
            final T typed = (T) value;
            if (test.test(typed)) return true;
        }
        return false;
    }

    public PalettedContainerSnapshot<T> snapshot() {
        long stamp = lock.tryOptimisticRead();
        Object[] pal = paletteArray;
        int[] copy = data.clone();
        if (stamp == 0L || !lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                pal = paletteArray;
                copy = data.clone();
            } finally {
                lock.unlockRead(stamp);
            }
        }

        @SuppressWarnings("unchecked")
        final T[] typedPal = (T[]) pal;
        return new PalettedContainerSnapshot<>(Arrays.asList(typedPal), copy, minBits);
    }

    public record PalettedContainerSnapshot<T>(List<T> palette, int[] data, int minBits) {
        public T get(final int index) {
            return palette.get(data[index]);
        }
    }
}
