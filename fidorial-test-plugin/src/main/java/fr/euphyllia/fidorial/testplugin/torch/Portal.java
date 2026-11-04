package fr.euphyllia.fidorial.testplugin.torch;

import fr.fidorial.math.Location;
import fr.fidorial.world.World;
import net.kyori.adventure.key.Key;

import java.util.HashSet;
import java.util.Set;

/**
 * A rectangular torch portal: its frame (corners excluded) and the interior it fills.
 *
 * @param anchor the lowest interior cell on the negative side
 * @param dx     1 when the portal runs along X, 0 otherwise
 * @param dz     1 when the portal runs along Z, 0 otherwise
 */
record Portal(Set<PortalBlock> frame, Set<PortalBlock> interior, PortalBlock anchor, int dx, int dz) {

    Portal {
        frame = Set.copyOf(frame);
        interior = Set.copyOf(interior);
    }

    static Portal rectangle(final Key world, final int startX, final int y, final int startZ,
                            final int dx, final int dz, final int width, final int height) {
        final Set<PortalBlock> frame = new HashSet<>();
        final Set<PortalBlock> interior = new HashSet<>();
        for (int i = -1; i <= width; i++) {
            for (int j = -1; j <= height; j++) {
                final boolean side = i == -1 || i == width;
                final boolean cap = j == -1 || j == height;
                if (side && cap) {
                    continue;
                }
                final PortalBlock block = new PortalBlock(world, startX + i * dx, y + j, startZ + i * dz);
                if (side || cap) {
                    frame.add(block);
                } else {
                    interior.add(block);
                }
            }
        }
        return new Portal(frame, interior, new PortalBlock(world, startX, y, startZ), dx, dz);
    }

    /**
     * {@return where a traveller lands: two blocks in front of the portal, facing it}
     */
    Location arrival(final World world) {
        return Location.of(world,
                anchor.x() + dz * 2 + 0.5,
                anchor.y() - 1,
                anchor.z() + dx * 2 + 0.5,
                dx == 1 ? 180.0F : 90.0F,
                0.0F);
    }
}
