package fr.euphyllia.fidorial.server.network.protocol.packet.clientbound.configuration;

import fr.euphyllia.fidorial.server.codecs.networking.NetworkCodec;
import fr.euphyllia.fidorial.server.codecs.networking.NetworkRecordCodec;
import fr.euphyllia.fidorial.server.datapack.known.KnownPack;
import fr.euphyllia.fidorial.server.network.PacketBuffer;
import fr.euphyllia.fidorial.server.network.protocol.catalog.ConfigurationClientboundPackets;
import fr.euphyllia.fidorial.server.network.protocol.packet.ClientboundCodecBackedPacket;
import net.kyori.adventure.key.Key;

import java.util.List;

public record ClientboundSelectKnownPacksPacket(List<KnownPack> knownPacks) implements ClientboundCodecBackedPacket<ClientboundSelectKnownPacksPacket> {

    private static final NetworkCodec<PacketBuffer, ClientboundSelectKnownPacksPacket> CODEC =
            NetworkRecordCodec.builder(ClientboundSelectKnownPacksPacket.class, PacketBuffer.class)
                    .required("known_packs", ClientboundSelectKnownPacksPacket::knownPacks,
                            KnownPack.NETWORK_CODEC.listOf(PacketBuffer::readVarInt, PacketBuffer::writeVarInt, Integer.MAX_VALUE)).build();

    @Override
    public Key name() {
        return ConfigurationClientboundPackets.SELECT_KNOWN_PACKS;
    }

    @Override
    public NetworkCodec<PacketBuffer, ClientboundSelectKnownPacksPacket> codec() {
        return CODEC;
    }
}
