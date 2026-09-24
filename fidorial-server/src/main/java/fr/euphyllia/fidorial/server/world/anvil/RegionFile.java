package fr.euphyllia.fidorial.server.world.anvil;

import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class RegionFile implements Closeable {

    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final int[] offsets = new int[RegionConstants.CHUNKS_PER_REGION];
    private final int[] sectorCounts = new int[RegionConstants.CHUNKS_PER_REGION];
    private final int[] timestamps = new int[RegionConstants.CHUNKS_PER_REGION];
    private boolean[] usedSectors = new boolean[0];
    private final ReentrantReadWriteLock headerLock = new ReentrantReadWriteLock();

    public RegionFile(final Path path) throws IOException {
        Files.createDirectories(path.getParent());
        this.raf = new RandomAccessFile(path.toFile(), "rw");
        this.channel = raf.getChannel();

        if (raf.length() < RegionConstants.HEADER_BYTES) {
            raf.setLength(RegionConstants.HEADER_BYTES);
        }

        if (raf.length() % RegionConstants.SECTOR_BYTES != 0) {
            final long padded = (raf.length() / RegionConstants.SECTOR_BYTES + 1) * RegionConstants.SECTOR_BYTES;
            raf.setLength(padded);
        }
        readHeader();
    }

    private void readHeader() throws IOException {
        raf.seek(0);
        for (int i = 0; i < RegionConstants.CHUNKS_PER_REGION; i++) {
            final int packed = raf.readInt();
            offsets[i] = packed >>> 8;
            sectorCounts[i] = packed & 0xFF;
        }
        for (int i = 0; i < RegionConstants.CHUNKS_PER_REGION; i++) {
            timestamps[i] = raf.readInt();
        }
        rebuildSectorMap();
    }

    private void rebuildSectorMap() {
        final int totalSectors;
        try {
            totalSectors = (int) (raf.length() / RegionConstants.SECTOR_BYTES);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        usedSectors = new boolean[Math.max(totalSectors, RegionConstants.HEADER_SECTORS)];
        usedSectors[0] = true;
        usedSectors[1] = true;
        for (int i = 0; i < RegionConstants.CHUNKS_PER_REGION; i++) {
            if (offsets[i] == 0 || sectorCounts[i] == 0) continue;
            for (int s = 0; s < sectorCounts[i]; s++) {
                final int sector = offsets[i] + s;
                if (sector < usedSectors.length) usedSectors[sector] = true;
            }
        }
    }

    private void readFully(final ByteBuffer buf, final long position) throws IOException {
        long pos = position;
        while (buf.hasRemaining()) {
            final int n = channel.read(buf, pos);
            if (n < 0) {
                throw new EOFException("Unexpected end of region file at position " + pos);
            }
            pos += n;
        }
    }

    private void writeFully(final ByteBuffer buf, final long position) throws IOException {
        long pos = position;
        while (buf.hasRemaining()) {
            pos += channel.write(buf, pos);
        }
    }

    public boolean hasChunk(final int chunkX, final int chunkZ) {
        final int i = RegionConstants.headerIndex(chunkX, chunkZ);
        headerLock.readLock().lock();
        try {
            return offsets[i] != 0 && sectorCounts[i] != 0;
        } finally {
            headerLock.readLock().unlock();
        }
    }

    public @Nullable CompoundBinaryTag readChunk(final int chunkX, final int chunkZ) throws IOException {
        final int i = RegionConstants.headerIndex(chunkX, chunkZ);
        final int offset;
        final int count;
        headerLock.readLock().lock();
        try {
            offset = offsets[i];
            count = sectorCounts[i];
        } finally {
            headerLock.readLock().unlock();
        }
        if (offset == 0 || count == 0) {
            return null;
        }

        final long base = (long) offset * RegionConstants.SECTOR_BYTES;

        final ByteBuffer headerBuf = ByteBuffer.allocate(5);
        readFully(headerBuf, base);
        headerBuf.flip();
        final int length = headerBuf.getInt();
        if (length <= 0) {
            return null;
        }
        final byte compression = headerBuf.get();

        final ByteBuffer payloadBuf = ByteBuffer.allocate(length - 1);
        readFully(payloadBuf, base + 5);
        payloadBuf.flip();
        final byte[] payload = new byte[payloadBuf.remaining()];
        payloadBuf.get(payload);

        final DataInputStream in =
                switch (compression) {
                    case RegionConstants.COMPRESSION_ZLIB ->
                            new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(payload)));
                    case RegionConstants.COMPRESSION_GZIP ->
                            new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(payload)));
                    case RegionConstants.COMPRESSION_NONE ->
                            new DataInputStream(new BufferedInputStream(new ByteArrayInputStream(payload)));
                    default ->
                            throw new IOException("Unsupported " + compression + " compression (external .mcc chunk?) for "
                                    + chunkX + "," + chunkZ);
                };
        try (in) {
            return BinaryTagIO.reader().readNamed((DataInput) in).getValue();
        }
    }

    public int timestamp(final int chunkX, final int chunkZ) {
        final int i = RegionConstants.headerIndex(chunkX, chunkZ);
        headerLock.readLock().lock();
        try {
            return timestamps[i];
        } finally {
            headerLock.readLock().unlock();
        }
    }

    public void writeChunk(final int chunkX, final int chunkZ, final CompoundBinaryTag chunk) throws IOException {
        final byte[] frame = buildFrame(chunk);
        final int neededSectors = (frame.length + RegionConstants.SECTOR_BYTES - 1) / RegionConstants.SECTOR_BYTES;
        if (neededSectors >= 256) {
            throw new IOException("Chunk " + chunkX + "," + chunkZ + " trop volumineux (" + neededSectors
                    + " secteurs) : nécessiterait un fichier .mcc externe");
        }

        final int i = RegionConstants.headerIndex(chunkX, chunkZ);
        final int start;
        headerLock.writeLock().lock();
        try {
            freeSectors(offsets[i], sectorCounts[i]);
            start = allocateSectors(neededSectors);
        } finally {
            headerLock.writeLock().unlock();
        }

        final ByteBuffer dataBuf = ByteBuffer.wrap(frame);
        writeFully(dataBuf, (long) start * RegionConstants.SECTOR_BYTES);

        final int pad = neededSectors * RegionConstants.SECTOR_BYTES - frame.length;
        if (pad > 0) {
            writeFully(ByteBuffer.allocate(pad), (long) start * RegionConstants.SECTOR_BYTES + frame.length);
        }

        headerLock.writeLock().lock();
        try {
            offsets[i] = start;
            sectorCounts[i] = neededSectors;
            timestamps[i] = (int) (System.currentTimeMillis() / 1000L);
            writeHeaderEntry(i);
        } finally {
            headerLock.writeLock().unlock();
        }
    }

    private byte[] buildFrame(final CompoundBinaryTag chunk) throws IOException {
        final ByteArrayOutputStream compressed = new ByteArrayOutputStream(8192);
        try (final DataOutputStream nbtOut = new DataOutputStream(new DeflaterOutputStream(compressed))) {
            BinaryTagIO.writer().writeNamed(Map.entry("", chunk), (DataOutput) nbtOut);
        }
        final byte[] data = compressed.toByteArray();

        final ByteArrayOutputStream frame = new ByteArrayOutputStream(data.length + 5);
        final DataOutputStream out = new DataOutputStream(frame);
        out.writeInt(data.length + 1);
        out.writeByte(RegionConstants.COMPRESSION_ZLIB);
        out.write(data);
        return frame.toByteArray();
    }

    private void freeSectors(final int start, final int count) {
        for (int s = 0; s < count; s++) {
            final int sector = start + s;
            if (sector >= RegionConstants.HEADER_SECTORS && sector < usedSectors.length) {
                usedSectors[sector] = false;
            }
        }
    }

    private int allocateSectors(final int count) throws IOException {
        int run = 0;
        int start = RegionConstants.HEADER_SECTORS;
        for (int s = RegionConstants.HEADER_SECTORS; s < usedSectors.length; s++) {
            if (!usedSectors[s]) {
                if (run == 0) start = s;
                if (++run == count) {
                    for (int k = 0; k < count; k++) usedSectors[start + k] = true;
                    return start;
                }
            } else {
                run = 0;
            }
        }
        // Pas assez d'espace : on ajoute des secteurs à la fin.
        final int newStart = usedSectors.length;
        final int newLength = newStart + count;
        usedSectors = Arrays.copyOf(usedSectors, newLength);
        for (int k = 0; k < count; k++) usedSectors[newStart + k] = true;
        raf.setLength((long) newLength * RegionConstants.SECTOR_BYTES);
        return newStart;
    }

    private void writeHeaderEntry(final int i) throws IOException {
        final ByteBuffer entry = ByteBuffer.allocate(4);
        entry.putInt((offsets[i] << 8) | (sectorCounts[i] & 0xFF));
        entry.flip();
        writeFully(entry, (long) i * 4);

        final ByteBuffer ts = ByteBuffer.allocate(4);
        ts.putInt(timestamps[i]);
        ts.flip();
        writeFully(ts, RegionConstants.SECTOR_BYTES + (long) i * 4);
    }

    @Override
    public void close() throws IOException {
        headerLock.writeLock().lock();
        try {
            raf.getFD().sync();
            raf.close();
        } finally {
            headerLock.writeLock().unlock();
        }
    }
}
