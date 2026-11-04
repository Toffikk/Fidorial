package fr.euphyllia.fidorial.testplugin.torch;

import net.kyori.adventure.key.Key;

/**
 * A block position in a given world.
 */
record PortalBlock(Key world, int x, int y, int z) {
}
