package dev.angelocordon.goldengolem.work;

/**
 * Answers "is this block one I want?" for {@link AreaScanner}. In the plugin it wraps a World
 * lookup; in tests it's a lambda over a fake world.
 */
@FunctionalInterface
public interface BlockProbe {

    boolean matches(int x, int y, int z);
}
