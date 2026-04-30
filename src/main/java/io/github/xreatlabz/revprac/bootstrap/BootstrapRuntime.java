package io.github.xreatlabz.revprac.bootstrap;

import io.github.xreatlabz.revprac.adapters.paper.arenas.PaperArenaRegistryFiles;
import io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitRegistryFiles;
import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.config.RevPracConfig;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.ports.lifecycle.LifecycleReporter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BootstrapRuntime {

    private final RevPracConfig config;
    private final LifecycleReporter lifecycleReporter;
    private final PlayerSessionService playerSessionService;
    private final ArenaRegistryService arenaRegistryService;
    private final KitRegistryService kitRegistryService;
    private final PaperArenaRegistryFiles arenaRegistryFiles;
    private final PaperKitRegistryFiles kitRegistryFiles;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public BootstrapRuntime(
            RevPracConfig config,
            LifecycleReporter lifecycleReporter,
            PlayerSessionService playerSessionService) {
        this(config, lifecycleReporter, playerSessionService, null, null, null, null);
    }

    public BootstrapRuntime(
            RevPracConfig config,
            LifecycleReporter lifecycleReporter,
            PlayerSessionService playerSessionService,
            ArenaRegistryService arenaRegistryService,
            KitRegistryService kitRegistryService,
            PaperArenaRegistryFiles arenaRegistryFiles,
            PaperKitRegistryFiles kitRegistryFiles) {
        this.config = Objects.requireNonNull(config, "config");
        this.lifecycleReporter = Objects.requireNonNull(lifecycleReporter, "lifecycleReporter");
        this.playerSessionService = Objects.requireNonNull(playerSessionService, "playerSessionService");
        this.arenaRegistryService = arenaRegistryService;
        this.kitRegistryService = kitRegistryService;
        this.arenaRegistryFiles = arenaRegistryFiles;
        this.kitRegistryFiles = kitRegistryFiles;
    }

    public RevPracConfig config() {
        return config;
    }

    public ArenaRegistryService arenaRegistryService() {
        return arenaRegistryService;
    }

    public KitRegistryService kitRegistryService() {
        return kitRegistryService;
    }

    public PaperArenaRegistryFiles arenaRegistryFiles() {
        return arenaRegistryFiles;
    }

    public PaperKitRegistryFiles kitRegistryFiles() {
        return kitRegistryFiles;
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
