package fr.euphyllia.fidorial.server.network.protocol.packet.serverbound.play;

import fr.euphyllia.fidorial.server.network.PacketBuffer;
import fr.euphyllia.fidorial.server.network.protocol.packet.listener.PlayPacketListener;
import fr.fidorial.protocol.PacketListener;
import fr.fidorial.protocol.ServerboundPacket;

// https://minecraft.wiki/w/Java_Edition_protocol/Packets#Player_Command
public record ServerboundPlayerCommandPacket(int entityId, int actionId, int jumpBoost) implements ServerboundPacket {

    public static final int LEAVE_BED = 0;
    public static final int START_SPRINTING = 1;
    public static final int STOP_SPRINTING = 2;
    public static final int START_JUMP_WITH_HORSE = 3;
    public static final int STOP_JUMP_WITH_HORSE = 4;
    public static final int OPEN_VEHICLE_INVENTORY = 5;
    public static final int START_FLYING_WITH_ELYTRA = 6;

    public static ServerboundPlayerCommandPacket read(final PacketBuffer buf) {
        final int entityId = buf.readVarInt();
        final int actionId = buf.readVarInt();
        final int jumpBoost = buf.readVarInt();
        return new ServerboundPlayerCommandPacket(entityId, actionId, jumpBoost);
    }

    @Override
    public void handle(final PacketListener listener) {
        ((PlayPacketListener) listener).handlePlayerCommand(this);
    }
}
