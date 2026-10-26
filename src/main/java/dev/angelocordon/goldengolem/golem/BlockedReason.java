package dev.angelocordon.goldengolem.golem;

/** Why a golem is {@link GolemState#BLOCKED}. Messages finish the sentence "Golden Golem ...". */
public enum BlockedReason {
    CANT_HARVEST("can't harvest its target with this tool"),
    NO_TOOL("needs a tool"),
    CHEST_FULL("can't fit anything more in its chest"),
    CHEST_MISSING("has no golden chest"),
    TOO_FAR("can't work that far from its chest"),
    UNREACHABLE("can't find a way there");

    private final String message;

    BlockedReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
