package dev.angelocordon.goldengolem.chest;

import java.util.List;
import org.bukkit.Material;

/**
 * Finds a broken chest's own drop among a player break's drops. Paper puts every item spawned
 * during the break into {@code BlockDropItemEvent}: the spilled contents first, the block's loot
 * drop last, and no loot drop in creative. So only the last entry can be the chest itself.
 */
final class OwnDrop {

    private OwnDrop() {
    }

    /**
     * Index of the chest's own drop in {@code dropTypes}, or -1 if there is none (creative, or the
     * last drop isn't a chest). Never points into the contents, even if they hold chests.
     */
    static int index(List<Material> dropTypes, boolean creative) {
        if (creative || dropTypes.isEmpty()) {
            return -1;
        }
        int last = dropTypes.size() - 1;
        return dropTypes.get(last) == Material.CHEST ? last : -1;
    }
}
