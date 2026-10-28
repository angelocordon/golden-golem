package dev.angelocordon.goldengolem.testsupport;

import dev.angelocordon.goldengolem.GoldenGolemPlugin;
import dev.angelocordon.goldengolem.Keys;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.util.BlockLocation;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Drives a Golden Golem through a real scenario on a server nobody can join. The smoke test
 * sends {@code ggtest mining} on the console and reads the {@code GGTEST} lines from the log.
 *
 * <p>There is no player, so a stand-in {@link Player} owns the golem. It only has to answer
 * {@code getUniqueId} and {@code isOnline}, which is all the plugin asks of it.
 */
public final class TestSupportPlugin extends JavaPlugin {

    private static final int TIMEOUT_TICKS = 6000;
    private static final int STONE_BLOCKS = 9;

    @Override
    public void onEnable() {
        Objects.requireNonNull(getCommand("ggtest")).setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equals("mining")) {
            mining();
            return true;
        }
        return false;
    }

    private void result(boolean pass, String detail) {
        getLogger().info("GGTEST " + (pass ? "PASS" : "FAIL") + " mining: " + detail);
    }

    /** Builds a golem next to a golden chest and a patch of stone, and waits for the drops to arrive. */
    private void mining() {
        GoldenGolemPlugin golden = (GoldenGolemPlugin) getServer().getPluginManager().getPlugin("GoldenGolem");
        if (golden == null) {
            result(false, "GoldenGolem plugin not found");
            return;
        }
        UUID ownerId = UUID.randomUUID();
        Player owner = standIn(ownerId);
        golden.overridePlayerLookup(id -> id.equals(ownerId) ? owner : getServer().getPlayer(id));

        World world = getServer().getWorlds().getFirst();
        // Forceloaded chunks keep ticking with nobody online.
        for (int cx = 5; cx <= 7; cx++) {
            for (int cz = 5; cz <= 7; cz++) {
                world.setChunkForceLoaded(cx, cz, true);
            }
        }
        int y = world.getHighestBlockYAt(100, 100) + 1;

        Block chest = world.getBlockAt(100, y, 100);
        chest.setType(Material.CHEST);
        if (chest.getState() instanceof Chest tile) {
            tile.getPersistentDataContainer().set(Keys.GOLDEN_CHEST, PersistentDataType.BOOLEAN, true);
            tile.update();
        }
        for (int x = 104; x <= 106; x++) {
            for (int z = 100; z <= 102; z++) {
                world.getBlockAt(x, y, z).setType(Material.STONE);
            }
        }

        Block body = world.getBlockAt(101, y, 97);
        Block head = body.getRelative(0, 1, 0);
        body.setType(Material.GOLD_BLOCK);
        BlockState replaced = head.getState();
        head.setType(Material.CARVED_PUMPKIN);
        getServer().getPluginManager().callEvent(new BlockPlaceEvent(head, replaced, body,
                new ItemStack(Material.CARVED_PUMPKIN), owner, true, EquipmentSlot.HAND));

        Block center = world.getBlockAt(105, y, 98);
        new BukkitRunnable() {
            private int tick;
            private boolean equipped;

            @Override
            public void run() {
                tick++;
                if (!equipped) {
                    if (tick > 40) {
                        cancel();
                        result(false, "no golem appeared after the pumpkin was placed");
                        return;
                    }
                    Collection<CopperGolem> nearby = world.getNearbyEntitiesByType(CopperGolem.class,
                            body.getLocation(), 6);
                    var found = nearby.stream().map(GoldenGolem::of).flatMap(java.util.Optional::stream).findFirst();
                    if (found.isEmpty()) {
                        return;
                    }
                    GoldenGolem golem = found.get();
                    if (golem.chest() == null) {
                        cancel();
                        result(false, "golem built but did not link to the golden chest");
                        return;
                    }
                    golem.setTool(new ItemStack(Material.IRON_PICKAXE));
                    golem.setTarget(Material.STONE);
                    golem.setWorkCenter(BlockLocation.of(center));
                    // Same as a golem loaded from disk already WORKING: the handler copes without onEnter.
                    golem.entity().getPersistentDataContainer().set(Keys.STATE, PersistentDataType.STRING, "WORKING");
                    getLogger().info("GGTEST golem equipped, working");
                    equipped = true;
                    tick = 0;
                    return;
                }
                if (tick % 20 != 0) {
                    return;
                }
                int cobble = countIn(chest, Material.COBBLESTONE);
                int stone = countStone(world, y);
                if (cobble == STONE_BLOCKS && stone == 0) {
                    cancel();
                    result(true, cobble + " cobblestone in the golden chest after " + tick + " ticks");
                } else if (tick >= TIMEOUT_TICKS) {
                    cancel();
                    result(false, "after " + tick + " ticks: " + cobble + "/" + STONE_BLOCKS
                            + " cobblestone in chest, " + stone + " stone left");
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    private static int countIn(Block chest, Material material) {
        if (!(chest.getState() instanceof Chest tile)) {
            return 0;
        }
        int total = 0;
        for (ItemStack item : tile.getBlockInventory().getContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private static int countStone(World world, int y) {
        int left = 0;
        for (int x = 104; x <= 106; x++) {
            for (int z = 100; z <= 102; z++) {
                if (world.getBlockAt(x, y, z).getType() == Material.STONE) {
                    left++;
                }
            }
        }
        return left;
    }

    private static Player standIn(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUniqueId":
                            return id;
                        case "isOnline":
                            return true;
                        case "getName":
                        case "toString":
                            return "GGTestOwner";
                        case "hashCode":
                            return id.hashCode();
                        case "equals":
                            return proxy == args[0];
                        default:
                            break;
                    }
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) {
                        return false;
                    }
                    if (type == int.class || type == short.class || type == byte.class) {
                        return 0;
                    }
                    if (type == long.class) {
                        return 0L;
                    }
                    if (type == float.class) {
                        return 0f;
                    }
                    if (type == double.class) {
                        return 0d;
                    }
                    return null;
                });
    }
}
