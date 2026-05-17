package io.github.xreatlabz.revprac.adapters.paper.events;

import io.github.xreatlabz.revprac.api.events.RevPracMatchEvent;
import io.github.xreatlabz.revprac.domain.matches.MatchEvent;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.plugin.PluginManager;

public final class PaperMatchEventBridge implements Consumer<MatchEvent> {

    private final PluginManager pluginManager;

    public PaperMatchEventBridge(PluginManager pluginManager) {
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager");
    }

    @Override
    public void accept(MatchEvent matchEvent) {
        pluginManager.callEvent(new RevPracMatchEvent(matchEvent));
    }
}
