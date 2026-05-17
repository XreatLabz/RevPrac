package io.github.xreatlabz.revprac.bootstrap;

import io.github.xreatlabz.revprac.adapters.paper.arenas.PaperArenaRegistryFiles;
import io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitRegistryFiles;
import io.github.xreatlabz.revprac.adapters.paper.matches.PaperMatchTicker;
import io.github.xreatlabz.revprac.adapters.paper.queues.PaperQueueTicker;
import io.github.xreatlabz.revprac.adapters.storage.jdbc.JdbcStorageRuntime;
import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.config.RevPracConfig;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.matches.DuelRequestService;
import io.github.xreatlabz.revprac.application.matches.MatchLifecycleService;
import io.github.xreatlabz.revprac.application.operations.StaffOperationsService;
import io.github.xreatlabz.revprac.application.parties.PartyService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.application.queues.QueueMatchmakingService;
import io.github.xreatlabz.revprac.application.queues.QueueService;
import io.github.xreatlabz.revprac.application.tournaments.TournamentService;
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
    private final QueueService queueService;
    private final QueueMatchmakingService queueMatchmakingService;
    private final PaperQueueTicker paperQueueTicker;
    private final AutoCloseable storageRuntime;
    private final StaffOperationsService staffOperationsService;
    private final PartyService partyService;
    private final TournamentService tournamentService;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public BootstrapRuntime(
            RevPracConfig config,
            LifecycleReporter lifecycleReporter,
            PlayerSessionService playerSessionService) {
        this(config, lifecycleReporter, playerSessionService, null, null, null, null, null, null, null, null, null, null);
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
                null,
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
        this(
                config,
                lifecycleReporter,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                arenaRegistryFiles,
                kitRegistryFiles,
                duelRequestService,
                matchLifecycleService,
                paperMatchTicker,
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
            PaperMatchTicker paperMatchTicker,
            QueueService queueService,
            QueueMatchmakingService queueMatchmakingService,
            PaperQueueTicker paperQueueTicker) {
        this(
                config,
                lifecycleReporter,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                arenaRegistryFiles,
                kitRegistryFiles,
                duelRequestService,
                matchLifecycleService,
                paperMatchTicker,
                queueService,
                queueMatchmakingService,
                paperQueueTicker,
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
            PaperMatchTicker paperMatchTicker,
            QueueService queueService,
            QueueMatchmakingService queueMatchmakingService,
            PaperQueueTicker paperQueueTicker,
            AutoCloseable storageRuntime) {
        this(
                config,
                lifecycleReporter,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                arenaRegistryFiles,
                kitRegistryFiles,
                duelRequestService,
                matchLifecycleService,
                paperMatchTicker,
                queueService,
                queueMatchmakingService,
                paperQueueTicker,
                storageRuntime,
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
            PaperMatchTicker paperMatchTicker,
            QueueService queueService,
            QueueMatchmakingService queueMatchmakingService,
            PaperQueueTicker paperQueueTicker,
            AutoCloseable storageRuntime,
            StaffOperationsService staffOperationsService) {
        this(
                config,
                lifecycleReporter,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                arenaRegistryFiles,
                kitRegistryFiles,
                duelRequestService,
                matchLifecycleService,
                paperMatchTicker,
                queueService,
                queueMatchmakingService,
                paperQueueTicker,
                storageRuntime,
                staffOperationsService,
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
            PaperMatchTicker paperMatchTicker,
            QueueService queueService,
            QueueMatchmakingService queueMatchmakingService,
            PaperQueueTicker paperQueueTicker,
            AutoCloseable storageRuntime,
            StaffOperationsService staffOperationsService,
            PartyService partyService,
            TournamentService tournamentService) {
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
        this.queueService = queueService;
        this.queueMatchmakingService = queueMatchmakingService;
        this.paperQueueTicker = paperQueueTicker;
        this.storageRuntime = storageRuntime;
        this.staffOperationsService = staffOperationsService;
        this.partyService = partyService;
        this.tournamentService = tournamentService;
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

    public QueueService queueService() {
        return queueService;
    }

    public QueueMatchmakingService queueMatchmakingService() {
        return queueMatchmakingService;
    }

    public PaperQueueTicker paperQueueTicker() {
        return paperQueueTicker;
    }

    public JdbcStorageRuntime storageRuntime() {
        return storageRuntime instanceof JdbcStorageRuntime jdbcStorageRuntime ? jdbcStorageRuntime : null;
    }

    public StaffOperationsService staffOperationsService() {
        return staffOperationsService;
    }

    public PartyService partyService() {
        return partyService;
    }

    public TournamentService tournamentService() {
        return tournamentService;
    }

    public void shutdown() {
        if (shutdown.get()) {
            return;
        }

        RuntimeException failure = null;
        failure = attemptShutdownStep(failure, queueService, QueueService::closeIntake);
        failure = attemptShutdownStep(failure, queueMatchmakingService, QueueMatchmakingService::closeIntake);
        failure = attemptShutdownStep(failure, paperQueueTicker, PaperQueueTicker::cancel);
        failure = attemptShutdownStep(failure, queueService, QueueService::shutdownAll);
        failure = attemptShutdownStep(failure, duelRequestService, DuelRequestService::closeIntake);
        failure = attemptShutdownStep(failure, paperMatchTicker, PaperMatchTicker::cancel);
        failure = attemptShutdownStep(failure, matchLifecycleService, MatchLifecycleService::shutdownAll);
        failure = attemptShutdownStep(failure, playerSessionService, PlayerSessionService::shutdownAll);
        failure = attemptShutdownStep(failure, storageRuntime, runtime -> {
            try {
                runtime.close();
            } catch (Exception exception) {
                throw new IllegalStateException("storage runtime shutdown failed", exception);
            }
        });
        if (failure != null) {
            throw failure;
        }
        if (shutdown.compareAndSet(false, true) && config.diagnostics().verboseLifecycleLogs()) {
            lifecycleReporter.info("RevPrac runtime shut down.");
        }
    }

    private <T> RuntimeException attemptShutdownStep(
            RuntimeException currentFailure,
            T stepOwner,
            ShutdownStep<T> shutdownStep) {
        if (stepOwner == null) {
            return currentFailure;
        }
        try {
            shutdownStep.run(stepOwner);
            return currentFailure;
        } catch (RuntimeException exception) {
            return mergeFailures(currentFailure, exception);
        }
    }

    private RuntimeException mergeFailures(RuntimeException current, RuntimeException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    @FunctionalInterface
    private interface ShutdownStep<T> {
        void run(T stepOwner);
    }
}
