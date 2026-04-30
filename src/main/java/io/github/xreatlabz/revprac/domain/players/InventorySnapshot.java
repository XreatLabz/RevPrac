package io.github.xreatlabz.revprac.domain.players;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record InventorySnapshot(
        List<String> storage,
        List<String> armor,
        List<String> extra,
        List<String> enderChest,
        String cursorItem,
        int selectedSlot) {

    public InventorySnapshot {
        storage = immutableCopyPreservingNulls(storage, "storage");
        armor = immutableCopyPreservingNulls(armor, "armor");
        extra = immutableCopyPreservingNulls(extra, "extra");
        enderChest = immutableCopyPreservingNulls(enderChest, "enderChest");
        if (selectedSlot < 0 || selectedSlot > 8) {
            throw new IllegalArgumentException("selectedSlot must be between 0 and 8");
        }
    }

    private static List<String> immutableCopyPreservingNulls(List<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName);
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
