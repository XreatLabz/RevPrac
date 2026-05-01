package io.github.xreatlabz.revprac.domain.matches;

import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record Match(
        MatchId id,
        MatchParticipants participants,
        ArenaId arenaId,
        KitId kitId,
        ArenaReservationId arenaReservationId,
        MatchRuleset ruleset,
        MatchState state,
        int countdownTicksRemaining,
        int activeTicksElapsed,
        Set<PlayerId> spectators,
        Optional<MatchOutcome> outcome) {

    public Match {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(participants, "participants");
        Objects.requireNonNull(arenaId, "arenaId");
        Objects.requireNonNull(kitId, "kitId");
        Objects.requireNonNull(arenaReservationId, "arenaReservationId");
        Objects.requireNonNull(ruleset, "ruleset");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(spectators, "spectators");
        Objects.requireNonNull(outcome, "outcome");

        if (countdownTicksRemaining < 0) {
            throw new IllegalArgumentException("countdownTicksRemaining must not be negative");
        }
        if (activeTicksElapsed < 0) {
            throw new IllegalArgumentException("activeTicksElapsed must not be negative");
        }

        spectators = Set.copyOf(spectators);
        for (PlayerId spectatorId : spectators) {
            Objects.requireNonNull(spectatorId, "spectatorId");
            if (participants.contains(spectatorId)) {
                throw new IllegalArgumentException("spectators must not include match participants");
            }
        }

        switch (state) {
            case COUNTDOWN -> {
                if (countdownTicksRemaining <= 0) {
                    throw new IllegalArgumentException("countdown matches must have ticks remaining");
                }
                if (activeTicksElapsed != 0) {
                    throw new IllegalArgumentException("countdown matches must not advance active ticks");
                }
                if (outcome.isPresent()) {
                    throw new IllegalArgumentException("countdown matches must not have an outcome");
                }
            }
            case ACTIVE -> {
                if (countdownTicksRemaining != 0) {
                    throw new IllegalArgumentException("active matches must have zero countdown ticks remaining");
                }
                if (activeTicksElapsed >= ruleset.maxDurationTicks()) {
                    throw new IllegalArgumentException("active matches must not exceed the max duration");
                }
                if (outcome.isPresent()) {
                    throw new IllegalArgumentException("active matches must not have an outcome");
                }
            }
            case COMPLETED -> {
                if (outcome.isEmpty()) {
                    throw new IllegalArgumentException("completed matches must have an outcome");
                }
                validateOutcomeParticipants(participants, outcome.get());
            }
        }
    }

    public static Match create(
            MatchId id,
            MatchParticipants participants,
            ArenaId arenaId,
            KitId kitId,
            ArenaReservationId arenaReservationId,
            MatchRuleset ruleset) {
        return new Match(
                id,
                participants,
                arenaId,
                kitId,
                arenaReservationId,
                ruleset,
                MatchState.COUNTDOWN,
                ruleset.countdownTicks(),
                0,
                Set.of(),
                Optional.empty());
    }

    public Match tickCountdown() {
        requireState(MatchState.COUNTDOWN, "countdown");
        if (countdownTicksRemaining == 1) {
            return new Match(
                    id,
                    participants,
                    arenaId,
                    kitId,
                    arenaReservationId,
                    ruleset,
                    MatchState.ACTIVE,
                    0,
                    0,
                    spectators,
                    Optional.empty());
        }
        return new Match(
                id,
                participants,
                arenaId,
                kitId,
                arenaReservationId,
                ruleset,
                MatchState.COUNTDOWN,
                countdownTicksRemaining - 1,
                0,
                spectators,
                Optional.empty());
    }

    public Match tickActive() {
        requireState(MatchState.ACTIVE, "active");
        int nextActiveTicksElapsed = activeTicksElapsed + 1;
        if (nextActiveTicksElapsed >= ruleset.maxDurationTicks()) {
            return new Match(
                    id,
                    participants,
                    arenaId,
                    kitId,
                    arenaReservationId,
                    ruleset,
                    MatchState.COMPLETED,
                    countdownTicksRemaining,
                    nextActiveTicksElapsed,
                    spectators,
                    Optional.of(MatchOutcome.timeout()));
        }
        return new Match(
                id,
                participants,
                arenaId,
                kitId,
                arenaReservationId,
                ruleset,
                MatchState.ACTIVE,
                countdownTicksRemaining,
                nextActiveTicksElapsed,
                spectators,
                Optional.empty());
    }

    public Match complete(MatchOutcome matchOutcome) {
        Objects.requireNonNull(matchOutcome, "matchOutcome");
        if (state == MatchState.COMPLETED) {
            throw new IllegalStateException("match is already completed");
        }
        return new Match(
                id,
                participants,
                arenaId,
                kitId,
                arenaReservationId,
                ruleset,
                MatchState.COMPLETED,
                countdownTicksRemaining,
                activeTicksElapsed,
                spectators,
                Optional.of(matchOutcome));
    }

    public Match addSpectator(PlayerId spectatorId) {
        Objects.requireNonNull(spectatorId, "spectatorId");
        if (state != MatchState.ACTIVE) {
            throw new IllegalStateException("only active matches can accept new spectators");
        }
        if (!ruleset.spectatorsEnabled()) {
            throw new IllegalStateException("spectators are disabled for this match");
        }
        if (participants.contains(spectatorId)) {
            throw new IllegalArgumentException("participants cannot become spectators in their own match");
        }

        LinkedHashSet<PlayerId> nextSpectators = new LinkedHashSet<>(spectators);
        nextSpectators.add(spectatorId);
        return new Match(
                id,
                participants,
                arenaId,
                kitId,
                arenaReservationId,
                ruleset,
                state,
                countdownTicksRemaining,
                activeTicksElapsed,
                nextSpectators,
                outcome);
    }

    public Match removeSpectator(PlayerId spectatorId) {
        Objects.requireNonNull(spectatorId, "spectatorId");
        if (!spectators.contains(spectatorId)) {
            return this;
        }

        LinkedHashSet<PlayerId> nextSpectators = new LinkedHashSet<>(spectators);
        nextSpectators.remove(spectatorId);
        return new Match(
                id,
                participants,
                arenaId,
                kitId,
                arenaReservationId,
                ruleset,
                state,
                countdownTicksRemaining,
                activeTicksElapsed,
                nextSpectators,
                outcome);
    }

    private static void validateOutcomeParticipants(MatchParticipants participants, MatchOutcome matchOutcome) {
        switch (matchOutcome.reason()) {
            case WIN, FORFEIT -> {
                PlayerId winnerId = matchOutcome.winnerId().orElseThrow(
                        () -> new IllegalArgumentException("winner is required for " + matchOutcome.reason()));
                PlayerId loserId = matchOutcome.loserId().orElseThrow(
                        () -> new IllegalArgumentException("loser is required for " + matchOutcome.reason()));
                if (!participants.contains(winnerId) || !participants.contains(loserId)) {
                    throw new IllegalArgumentException("winner and loser must be match participants");
                }
            }
            case TIMEOUT, SHUTDOWN -> {
            }
        }
    }

    private void requireState(MatchState expectedState, String action) {
        if (state != expectedState) {
            throw new IllegalStateException("cannot tick " + action + " match while in state " + state);
        }
    }
}
