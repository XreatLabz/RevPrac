package io.github.xreatlabz.revprac.domain.parties;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record PartyQueueEligibilitySnapshot(
        PartyId partyId,
        PlayerId leaderId,
        List<PlayerId> members,
        int requiredSize,
        boolean eligible,
        Optional<String> reason) {

    public PartyQueueEligibilitySnapshot {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(leaderId, "leaderId");
        Objects.requireNonNull(members, "members");
        Objects.requireNonNull(reason, "reason");
        members = List.copyOf(members);
        reason = reason.map(PartyQueueEligibilitySnapshot::normalizeReason);
        if (requiredSize <= 0) {
            throw new IllegalArgumentException("requiredSize must be positive");
        }
        if (!members.contains(leaderId)) {
            throw new IllegalArgumentException("party leader must be a party member");
        }
        if (eligible && reason.isPresent()) {
            throw new IllegalArgumentException("eligible queue snapshots must not include a rejection reason");
        }
        if (!eligible && reason.isEmpty()) {
            throw new IllegalArgumentException("ineligible queue snapshots must include a rejection reason");
        }
    }

    private static String normalizeReason(String value) {
        Objects.requireNonNull(value, "reason value");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return normalized;
    }
}
