package io.github.xreatlabz.revprac.domain.matches;

import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.Objects;

public sealed interface MatchEvent permits MatchEvent.DuelRequestCreated,
        MatchEvent.DuelRequestAccepted,
        MatchEvent.MatchCountdownStarted,
        MatchEvent.MatchStarted,
        MatchEvent.MatchCompleted,
        MatchEvent.MatchSpectatorJoined,
        MatchEvent.MatchSpectatorLeft,
        MatchEvent.MatchTornDown {

    long sequence();

    record DuelRequestCreated(
            long sequence,
            DuelRequestId requestId,
            PlayerId requesterId,
            PlayerId targetId,
            ArenaId arenaId,
            KitId kitId) implements MatchEvent {

        public DuelRequestCreated {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(requesterId, "requesterId");
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(arenaId, "arenaId");
            Objects.requireNonNull(kitId, "kitId");
        }
    }

    record DuelRequestAccepted(long sequence, DuelRequestId requestId, MatchId matchId) implements MatchEvent {

        public DuelRequestAccepted {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(matchId, "matchId");
        }
    }

    record MatchCountdownStarted(long sequence, MatchId matchId, int countdownTicks) implements MatchEvent {

        public MatchCountdownStarted {
            Objects.requireNonNull(matchId, "matchId");
            if (countdownTicks <= 0) {
                throw new IllegalArgumentException("countdownTicks must be positive");
            }
        }
    }

    record MatchStarted(long sequence, MatchId matchId) implements MatchEvent {

        public MatchStarted {
            Objects.requireNonNull(matchId, "matchId");
        }
    }

    record MatchCompleted(long sequence, MatchId matchId, MatchOutcome outcome) implements MatchEvent {

        public MatchCompleted {
            Objects.requireNonNull(matchId, "matchId");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    record MatchSpectatorJoined(long sequence, MatchId matchId, PlayerId spectatorId) implements MatchEvent {

        public MatchSpectatorJoined {
            Objects.requireNonNull(matchId, "matchId");
            Objects.requireNonNull(spectatorId, "spectatorId");
        }
    }

    record MatchSpectatorLeft(long sequence, MatchId matchId, PlayerId spectatorId) implements MatchEvent {

        public MatchSpectatorLeft {
            Objects.requireNonNull(matchId, "matchId");
            Objects.requireNonNull(spectatorId, "spectatorId");
        }
    }

    record MatchTornDown(long sequence, MatchId matchId, MatchEndReason reason) implements MatchEvent {

        public MatchTornDown {
            Objects.requireNonNull(matchId, "matchId");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
