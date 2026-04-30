package io.github.xreatlabz.revprac.application.config;

public record RevPracConfig(int configVersion, BootstrapConfig bootstrap, DiagnosticsConfig diagnostics) {
}
