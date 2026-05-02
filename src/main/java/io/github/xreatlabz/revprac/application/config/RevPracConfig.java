package io.github.xreatlabz.revprac.application.config;

import java.util.Objects;

public record RevPracConfig(
        int configVersion,
        BootstrapConfig bootstrap,
        DiagnosticsConfig diagnostics,
        MatchConfig matches,
        QueueConfig queues) {

    public RevPracConfig {
        bootstrap = Objects.requireNonNull(bootstrap, "bootstrap");
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        matches = Objects.requireNonNull(matches, "matches");
        queues = Objects.requireNonNull(queues, "queues");
    }

    public RevPracConfig(int configVersion, BootstrapConfig bootstrap, DiagnosticsConfig diagnostics) {
        this(configVersion, bootstrap, diagnostics, MatchConfig.defaults(), QueueConfig.defaults());
    }

    public RevPracConfig(int configVersion, BootstrapConfig bootstrap, DiagnosticsConfig diagnostics, MatchConfig matches) {
        this(configVersion, bootstrap, diagnostics, matches, QueueConfig.defaults());
    }
}
