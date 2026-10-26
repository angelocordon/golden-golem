package dev.angelocordon.goldengolem.chest;

import dev.angelocordon.goldengolem.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Golden chests: vanilla chests tagged in their persistent data (Paper can't add blocks).
 *
 * <p>The tag lives in the item's PDC while it is an item and in the chest's block-entity PDC once
 * placed. {@link GoldenChestListener} carries it across place and break. A double golden chest
 * has the tag on both halves, because a golden half never merges with a plain one.
 */
public final class GoldenChests {

    private static final Component NAME = Component.text("Golden Chest", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false);

    private final Plugin plugin;
    private final NamespacedKey recipeKey;

    public GoldenChests(Plugin plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "golden_chest");
    }

    /** The crafting recipe's key, in the plugin namespace. */
    public NamespacedKey recipeKey() {
        return recipeKey;
    }

    /**
     * Registers the golden chest crafting recipe (a chest ringed by 8 gold ingots) and unlocks it
     * in the recipe book of everyone already online. Called once on enable; skips the add if the
     * recipe survived a reload, since adding a duplicate key throws.
     */
    public void registerRecipe() {
        Server server = plugin.getServer();
        if (server.getRecipe(recipeKey) == null) {
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, createItem());
            recipe.shape("GGG", "GCG", "GGG");
            recipe.setIngredient('G', Material.GOLD_INGOT);
            recipe.setIngredient('C', Material.CHEST);
            server.addRecipe(recipe);
        }
        for (Player player : server.getOnlinePlayers()) {
            player.discoverRecipe(recipeKey);
        }
    }

    /** A new golden chest item. */
    public ItemStack createItem() {
        return markGolden(new ItemStack(Material.CHEST));
    }

    /**
     * A copy of {@code chestItem} tagged as a golden chest. Keeps an existing custom name (an
     * anvil rename survives place and break) and only adds the default gold name when there is
     * none.
     */
    ItemStack markGolden(ItemStack chestItem) {
        ItemStack tagged = chestItem.clone();
        tagged.editMeta(meta -> {
            meta.getPersistentDataContainer().set(Keys.GOLDEN_CHEST, PersistentDataType.BOOLEAN, true);
            if (meta.customName() == null) {
                meta.customName(NAME);
            }
        });
        return tagged;
    }

    /** True if {@code item} is a golden chest item. */
    public boolean isGoldenChestItem(@Nullable ItemStack item) {
        return item != null
                && item.getType() == Material.CHEST
                && item.getPersistentDataContainer().has(Keys.GOLDEN_CHEST);
    }

    /** True if {@code block} is a placed golden chest (either half of a double one). */
    public boolean isGoldenChest(@Nullable Block block) {
        return block != null && block.getType() == Material.CHEST && isGoldenState(block.getState(false));
    }

    /** True if {@code state} is a golden chest's state, e.g. the snapshot of one just broken. */
    boolean isGoldenState(BlockState state) {
        return state.getType() == Material.CHEST
                && state instanceof TileState tile
                && tile.getPersistentDataContainer().has(Keys.GOLDEN_CHEST);
    }

    /**
     * Tags a just-placed chest block as golden. Writes through a snapshot and {@code update}s it,
     * which is what persists block-entity PDC.
     */
    void tag(Block block) {
        if (block.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().set(Keys.GOLDEN_CHEST, PersistentDataType.BOOLEAN, true);
            tile.update(true, false);
        }
    }

    /** The chest's inventory (the whole double chest if it is one), or null if not a golden chest. */
    public @Nullable Inventory inventoryOf(Block block) {
        if (isGoldenChest(block) && block.getState(false) instanceof org.bukkit.block.Chest chest) {
            return chest.getInventory();
        }
        return null;
    }

    /** How many golems may link to this chest: 1 for a single chest, 2 for a double, 0 if not golden. */
    public int golemCapacity(Block block) {
        if (block.getBlockData() instanceof Chest data) {
            return capacityFor(isGoldenChest(block), data.getType());
        }
        return 0;
    }

    // Pure rules below: plain values only, so they are unit-tested without a server.

    /** Golem capacity of a chest: 0 if not golden, otherwise one per half. */
    static int capacityFor(boolean golden, Chest.Type type) {
        if (!golden) {
            return 0;
        }
        return type == Chest.Type.SINGLE ? 1 : 2;
    }

    /** Two chest halves may form a double chest only if both or neither are golden. */
    static boolean mayMerge(boolean golden, boolean partnerGolden) {
        return golden == partnerGolden;
    }

    /**
     * The chest type to keep for a chest vanilla just placed as {@code proposed}: unchanged, unless
     * it would merge a golden half with a plain one, in which case it stays single.
     */
    static Chest.Type allowedPlacementType(Chest.Type proposed, boolean golden, boolean partnerGolden) {
        if (proposed == Chest.Type.SINGLE || mayMerge(golden, partnerGolden)) {
            return proposed;
        }
        return Chest.Type.SINGLE;
    }

    /**
     * Direction from a chest half to its other half, or null for a single chest. Mirrors vanilla:
     * a LEFT half's partner is clockwise of its facing, a RIGHT half's is counter-clockwise.
     */
    static @Nullable BlockFace partnerFace(BlockFace facing, Chest.Type type) {
        return switch (type) {
            case SINGLE -> null;
            case LEFT -> clockwise(facing);
            case RIGHT -> clockwise(clockwise(clockwise(facing)));
        };
    }

    private static BlockFace clockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> throw new IllegalArgumentException("Chests only face horizontally: " + face);
        };
    }
}
