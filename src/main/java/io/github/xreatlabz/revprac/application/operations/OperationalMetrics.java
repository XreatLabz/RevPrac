package io.github.xreatlabz.revprac.application.operations;

import io.github.xreatlabz.revprac.domain.matches.MatchEvent;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class OperationalMetrics implements Consumer<MatchEvent> {

    private final AtomicLong publishedEvents = new AtomicLong();
    private final AtomicLong duelRequestsCreated = new AtomicLong();
    private final AtomicLong matchesCompleted = new AtomicLong();
    private final AtomicLong matchesTornDown = new AtomicLong();

    @Override
    public void accept(MatchEvent matchEvent) {
        Objects.requireNonNull(matchEvent, "matchEvent");
        publishedEvents.incrementAndGet();
        if (matchEvent instanceof MatchEvent.DuelRequestCreated) {
            duelRequestsCreated.incrementAndGet();
            return;
        }
        if (matchEvent instanceof MatchEvent.MatchCompleted) {
            matchesCompleted.incrementAndGet();
            return;
        }
        if (matchEvent instanceof MatchEvent.MatchTornDown) {
            matchesTornDown.incrementAndGet();
        }
    }

    public OperationalMetricsSnapshot snapshot() {
        return new OperationalMetricsSnapshot(
                publishedEvents.get(),
                duelRequestsCreated.get(),
                matchesCompleted.get(),
                matchesTornDown.get());
    }
}
