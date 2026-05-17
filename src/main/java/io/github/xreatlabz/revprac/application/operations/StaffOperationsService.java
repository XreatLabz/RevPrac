package io.github.xreatlabz.revprac.application.operations;

import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.config.RevPracConfig;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.matches.DuelRequestService;
import io.github.xreatlabz.revprac.application.matches.MatchLifecycleService;
import io.github.xreatlabz.revprac.application.queues.QueueService;
import io.github.xreatlabz.revprac.application.seasons.SeasonService;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.seasons.Season;
import io.github.xreatlabz.revprac.ports.integrations.IntegrationProbe;
import io.github.xreatlabz.revprac.ports.operations.AuditRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class StaffOperationsService {

    private final RevPracConfig config;
    private final ArenaRegistryService arenaRegistryService;
    private final KitRegistryService kitRegistryService;
    private final DuelRequestService duelRequestService;
    private final MatchLifecycleService matchLifecycleService;
    private final QueueService queueService;
    private final SeasonService seasonService;
    private final AuditRepository auditRepository;
    private final IntegrationProbe integrationProbe;
    private final OperationalMetrics metrics;
    private final RegistryLoader<List<ArenaDefinition>> arenaLoader;
    private final RegistryLoader<List<KitDefinition>> kitLoader;
    private final Clock clock;

    public StaffOperationsService(
            RevPracConfig config,
            ArenaRegistryService arenaRegistryService,
            KitRegistryService kitRegistryService,
            DuelRequestService duelRequestService,
            MatchLifecycleService matchLifecycleService,
            QueueService queueService,
            SeasonService seasonService,
            AuditRepository auditRepository,
            IntegrationProbe integrationProbe,
            OperationalMetrics metrics,
            RegistryLoader<List<ArenaDefinition>> arenaLoader,
            RegistryLoader<List<KitDefinition>> kitLoader,
            Clock clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.arenaRegistryService = Objects.requireNonNull(arenaRegistryService, "arenaRegistryService");
        this.kitRegistryService = Objects.requireNonNull(kitRegistryService, "kitRegistryService");
        this.duelRequestService = Objects.requireNonNull(duelRequestService, "duelRequestService");
        this.matchLifecycleService = Objects.requireNonNull(matchLifecycleService, "matchLifecycleService");
        this.queueService = Objects.requireNonNull(queueService, "queueService");
        this.seasonService = Objects.requireNonNull(seasonService, "seasonService");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository");
        this.integrationProbe = Objects.requireNonNull(integrationProbe, "integrationProbe");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.arenaLoader = Objects.requireNonNull(arenaLoader, "arenaLoader");
        this.kitLoader = Objects.requireNonNull(kitLoader, "kitLoader");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public StaffDiagnostics diagnostics() {
        return new StaffDiagnostics(
                config.storage().backend(),
                seasonService.activeSeasonId(),
                arenaRegistryService.arenas().size(),
                kitRegistryService.kits().size(),
                arenaRegistryService.activeReservationCount(),
                queueService.activeTicketCount(),
                matchLifecycleService.activeMatchCount(),
                duelRequestService.pendingRequestCount(),
                metrics.snapshot(),
                integrationProbe.statuses());
    }

    public RegistryReloadResult reloadRegistries(String actor) {
        ensureNoActiveRuntimeStateForRegistryReload();
        List<ArenaDefinition> arenas = load(arenaLoader, "arenas.yml");
        List<KitDefinition> kits = load(kitLoader, "kits.yml");
        arenaRegistryService.replaceAll(arenas);
        kitRegistryService.replaceAll(kits);
        audit(actor, "registry.reload", "arenas=" + arenas.size() + ", kits=" + kits.size());
        return new RegistryReloadResult(arenas.size(), kits.size());
    }

    public List<Season> seasons() {
        return seasonService.seasons();
    }

    public Season createSeason(String actor, String seasonId) {
        Season created = seasonService.create(seasonId);
        audit(actor, "season.create", "season=" + created.id().value());
        return created;
    }

    public Season activateSeason(String actor, String seasonId) {
        ensureNoActiveRuntimeStateForSeasonSwitch();
        Season activated = seasonService.activate(seasonId);
        audit(actor, "season.activate", "season=" + activated.id().value());
        return activated;
    }

    public List<AuditEntry> recentAudit(int limit) {
        return auditRepository.recent(limit);
    }

    public void audit(String actor, String action, String details) {
        auditRepository.append(new AuditEntry(UUID.randomUUID(), clock.instant(), actor, action, details));
    }

    private void ensureNoActiveRuntimeStateForRegistryReload() {
        if (queueService.activeTicketCount() > 0
                || matchLifecycleService.activeMatchCount() > 0
                || arenaRegistryService.activeReservationCount() > 0) {
            throw new IllegalStateException(
                    "registry reload requires no active queue tickets, matches, or arena reservations");
        }
    }

    private void ensureNoActiveRuntimeStateForSeasonSwitch() {
        if (queueService.activeTicketCount() > 0
                || matchLifecycleService.activeMatchCount() > 0
                || duelRequestService.pendingRequestCount() > 0) {
            throw new IllegalStateException(
                    "season activation requires no active queue tickets, matches, or pending duel requests");
        }
    }

    private static <T> T load(RegistryLoader<T> loader, String name) {
        try {
            return loader.load();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load " + name + ".", exception);
        }
    }
}
