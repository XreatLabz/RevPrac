package io.github.xreatlabz.revprac.application.matches;

import io.github.xreatlabz.revprac.domain.matches.MatchEvent;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;
import java.util.function.Consumer;

final class MatchEventPublisher {

    private static final AtomicLong GLOBAL_SEQUENCE = new AtomicLong(0L);

    private final Consumer<MatchEvent> sink;

    MatchEventPublisher(Consumer<MatchEvent> sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    void emit(LongFunction<MatchEvent> eventFactory) {
        MatchEvent event = Objects.requireNonNull(eventFactory, "eventFactory")
                .apply(GLOBAL_SEQUENCE.incrementAndGet());
        try {
            sink.accept(event);
        } catch (RuntimeException ignored) {
            // Event listeners are observational only and must not break service mutations.
        }
    }
}
