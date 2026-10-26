package dev.angelocordon.goldengolem.util;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * A block in a specific world, stored by world UUID so it survives world renames. This is the
 * format the golem's chest and work center are persisted in.
 */
public record BlockLocation(UUID world, BlockPos pos) {

    public static BlockLocation of(Block block) {
        return new BlockLocation(block.getWorld().getUID(), BlockPos.of(block));
    }

    public static BlockLocation of(Location location) {
        return new BlockLocation(location.getWorld().getUID(), BlockPos.of(location));
    }

    /** Encodes as {@code worldUuid;x;y;z}. */
    public String encode() {
        return world + ";" + pos.x() + ";" + pos.y() + ";" + pos.z();
    }

    /** The inverse of {@link #encode()}; returns null for anything malformed. */
    public static @Nullable BlockLocation decode(@Nullable String encoded) {
        if (encoded == null) {
            return null;
        }
        String[] parts = encoded.split(";");
        if (parts.length != 4) {
            return null;
        }
        try {
            return new BlockLocation(
                    UUID.fromString(parts[0]),
                    new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The loaded world, or null if it isn't loaded. Main thread only. */
    public @Nullable World loadedWorld() {
        return Bukkit.getWorld(world);
    }

    /** The block, or null if the world isn't loaded. Main thread only. */
    public @Nullable Block block() {
        World w = loadedWorld();
        return w == null ? null : w.getBlockAt(pos.x(), pos.y(), pos.z());
    }

    /** The center of the block's top face, where a mob would stand. Null if the world isn't loaded. */
    public @Nullable Location standingLocation() {
        World w = loadedWorld();
        return w == null ? null : new Location(w, pos.x() + 0.5, pos.y(), pos.z() + 0.5);
    }
}
