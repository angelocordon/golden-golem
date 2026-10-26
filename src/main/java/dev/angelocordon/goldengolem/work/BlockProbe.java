package dev.angelocordon.goldengolem.work;

/**
 * Answers "is this block one I want?" for {@link AreaScanner}. In the plugin it wraps a World
 * lookup; in tests it's a lambda over a fake world.
 *
 * <p>The scanner passes raw coordinates and calls this hundreds of times per tick on the main
 * thread, so an implementation must:
 *
 * <ul>
 *   <li>return false for positions in unloaded chunks (check
 *       {@code world.isChunkLoaded(x >> 4, z >> 4)} first) and <b>never load a chunk</b>; a plain
 *       {@code world.getBlockAt(x, y, z)} would sync-load it;
 *   <li>return false for {@code y} outside {@code world.getMinHeight()} (inclusive) to
 *       {@code world.getMaxHeight()} (exclusive);
 *   <li>stay cheap: no allocation-heavy work, no I/O.
 * </ul>
 */
@FunctionalInterface
public interface BlockProbe {

    boolean matches(int x, int y, int z);
}
