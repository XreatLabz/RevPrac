package io.github.xreatlabz.revprac.application.config;

import java.util.Objects;

public record RevPracConfig(
        int configVersion,
        BootstrapConfig bootstrap,
        DiagnosticsConfig diagnostics,
        MatchConfig matches,
        QueueConfig queues,
        StorageConfig storage) {

    public RevPracConfig {
        bootstrap = Objects.requireNonNull(bootstrap, "bootstrap");
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        matches = Objects.requireNonNull(matches, "matches");
        queues = Objects.requireNonNull(queues, "queues");
        storage = Objects.requireNonNull(storage, "storage");
    }

    public RevPracConfig(int configVersion, BootstrapConfig bootstrap, DiagnosticsConfig diagnostics) {
        this(configVersion, bootstrap, diagnostics, MatchConfig.defaults(), QueueConfig.defaults(), StorageConfig.defaults());
    }

    public RevPracConfig(int configVersion, BootstrapConfig bootstrap, DiagnosticsConfig diagnostics, MatchConfig matches) {
        this(configVersion, bootstrap, diagnostics, matches, QueueConfig.defaults(), StorageConfig.defaults());
    }

    public RevPracConfig(
            int configVersion,
            BootstrapConfig bootstrap,
            DiagnosticsConfig diagnostics,
            MatchConfig matches,
            QueueConfig queues) {
        this(configVersion, bootstrap, diagnostics, matches, queues, StorageConfig.defaults());
    }
}
