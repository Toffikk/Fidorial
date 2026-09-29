package fr.euphyllia.fidorial.server.network.protocol.packet.clientbound.utils;

import fr.euphyllia.fidorial.server.codecs.networking.NetworkCodec;
import fr.euphyllia.fidorial.server.codecs.networking.NetworkRecordCodec;
import fr.euphyllia.fidorial.server.network.PacketBuffer;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("unused")
public final class PositionData {

    private PositionData() {
        throw new UnsupportedOperationException("PositionData cannot be instantiated.");
    }

    public record Vec3D(double x, double y, double z) {

        public static final NetworkCodec<PacketBuffer, Vec3D> NETWORK_CODEC = NetworkRecordCodec.builder(Vec3D.class, PacketBuffer.class)
                .required("x", Vec3D::x, PacketBuffer::readDouble, PacketBuffer::writeDouble)
                .required("y", Vec3D::y, PacketBuffer::readDouble, PacketBuffer::writeDouble)
                .required("z", Vec3D::z, PacketBuffer::readDouble, PacketBuffer::writeDouble)
                .build();

        public void writeTo(final PacketBuffer buf) {
            NETWORK_CODEC.write(buf, this);
        }

        public static Vec3D readFrom(final PacketBuffer buf) {
            return NETWORK_CODEC.read(buf);
        }
    }

    public record DeltaVec3D(short x, short y, short z) {

        public static final NetworkCodec<PacketBuffer, DeltaVec3D> NETWORK_CODEC = NetworkRecordCodec.builder(DeltaVec3D.class, PacketBuffer.class)
                .required("x", DeltaVec3D::x, PacketBuffer::readShort, PacketBuffer::writeShort)
                .required("y", DeltaVec3D::y, PacketBuffer::readShort, PacketBuffer::writeShort)
                .required("z", DeltaVec3D::z, PacketBuffer::readShort, PacketBuffer::writeShort)
                .build();

        private static final double SCALE = 4096.0;
        private static final long MIN = -32768L;
        private static final long MAX = 32767L;

        public static @Nullable DeltaVec3D between(final Vec3D prev, final Vec3D current) {
            final long dx = encode(current.x()) - encode(prev.x());
            final long dy = encode(current.y()) - encode(prev.y());
            final long dz = encode(current.z()) - encode(prev.z());
            if (isTooBig(dx) || isTooBig(dy) || isTooBig(dz)) {
                return null;
            }
            return new DeltaVec3D((short) dx, (short) dy, (short) dz);
        }

        private static long encode(final double v) {
            return Math.round(v * SCALE);
        }

        private static boolean isTooBig(final long v) {
            return v < MIN || v > MAX;
        }

        public void writeTo(final PacketBuffer buf) {
            NETWORK_CODEC.write(buf, this);
        }

        public static DeltaVec3D readFrom(final PacketBuffer buf) {
            return NETWORK_CODEC.read(buf);
        }
    }

    public record VelocityVec3D(double x, double y, double z) {

        public static final NetworkCodec<PacketBuffer, VelocityVec3D> NETWORK_CODEC = NetworkCodec.of(
                buf -> {
                    final double[] velocity = buf.readLpVec3();
                    return new VelocityVec3D(velocity[0], velocity[1], velocity[2]);
                    },
                (buf, velocity) -> buf.writeLpVec3(velocity.x(), velocity.y(), velocity.z()));

        public void writeTo(final PacketBuffer buf) {
            NETWORK_CODEC.write(buf, this);
        }

        public static VelocityVec3D readFrom(final PacketBuffer buf) {
            return NETWORK_CODEC.read(buf);
        }
    }

    public record FloatRotation(float yaw, float pitch) {

        public static final NetworkCodec<PacketBuffer, FloatRotation> NETWORK_CODEC = NetworkRecordCodec.builder(FloatRotation.class, PacketBuffer.class)
                .required("yaw", FloatRotation::yaw, PacketBuffer::readFloat, PacketBuffer::writeFloat)
                .required("pitch", FloatRotation::pitch, PacketBuffer::readFloat, PacketBuffer::writeFloat)
                .build();

        public void writeTo(final PacketBuffer buf) {
            NETWORK_CODEC.write(buf, this);
        }

        public static FloatRotation readFrom(final PacketBuffer buf) {
            return NETWORK_CODEC.read(buf);
        }
    }

    public record AngleRotation(float yaw, float pitch) {

        public static final NetworkCodec<PacketBuffer, AngleRotation> NETWORK_CODEC = NetworkRecordCodec.builder(AngleRotation.class, PacketBuffer.class)
                .required("yaw", AngleRotation::yaw, NetworkCodec.writeOnly(PacketBuffer::writeAngle))
                .required("pitch", AngleRotation::pitch, NetworkCodec.writeOnly(PacketBuffer::writeAngle))
                .build();

        public void writeTo(final PacketBuffer buf) {
            NETWORK_CODEC.write(buf, this);
        }
    }

    public record PositionMoveRotationData(Vec3D position, Vec3D deltaMovement, FloatRotation rotation) {

        public static final NetworkCodec<PacketBuffer, PositionMoveRotationData> NETWORK_CODEC =
                NetworkRecordCodec.builder(PositionMoveRotationData.class, PacketBuffer.class)
                        .required("position", PositionMoveRotationData::position, Vec3D.NETWORK_CODEC)
                        .required("delta_movement", PositionMoveRotationData::deltaMovement, Vec3D.NETWORK_CODEC)
                        .required("rotation", PositionMoveRotationData::rotation, FloatRotation.NETWORK_CODEC)
                        .build();

        public void writeTo(final PacketBuffer buf) {
            NETWORK_CODEC.write(buf, this);
        }

        public static PositionMoveRotationData readFrom(final PacketBuffer buf) {
            return NETWORK_CODEC.read(buf);
        }
    }

    public record LinearPositionPath(Vec3D position) {

        private static final int LINEAR = 0; // 0 = linear (absolute), 1+ = stepped (interpolated multi-tick path)

        public static final NetworkCodec<PacketBuffer, LinearPositionPath> NETWORK_CODEC = NetworkCodec.writeOnly((buf, path) -> {
            buf.writeVarInt(LINEAR);
            Vec3D.NETWORK_CODEC.write(buf, path.position());
        });

        public void writeTo(final PacketBuffer buf) {
            NETWORK_CODEC.write(buf, this);
        }
    }
}
