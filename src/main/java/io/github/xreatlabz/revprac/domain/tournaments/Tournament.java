package io.github.xreatlabz.revprac.domain.tournaments;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record Tournament(
        TournamentId id,
        String name,
        int maxEntrants,
        TournamentState state,
        List<PlayerId> entrants,
        Optional<Instant> openedAt,
        Optional<Instant> startedAt,
        Optional<Instant> completedAt,
        Optional<PlayerId> winnerId) {

    public Tournament {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(entrants, "entrants");
        Objects.requireNonNull(openedAt, "openedAt");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        Objects.requireNonNull(winnerId, "winnerId");
        name = normalizeName(name);
        entrants = List.copyOf(entrants);
        if (maxEntrants < 2) {
            throw new IllegalArgumentException("maxEntrants must be at least 2");
        }
        if (entrants.size() > maxEntrants) {
            throw new IllegalArgumentException("entrant count must not exceed maxEntrants");
        }
        Set<PlayerId> distinctEntrants = new HashSet<>();
        for (PlayerId entrantId : entrants) {
            Objects.requireNonNull(entrantId, "entrantId");
            if (!distinctEntrants.add(entrantId)) {
                throw new IllegalArgumentException("tournament entrants must be distinct");
            }
        }
        if (winnerId.isPresent() && !entrants.contains(winnerId.orElseThrow())) {
            throw new IllegalArgumentException("winner must be one of the registered entrants");
        }
        if (openedAt.isPresent() && startedAt.isPresent() && startedAt.orElseThrow().isBefore(openedAt.orElseThrow())) {
            throw new IllegalArgumentException("startedAt must not be before openedAt");
        }
        if (startedAt.isPresent()
                && completedAt.isPresent()
                && completedAt.orElseThrow().isBefore(startedAt.orElseThrow())) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }

        switch (state) {
            case DRAFT -> {
                if (!entrants.isEmpty()) {
                    throw new IllegalArgumentException("draft tournaments must not have entrants");
                }
                if (openedAt.isPresent() || startedAt.isPresent() || completedAt.isPresent() || winnerId.isPresent()) {
                    throw new IllegalArgumentException("draft tournaments must not have lifecycle timestamps or a winner");
                }
            }
            case OPEN -> {
                if (openedAt.isEmpty()) {
                    throw new IllegalArgumentException("open tournaments must record when registration opened");
                }
                if (startedAt.isPresent() || completedAt.isPresent() || winnerId.isPresent()) {
                    throw new IllegalArgumentException("open tournaments must not have started or completed state");
                }
            }
            case STARTED -> {
                if (openedAt.isEmpty() || startedAt.isEmpty()) {
                    throw new IllegalArgumentException("started tournaments must record open and start times");
                }
                if (entrants.size() < 2) {
                    throw new IllegalArgumentException("started tournaments require at least two entrants");
                }
                if (completedAt.isPresent() || winnerId.isPresent()) {
                    throw new IllegalArgumentException("started tournaments must not be completed");
                }
            }
            case COMPLETED -> {
                if (openedAt.isEmpty() || startedAt.isEmpty() || completedAt.isEmpty() || winnerId.isEmpty()) {
                    throw new IllegalArgumentException("completed tournaments must record all lifecycle timestamps and a winner");
                }
                if (entrants.size() < 2) {
                    throw new IllegalArgumentException("completed tournaments require at least two entrants");
                }
            }
        }
    }

    public static Tournament create(TournamentId id, String name, int maxEntrants) {
        return new Tournament(
                id,
                name,
                maxEntrants,
                TournamentState.DRAFT,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public Tournament open(Instant openedAt) {
        Objects.requireNonNull(openedAt, "openedAt");
        requireState(TournamentState.DRAFT, "draft");
        return new Tournament(
                id,
                name,
                maxEntrants,
                TournamentState.OPEN,
                entrants,
                Optional.of(openedAt),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public Tournament register(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        requireState(TournamentState.OPEN, "open");
        if (entrants.contains(playerId)) {
            throw new IllegalStateException("player is already registered");
        }
        if (entrants.size() >= maxEntrants) {
            throw new IllegalStateException("tournament is full");
        }
        List<PlayerId> updatedEntrants = new ArrayList<>(entrants);
        updatedEntrants.add(playerId);
        return new Tournament(
                id,
                name,
                maxEntrants,
                TournamentState.OPEN,
                updatedEntrants,
                openedAt,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public Tournament start(Instant startedAt) {
        Objects.requireNonNull(startedAt, "startedAt");
        requireState(TournamentState.OPEN, "open");
        if (entrants.size() < 2) {
            throw new IllegalStateException("tournament requires at least two entrants to start");
        }
        return new Tournament(
                id,
                name,
                maxEntrants,
                TournamentState.STARTED,
                entrants,
                openedAt,
                Optional.of(startedAt),
                Optional.empty(),
                Optional.empty());
    }

    public Tournament complete(PlayerId winnerId, Instant completedAt) {
        Objects.requireNonNull(winnerId, "winnerId");
        Objects.requireNonNull(completedAt, "completedAt");
        requireState(TournamentState.STARTED, "started");
        if (!entrants.contains(winnerId)) {
            throw new IllegalStateException("winner must be a registered entrant");
        }
        return new Tournament(
                id,
                name,
                maxEntrants,
                TournamentState.COMPLETED,
                entrants,
                openedAt,
                startedAt,
                Optional.of(completedAt),
                Optional.of(winnerId));
    }

    public int entrantCount() {
        return entrants.size();
    }

    private void requireState(TournamentState expectedState, String expectedDescription) {
        if (state != expectedState) {
            throw new IllegalStateException("tournament must be " + expectedDescription + " before this operation");
        }
    }

    private static String normalizeName(String value) {
        String normalized = Objects.requireNonNull(value, "name").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return normalized;
    }
}
