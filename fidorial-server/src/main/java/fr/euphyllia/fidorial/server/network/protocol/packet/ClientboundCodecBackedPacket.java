package fr.euphyllia.fidorial.server.network.protocol.packet;

import fr.euphyllia.fidorial.server.codecs.networking.NetworkCodec;
import fr.euphyllia.fidorial.server.network.PacketBuffer;

/**
 * A clientbound packet written entirely by its {@link #codec()}.
 *
 * @param <P> the implementing packet type itself
 */
public interface ClientboundCodecBackedPacket<P extends ClientboundCodecBackedPacket<P>> extends ClientboundPacket {

    NetworkCodec<PacketBuffer, P> codec();

    @Override
    @SuppressWarnings("unchecked")
    default void write(final PacketBuffer buf) {
        codec().write(buf, (P) this);
    }
}
