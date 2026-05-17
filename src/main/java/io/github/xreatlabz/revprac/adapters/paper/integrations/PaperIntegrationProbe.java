package io.github.xreatlabz.revprac.adapters.paper.integrations;

import io.github.xreatlabz.revprac.application.integrations.IntegrationStatus;
import io.github.xreatlabz.revprac.application.integrations.IntegrationType;
import io.github.xreatlabz.revprac.ports.integrations.IntegrationProbe;
import java.util.List;
import java.util.Objects;
import org.bukkit.plugin.PluginManager;

public final class PaperIntegrationProbe implements IntegrationProbe {

    private final PluginManager pluginManager;

    public PaperIntegrationProbe(PluginManager pluginManager) {
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager");
    }

    @Override
    public List<IntegrationStatus> statuses() {
        return List.of(
                status(IntegrationType.SCOREBOARD, "FastBoard"),
                status(IntegrationType.PLACEHOLDER, "PlaceholderAPI"),
                status(IntegrationType.TAB, "TAB"),
                status(IntegrationType.COMBAT_LOG, "CombatLogX"),
                status(IntegrationType.PARTY, "RevPrac"));
    }

    private IntegrationStatus status(IntegrationType type, String pluginName) {
        boolean present = type == IntegrationType.PARTY || pluginManager.getPlugin(pluginName) != null;
        return new IntegrationStatus(type, pluginName, present);
    }
}
