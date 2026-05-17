package io.github.xreatlabz.revprac.application.operations;

import io.github.xreatlabz.revprac.application.integrations.IntegrationStatus;
import java.util.List;
import java.util.Objects;

public record StaffDiagnostics(
        String storageBackend,
        String activeSeasonId,
        int arenaCount,
        int kitCount,
        int arenaReservationCount,
        long activeQueueTicketCount,
        long activeMatchCount,
        long pendingDuelRequestCount,
        OperationalMetricsSnapshot metrics,
        List<IntegrationStatus> integrations) {

    public StaffDiagnostics {
        storageBackend = Objects.requireNonNull(storageBackend, "storageBackend");
        activeSeasonId = Objects.requireNonNull(activeSeasonId, "activeSeasonId");
        metrics = Objects.requireNonNull(metrics, "metrics");
        integrations = List.copyOf(Objects.requireNonNull(integrations, "integrations"));
    }
}
