package dev.angelocordon.goldengolem.chest;

import dev.angelocordon.goldengolem.Services;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.ExplosionResult;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the golden-chest tag on place and break, and stops a golden and a plain chest merging.
 *
 * <p>Every way a chest can leave the world in survival is covered: player break (drop swapped for
 * the tagged item), explosions and withers (broken by hand here so the tagged item drops).
 * Pistons can't move chests, and chests don't burn. Golden chests also can't be used as a
 * crafting ingredient, so the tag can't be lost by crafting it into something else.
 */
public final class GoldenChestListener implements Listener {

    private static final BlockFace[] HORIZONTAL = {
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private final Services services;
    private final GoldenChests chests;

    public GoldenChestListener(Services services) {
        this.services = services;
        this.chests = services.chests();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(chests.recipeKey());
    }

    /**
     * Tags a placed golden chest and keeps it from merging with a plain neighbour. Vanilla has
     * already picked the chest type (single or a half) when this fires, but the neighbour hasn't
     * been updated yet, so forcing the placed chest to SINGLE is enough to stop the merge.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (placed.getType() != Material.CHEST) {
            return;
        }
        boolean golden = chests.isGoldenChestItem(event.getItemInHand());
        if (placed.getBlockData() instanceof Chest data && data.getType() != Chest.Type.SINGLE) {
            Block partner = placed.getRelative(partnerFace(data));
            Chest.Type allowed = GoldenChests.allowedPlacementType(
                    data.getType(), golden, chests.isGoldenChest(partner));
            if (allowed != data.getType()) {
                data.setType(allowed);
                placed.setBlockData(data, false);
            }
        }
        if (golden) {
            chests.tag(placed);
        }
        // Belt and braces: re-check next tick that no golden/plain double chest formed.
        services.plugin().getServer().getScheduler().runTask(services.plugin(), () -> splitMixedAround(placed));
    }

    /** Swaps the plain chest a player's break would drop for the tagged item. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        if (!chests.isGoldenState(event.getBlockState())) {
            return;
        }
        // The spilled contents are in this list too (and may hold plain chests), so only the
        // entry OwnDrop picks is the block's own drop.
        List<Item> items = event.getItems();
        List<Material> types = items.stream().map(item -> item.getItemStack().getType()).toList();
        int own = OwnDrop.index(types, event.getPlayer().getGameMode() == GameMode.CREATIVE);
        if (own >= 0) {
            Item item = items.get(own);
            item.setItemStack(chests.markGolden(item.getItemStack()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        breakGoldenChestsIn(event.blockList(), event.getExplosionResult());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        breakGoldenChestsIn(event.blockList(), event.getExplosionResult());
    }

    /**
     * Withers (and anything else that changes a chest into another block) would drop a plain
     * chest. A waterlogged chest turns into water rather than air, so any change counts.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getTo() != Material.CHEST && chests.isGoldenChest(event.getBlock())) {
            event.setCancelled(true);
            breakAndDrop(event.getBlock());
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (containsGoldenChest(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (event.getBlock().getState(false) instanceof Container crafter
                && containsGoldenChest(crafter.getInventory().getContents())) {
            event.setCancelled(true);
        }
    }

    /**
     * Takes golden chests out of an explosion and breaks them here instead, so they drop the
     * tagged item (always, ignoring the explosion's drop chance) plus their contents.
     */
    private void breakGoldenChestsIn(List<Block> blocks, ExplosionResult result) {
        if (result != ExplosionResult.DESTROY && result != ExplosionResult.DESTROY_WITH_DECAY) {
            return;
        }
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (chests.isGoldenChest(block)) {
                it.remove();
                breakAndDrop(block);
            }
        }
    }

    /**
     * Breaks one golden chest half and drops its contents and the tagged item. Contents are copied
     * and the half emptied before the block is removed, so nothing can drop twice.
     */
    private void breakAndDrop(Block block) {
        ItemStack chestItem = chests.createItem();
        boolean waterlogged = block.getBlockData() instanceof Chest data && data.isWaterlogged();
        List<ItemStack> contents = new ArrayList<>();
        if (block.getState(false) instanceof org.bukkit.block.Chest chest) {
            Component name = chest.customName();
            if (name != null) {
                chestItem.editMeta(meta -> meta.customName(name));
            }
            Inventory half = chest.getBlockInventory();
            for (ItemStack stack : half.getContents()) {
                if (stack != null && !stack.isEmpty()) {
                    contents.add(stack.clone());
                }
            }
            half.clear();
        }
        // Like vanilla, breaking a waterlogged chest leaves its water behind.
        block.setType(waterlogged ? Material.WATER : Material.AIR);
        World world = block.getWorld();
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack stack : contents) {
            world.dropItemNaturally(at, stack);
        }
        world.dropItemNaturally(at, chestItem);
    }

    /** Splits any golden/plain double chest at or next to {@code center} back into singles. */
    private void splitMixedAround(Block center) {
        splitIfMixed(center);
        for (BlockFace face : HORIZONTAL) {
            splitIfMixed(center.getRelative(face));
        }
    }

    private void splitIfMixed(Block block) {
        if (block.getType() != Material.CHEST
                || !(block.getBlockData() instanceof Chest data)
                || data.getType() == Chest.Type.SINGLE) {
            return;
        }
        Block partner = chests.partnerOf(block);
        if (partner != null && !GoldenChests.mayMerge(chests.isGoldenChest(block), chests.isGoldenChest(partner))) {
            data.setType(Chest.Type.SINGLE);
            block.setBlockData(data, false);
        }
    }

    private static BlockFace partnerFace(Chest data) {
        BlockFace face = GoldenChests.partnerFace(data.getFacing(), data.getType());
        return face == null ? BlockFace.SELF : face;
    }

    private boolean containsGoldenChest(@Nullable ItemStack[] items) {
        for (ItemStack item : items) {
            if (chests.isGoldenChestItem(item)) {
                return true;
            }
        }
        return false;
    }
}
