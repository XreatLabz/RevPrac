package io.github.xreatlabz.revprac.bootstrap;

import io.github.xreatlabz.revprac.application.config.RevPracConfig;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.ports.lifecycle.LifecycleReporter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BootstrapRuntime {

    private final RevPracConfig config;
    private final LifecycleReporter lifecycleReporter;
    private final PlayerSessionService playerSessionService;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public BootstrapRuntime(
            RevPracConfig config,
            LifecycleReporter lifecycleReporter,
            PlayerSessionService playerSessionService) {
        this.config = Objects.requireNonNull(config, "config");
        this.lifecycleReporter = Objects.requireNonNull(lifecycleReporter, "lifecycleReporter");
        this.playerSessionService = Objects.requireNonNull(playerSessionService, "playerSessionService");
    }

    public RevPracConfig config() {
        return config;
    }

    public void shutdown() {
        if (shutdown.get()) {
            return;
        }

        playerSessionService.shutdownAll();
        if (shutdown.compareAndSet(false, true) && config.diagnostics().verboseLifecycleLogs()) {
            lifecycleReporter.info("RevPrac runtime shut down.");
        }
    }
}
