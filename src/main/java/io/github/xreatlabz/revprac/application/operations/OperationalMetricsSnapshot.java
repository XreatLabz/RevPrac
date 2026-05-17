package io.github.xreatlabz.revprac.application.operations;

public record OperationalMetricsSnapshot(
        long publishedEvents,
        long duelRequestsCreated,
        long matchesCompleted,
        long matchesTornDown) {}
