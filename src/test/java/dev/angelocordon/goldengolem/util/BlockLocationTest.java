package dev.angelocordon.goldengolem.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlockLocationTest {

    private static final UUID WORLD = UUID.fromString("2f1c6a1e-5b7d-4e8a-9c21-7d4e5f6a8b90");

    @Test
    void encodeDecodeRoundTrips() {
        BlockLocation location = new BlockLocation(WORLD, new BlockPos(12, -59, -3040));

        assertEquals(location, BlockLocation.decode(location.encode()));
    }

    @Test
    void decodeRejectsMalformedInput() {
        assertNull(BlockLocation.decode(null));
        assertNull(BlockLocation.decode(""));
        assertNull(BlockLocation.decode("not-a-uuid;1;2;3"));
        assertNull(BlockLocation.decode(WORLD + ";1;2"));
        assertNull(BlockLocation.decode(WORLD + ";1;two;3"));
    }
}
