package dev.angelocordon.goldengolem.golem;

import dev.angelocordon.goldengolem.Keys;
import dev.angelocordon.goldengolem.util.BlockLocation;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/**
 * A vanilla copper golem tagged as a Golden Golem. All golem data lives in the entity's
 * persistent data container, so it saves with the world and there is no separate store.
 *
 * <p>This is the only class that reads or writes golem keys. Wrappers are cheap and hold no
 * state of their own, so it's fine to create one per tick. Main thread only.
 */
public final class GoldenGolem {

    public static final int STORAGE_SIZE = 15;

    private final CopperGolem entity;

    private GoldenGolem(CopperGolem entity) {
        this.entity = entity;
    }

    public static boolean isGoldenGolem(@Nullable Entity entity) {
        return entity instanceof CopperGolem
                && entity.getPersistentDataContainer().has(Keys.GOLEM, PersistentDataType.BOOLEAN);
    }

    public static Optional<GoldenGolem> of(@Nullable Entity entity) {
        return isGoldenGolem(entity) ? Optional.of(new GoldenGolem((CopperGolem) entity)) : Optional.empty();
    }

    /**
     * Tags a copper golem as a Golden Golem owned by {@code owner}, in the IDLE state. Call it
     * from the {@code world.spawn(..., golem -> ...)} callback so the tag exists before the entity
     * joins the world and the registry sees it.
     */
    public static GoldenGolem create(CopperGolem entity, UUID owner) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(Keys.GOLEM, PersistentDataType.BOOLEAN, true);
        pdc.set(Keys.OWNER, PersistentDataType.STRING, owner.toString());
        GoldenGolem golem = new GoldenGolem(entity);
        golem.setState(GolemState.IDLE);
        return golem;
    }

    public CopperGolem entity() {
        return entity;
    }

    public UUID id() {
        return entity.getUniqueId();
    }

    public boolean isValid() {
        return entity.isValid();
    }

    /** The player who built it. Null only if the data was tampered with. */
    public @Nullable UUID owner() {
        String raw = pdc().get(Keys.OWNER, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isOwnedBy(UUID player) {
        return player.equals(owner());
    }

    /**
     * The current state. To change it from outside a state handler, use
     * {@link GolemController#transition} so the new state's {@code onEnter} runs.
     */
    public GolemState state() {
        String raw = pdc().get(Keys.STATE, PersistentDataType.STRING);
        if (raw == null) {
            return GolemState.IDLE;
        }
        try {
            return GolemState.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return GolemState.IDLE;
        }
    }

    void setState(GolemState state) {
        pdc().set(Keys.STATE, PersistentDataType.STRING, state.name());
    }

    public @Nullable BlockedReason blockedReason() {
        String raw = pdc().get(Keys.BLOCKED_REASON, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return BlockedReason.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setBlockedReason(@Nullable BlockedReason reason) {
        if (reason == null) {
            pdc().remove(Keys.BLOCKED_REASON);
        } else {
            pdc().set(Keys.BLOCKED_REASON, PersistentDataType.STRING, reason.name());
        }
    }

    /** The linked golden chest, or null if not linked. */
    public @Nullable BlockLocation chest() {
        return BlockLocation.decode(pdc().get(Keys.CHEST, PersistentDataType.STRING));
    }

    public void setChest(@Nullable BlockLocation chest) {
        setLocation(Keys.CHEST, chest);
    }

    /** Where the golem was told to Stay; the center of its work area. Null if never set. */
    public @Nullable BlockLocation workCenter() {
        return BlockLocation.decode(pdc().get(Keys.WORK_CENTER, PersistentDataType.STRING));
    }

    public void setWorkCenter(@Nullable BlockLocation workCenter) {
        setLocation(Keys.WORK_CENTER, workCenter);
    }

    /** A copy of the tool, or null if the slot is empty. Write changes back with {@link #setTool}. */
    public @Nullable ItemStack tool() {
        byte[] raw = pdc().get(Keys.TOOL, PersistentDataType.BYTE_ARRAY);
        return raw == null ? null : ItemStack.deserializeBytes(raw);
    }

    public void setTool(@Nullable ItemStack tool) {
        if (tool == null || tool.isEmpty()) {
            pdc().remove(Keys.TOOL);
        } else {
            pdc().set(Keys.TOOL, PersistentDataType.BYTE_ARRAY, tool.serializeAsBytes());
        }
    }

    /** The block type it gathers, or null if the target slot is empty. */
    public @Nullable Material target() {
        String raw = pdc().get(Keys.TARGET, PersistentDataType.STRING);
        return raw == null ? null : Material.getMaterial(raw);
    }

    public void setTarget(@Nullable Material target) {
        if (target == null) {
            pdc().remove(Keys.TARGET);
        } else {
            pdc().set(Keys.TARGET, PersistentDataType.STRING, target.name());
        }
    }

    /**
     * A copy of the storage, always {@link #STORAGE_SIZE} long. Empty slots are null. Write
     * changes back with {@link #setStorage}.
     */
    public @Nullable ItemStack[] storage() {
        @Nullable ItemStack[] slots = new ItemStack[STORAGE_SIZE];
        byte[] raw = pdc().get(Keys.STORAGE, PersistentDataType.BYTE_ARRAY);
        if (raw == null) {
            return slots;
        }
        ItemStack[] stored = ItemStack.deserializeItemsFromBytes(raw);
        for (int i = 0; i < Math.min(stored.length, STORAGE_SIZE); i++) {
            slots[i] = stored[i].isEmpty() ? null : stored[i];
        }
        return slots;
    }

    public void setStorage(@Nullable ItemStack[] slots) {
        if (slots.length != STORAGE_SIZE) {
            throw new IllegalArgumentException("storage must have " + STORAGE_SIZE + " slots, got " + slots.length);
        }
        if (Arrays.stream(slots).allMatch(item -> item == null || item.isEmpty())) {
            pdc().remove(Keys.STORAGE);
        } else {
            pdc().set(Keys.STORAGE, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(slots));
        }
    }

    private void setLocation(org.bukkit.NamespacedKey key, @Nullable BlockLocation location) {
        if (location == null) {
            pdc().remove(key);
        } else {
            pdc().set(key, PersistentDataType.STRING, location.encode());
        }
    }

    private PersistentDataContainer pdc() {
        return entity.getPersistentDataContainer();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GoldenGolem golem && golem.id().equals(id());
    }

    @Override
    public int hashCode() {
        return id().hashCode();
    }
}
