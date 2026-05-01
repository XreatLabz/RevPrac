package io.github.xreatlabz.revprac.application.config;

import java.util.Objects;

public record RevPracConfig(
        int configVersion,
        BootstrapConfig bootstrap,
        DiagnosticsConfig diagnostics,
        MatchConfig matches) {

    public RevPracConfig {
        bootstrap = Objects.requireNonNull(bootstrap, "bootstrap");
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        matches = Objects.requireNonNull(matches, "matches");
    }

    public RevPracConfig(int configVersion, BootstrapConfig bootstrap, DiagnosticsConfig diagnostics) {
        this(configVersion, bootstrap, diagnostics, MatchConfig.defaults());
    }
}
