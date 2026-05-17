package io.github.xreatlabz.revprac.application.operations;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuditEntry(UUID id, Instant occurredAt, String actor, String action, String details) {

    public AuditEntry {
        id = Objects.requireNonNull(id, "id");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        actor = requireText(actor, "actor");
        action = requireText(action, "action");
        details = requireText(details, "details");
    }

    private static String requireText(String value, String name) {
        String trimmed = Objects.requireNonNull(value, name).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }
}
