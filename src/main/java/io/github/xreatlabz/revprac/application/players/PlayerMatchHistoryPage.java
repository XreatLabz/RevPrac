package io.github.xreatlabz.revprac.application.players;

import java.util.List;
import java.util.Objects;

public record PlayerMatchHistoryPage(
        int page,
        int pageSize,
        List<PlayerMatchHistoryLineItem> items,
        boolean hasNextPage) {

    public PlayerMatchHistoryPage {
        if (page <= 0) {
            throw new IllegalArgumentException("page must be positive");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
        items.forEach(item -> Objects.requireNonNull(item, "item"));
    }
}
