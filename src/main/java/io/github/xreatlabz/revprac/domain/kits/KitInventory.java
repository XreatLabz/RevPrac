package io.github.xreatlabz.revprac.domain.kits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record KitInventory(List<String> storage, List<String> armor, List<String> extra, int selectedSlot) {

    public KitInventory {
        storage = immutableCopyPreservingNulls(storage, "storage");
        armor = immutableCopyPreservingNulls(armor, "armor");
        extra = immutableCopyPreservingNulls(extra, "extra");
        if (selectedSlot < 0 || selectedSlot > 8) {
            throw new IllegalArgumentException("selectedSlot must be between 0 and 8");
        }
    }

    private static List<String> immutableCopyPreservingNulls(List<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName);
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
