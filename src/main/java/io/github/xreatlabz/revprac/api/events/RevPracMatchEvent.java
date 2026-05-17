package io.github.xreatlabz.revprac.api.events;

import io.github.xreatlabz.revprac.domain.matches.MatchEvent;
import java.util.Objects;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class RevPracMatchEvent extends Event {

    public static final int CONTRACT_VERSION = 1;
    private static final HandlerList HANDLERS = new HandlerList();

    private final MatchEvent domainEvent;

    public RevPracMatchEvent(MatchEvent domainEvent) {
        this.domainEvent = Objects.requireNonNull(domainEvent, "domainEvent");
    }

    public int contractVersion() {
        return CONTRACT_VERSION;
    }

    public MatchEvent domainEvent() {
        return domainEvent;
    }

    public String eventType() {
        return domainEvent.getClass().getSimpleName();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
