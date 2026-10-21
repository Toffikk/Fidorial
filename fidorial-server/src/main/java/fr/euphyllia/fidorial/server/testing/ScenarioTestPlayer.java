package fr.euphyllia.fidorial.server.testing;

import fr.euphyllia.fidorial.server.FidorialServer;
import fr.euphyllia.fidorial.server.entity.player.ServerPlayer;
import fr.euphyllia.fidorial.server.network.ClientConnection;
import fr.euphyllia.fidorial.server.world.ServerWorld;
import fr.fidorial.entity.GameMode;
import fr.fidorial.entity.player.Player;
import fr.fidorial.entity.player.profile.PlayerProfile;
import fr.fidorial.inventory.EnderChestInventory;
import fr.fidorial.inventory.PlayerInventory;
import fr.fidorial.math.Location;
import fr.fidorial.world.ChunkPos;

import java.util.UUID;

final class ScenarioTestPlayer {

    static Player spawn(final FidorialServer server, final String name, final Location location, final GameMode gameMode) {
        final PlayerProfile profile = new PlayerProfile(UUID.nameUUIDFromBytes(("test:" + name).getBytes()), name);
        final ClientConnection connection = ClientConnection.mock(server);

        final ServerPlayer player = new ServerPlayer(
                server.entities().ids().allocate(),
                profile,
                new PlayerInventory(),
                new EnderChestInventory(),
                gameMode,
                connection,
                location);

        connection.setProfile(profile);
        connection.setPlayer(player);

        final ServerWorld world = (ServerWorld) location.world();
        final ChunkPos chunk = location.chunk();
        final Runnable join = () -> {
            world.addEntity(player);
            connection.bindMockPlayer(player);
            server.players().connect(connection);
            player.invalidatePermissions();
        };

        if (world.scheduler().isOwnedByCurrentThread(world.key(), chunk)) {
            join.run();
        } else if (!world.scheduler().execute(world.key(), chunk, join)) {
            throw new IllegalStateException("Could not schedule the spawn of " + name + " at " + location);
        }
        return player;
    }

    static void despawn(final FidorialServer server, final ServerPlayer player) {
        final ClientConnection connection = player.connection();
        connection.mockDisconnect();
        server.players().disconnect(connection);
    }
}
