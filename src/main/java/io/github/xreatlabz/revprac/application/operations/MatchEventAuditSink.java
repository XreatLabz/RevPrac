package io.github.xreatlabz.revprac.application.operations;

import io.github.xreatlabz.revprac.domain.matches.MatchEvent;
import io.github.xreatlabz.revprac.ports.operations.AuditRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class MatchEventAuditSink implements Consumer<MatchEvent> {

    private final AuditRepository auditRepository;
    private final Clock clock;

    public MatchEventAuditSink(AuditRepository auditRepository, Clock clock) {
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void accept(MatchEvent matchEvent) {
        Objects.requireNonNull(matchEvent, "matchEvent");
        auditRepository.append(new AuditEntry(
                UUID.randomUUID(),
                clock.instant(),
                "system",
                action(matchEvent),
                details(matchEvent)));
    }

    private static String action(MatchEvent matchEvent) {
        return "match.event." + matchEvent.getClass().getSimpleName();
    }

    private static String details(MatchEvent matchEvent) {
        if (matchEvent instanceof MatchEvent.DuelRequestCreated event) {
            return "request=" + event.requestId().value()
                    + ", requester=" + event.requesterId().value()
                    + ", target=" + event.targetId().value();
        }
        if (matchEvent instanceof MatchEvent.DuelRequestAccepted event) {
            return "request=" + event.requestId().value() + ", match=" + event.matchId().value();
        }
        if (matchEvent instanceof MatchEvent.MatchCountdownStarted event) {
            return "match=" + event.matchId().value() + ", countdownTicks=" + event.countdownTicks();
        }
        if (matchEvent instanceof MatchEvent.MatchStarted event) {
            return "match=" + event.matchId().value();
        }
        if (matchEvent instanceof MatchEvent.MatchCompleted event) {
            return "match=" + event.matchId().value() + ", reason=" + event.outcome().reason();
        }
        if (matchEvent instanceof MatchEvent.MatchSpectatorJoined event) {
            return "match=" + event.matchId().value() + ", spectator=" + event.spectatorId().value();
        }
        if (matchEvent instanceof MatchEvent.MatchSpectatorLeft event) {
            return "match=" + event.matchId().value() + ", spectator=" + event.spectatorId().value();
        }
        if (matchEvent instanceof MatchEvent.MatchTornDown event) {
            return "match=" + event.matchId().value() + ", reason=" + event.reason();
        }
        return matchEvent.toString();
    }
}
