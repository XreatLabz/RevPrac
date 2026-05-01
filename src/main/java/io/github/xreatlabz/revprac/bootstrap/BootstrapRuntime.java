package io.github.xreatlabz.revprac.bootstrap;

import io.github.xreatlabz.revprac.adapters.paper.arenas.PaperArenaRegistryFiles;
import io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitRegistryFiles;
import io.github.xreatlabz.revprac.adapters.paper.matches.PaperMatchTicker;
import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.config.RevPracConfig;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.matches.DuelRequestService;
import io.github.xreatlabz.revprac.application.matches.MatchLifecycleService;
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
    private final DuelRequestService duelRequestService;
    private final MatchLifecycleService matchLifecycleService;
    private final PaperMatchTicker paperMatchTicker;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public BootstrapRuntime(
            RevPracConfig config,
            LifecycleReporter lifecycleReporter,
            PlayerSessionService playerSessionService) {
        this(config, lifecycleReporter, playerSessionService, null, null, null, null, null, null, null);
    }

    public BootstrapRuntime(
            RevPracConfig config,
            LifecycleReporter lifecycleReporter,
            PlayerSessionService playerSessionService,
            ArenaRegistryService arenaRegistryService,
            KitRegistryService kitRegistryService,
            PaperArenaRegistryFiles arenaRegistryFiles,
            PaperKitRegistryFiles kitRegistryFiles) {
        this(
                config,
                lifecycleReporter,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                arenaRegistryFiles,
                kitRegistryFiles,
                null,
                null,
                null);
    }

    public BootstrapRuntime(
            RevPracConfig config,
            LifecycleReporter lifecycleReporter,
            PlayerSessionService playerSessionService,
            ArenaRegistryService arenaRegistryService,
            KitRegistryService kitRegistryService,
            PaperArenaRegistryFiles arenaRegistryFiles,
            PaperKitRegistryFiles kitRegistryFiles,
            DuelRequestService duelRequestService,
            MatchLifecycleService matchLifecycleService,
            PaperMatchTicker paperMatchTicker) {
        this.config = Objects.requireNonNull(config, "config");
        this.lifecycleReporter = Objects.requireNonNull(lifecycleReporter, "lifecycleReporter");
        this.playerSessionService = Objects.requireNonNull(playerSessionService, "playerSessionService");
        this.arenaRegistryService = arenaRegistryService;
        this.kitRegistryService = kitRegistryService;
        this.arenaRegistryFiles = arenaRegistryFiles;
        this.kitRegistryFiles = kitRegistryFiles;
        this.duelRequestService = duelRequestService;
        this.matchLifecycleService = matchLifecycleService;
        this.paperMatchTicker = paperMatchTicker;
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

    public DuelRequestService duelRequestService() {
        return duelRequestService;
    }

    public MatchLifecycleService matchLifecycleService() {
        return matchLifecycleService;
    }

    public PaperMatchTicker paperMatchTicker() {
        return paperMatchTicker;
    }

    public void shutdown() {
        if (shutdown.get()) {
            return;
        }

        if (duelRequestService != null) {
            duelRequestService.closeIntake();
        }
        if (paperMatchTicker != null) {
            paperMatchTicker.cancel();
        }
        RuntimeException failure = null;
        if (matchLifecycleService != null) {
            try {
                matchLifecycleService.shutdownAll();
            } catch (RuntimeException exception) {
                failure = mergeFailures(failure, exception);
            }
        }
        try {
            playerSessionService.shutdownAll();
        } catch (RuntimeException exception) {
            failure = mergeFailures(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
        if (shutdown.compareAndSet(false, true) && config.diagnostics().verboseLifecycleLogs()) {
            lifecycleReporter.info("RevPrac runtime shut down.");
        }
    }

    private RuntimeException mergeFailures(RuntimeException current, RuntimeException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }
}
