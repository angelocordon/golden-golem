package dev.angelocordon.goldengolem.chest;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Golden chests: vanilla chests tagged in their persistent data (Paper can't add blocks).
 *
 * <p>TODO(LAB-13): implement. Stubs return "not a golden chest" so callers fail safe.
 */
public final class GoldenChests {

    private final Plugin plugin;

    public GoldenChests(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Registers the golden chest crafting recipe. Called once on enable. */
    public void registerRecipe() {
    }

    /** A new golden chest item. */
    public ItemStack createItem() {
        return new ItemStack(Material.CHEST);
    }

    /** True if {@code block} is a placed golden chest (either half of a double one). */
    public boolean isGoldenChest(@Nullable Block block) {
        return false;
    }

    /** The chest's inventory (the whole double chest if it is one), or null if not a golden chest. */
    public @Nullable Inventory inventoryOf(Block block) {
        return null;
    }

    /** How many golems may link to this chest: 1 for a single chest, 2 for a double, 0 if not golden. */
    public int golemCapacity(Block block) {
        return 0;
    }
}
