package io.github.xreatlabz.revprac.application.matches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryArenaRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryDuelRequestRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryKitRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryQueueTicketRepository;
import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.queues.PlayerAvailabilityService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.domain.arenas.ArenaCuboid;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitInventory;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import io.github.xreatlabz.revprac.domain.matches.DuelRequest;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestId;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestState;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchEvent;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.matches.MatchParticipants;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.domain.queues.QueueKey;
import io.github.xreatlabz.revprac.domain.queues.QueueMode;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketId;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketState;
import io.github.xreatlabz.revprac.ports.arenas.ArenaResetPort;
import io.github.xreatlabz.revprac.ports.matches.MatchPlayerPort;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class DuelRequestServiceTest {

    private static final Path APPLICATION_MATCHES_DIR =
            Path.of("src/main/java/io/github/xreatlabz/revprac/application/matches");
    private static final Path PORTS_MATCHES_DIR =
            Path.of("src/main/java/io/github/xreatlabz/revprac/ports/matches");

    @Test
    void requestRejectsSelfDuelsOfflinePlayersBusyPlayersAndMissingResources() {
        Harness harness = new Harness(new AdjustableClock(Instant.parse("2026-05-01T12:00:00Z")));
        harness.join(harness.requester());
        harness.join(harness.target());
        harness.matchPlayerPort.onlinePlayers.remove(harness.target());

        IllegalArgumentException selfDuel = assertThrows(
                IllegalArgumentException.class,
                () -> harness.duelRequestService.request(
                        harness.requester(), harness.requester(), harness.arenaId(), harness.kitId()));
        assertEquals("requester and target must be different players", selfDuel.getMessage());

        IllegalStateException offline = assertThrows(
                IllegalStateException.class,
                () -> harness.duelRequestService.request(
                        harness.requester(), harness.target(), harness.arenaId(), harness.kitId()));
        assertEquals("target is offline", offline.getMessage());

        harness.matchPlayerPort.onlinePlayers.add(harness.target());
        harness.startAcceptedDuel();

        IllegalStateException busy = assertThrows(
                IllegalStateException.class,
                () -> harness.duelRequestService.request(
                        harness.requester(), harness.target(), harness.arenaId(), harness.kitId()));
        assertEquals("requester is already busy", busy.getMessage());

        Harness missingResourceHarness = new Harness(new AdjustableClock(Instant.parse("2026-05-01T12:00:00Z")));
        missingResourceHarness.join(missingResourceHarness.requester());
        missingResourceHarness.join(missingResourceHarness.target());

        IllegalArgumentException missingArena = assertThrows(
                IllegalArgumentException.class,
                () -> missingResourceHarness.duelRequestService.request(
                        missingResourceHarness.requester(),
                        missingResourceHarness.target(),
                        new ArenaId("missing-arena"),
                        missingResourceHarness.kitId()));
        assertEquals("unknown arena: missing-arena", missingArena.getMessage());

        IllegalArgumentException missingKit = assertThrows(
                IllegalArgumentException.class,
                () -> missingResourceHarness.duelRequestService.request(
                        missingResourceHarness.requester(),
                        missingResourceHarness.target(),
                        missingResourceHarness.arenaId(),
                        new KitId("missing-kit")));
        assertEquals("unknown kit: missing-kit", missingKit.getMessage());
    }

    @Test
    void requestRejectsDuplicatePendingRequests() {
        Harness harness = new Harness(new AdjustableClock(Instant.parse("2026-05-01T12:00:00Z")));
        harness.join(harness.requester());
        harness.join(harness.target());

        harness.duelRequestService.request(harness.requester(), harness.target(), harness.arenaId(), harness.kitId());

        IllegalStateException duplicate = assertThrows(
                IllegalStateException.class,
                () -> harness.duelRequestService.request(
                        harness.requester(), harness.target(), harness.arenaId(), harness.kitId()));
        assertEquals("a pending duel request already exists for these players", duplicate.getMessage());
    }

    @Test
    void requestRejectsQueuedRequesterOrTarget() {
        Harness requesterHarness = new Harness(new AdjustableClock(Instant.parse("2026-05-01T12:00:00Z")));
        requesterHarness.join(requesterHarness.requester());
        requesterHarness.join(requesterHarness.target());
        assertTrue(requesterHarness.queueTicketRepository.create(requesterHarness.queueTicket(
                "queued-requester", requesterHarness.requester(), QueueTicketState.SEARCHING)));

        IllegalStateException requesterQueued = assertThrows(
                IllegalStateException.class,
                () -> requesterHarness.duelRequestService.request(
                        requesterHarness.requester(),
                        requesterHarness.target(),
                        requesterHarness.arenaId(),
                        requesterHarness.kitId()));
        assertEquals("requester is already busy", requesterQueued.getMessage());

        Harness targetHarness = new Harness(new AdjustableClock(Instant.parse("2026-05-01T12:00:00Z")));
        targetHarness.join(targetHarness.requester());
        targetHarness.join(targetHarness.target());
        assertTrue(targetHarness.queueTicketRepository.create(targetHarness.queueTicket(
                "queued-target", targetHarness.target(), QueueTicketState.PAIRING)));

        IllegalStateException targetQueued = assertThrows(
                IllegalStateException.class,
                () -> targetHarness.duelRequestService.request(
                        targetHarness.requester(),
                        targetHarness.target(),
                        targetHarness.arenaId(),
                        targetHarness.kitId()));
        assertEquals("target is already busy", targetQueued.getMessage());
    }

    @Test
    void acceptRejectsRequesterOrTargetQueuedAfterRequestCreation() {
        Harness requesterHarness = new Harness(new AdjustableClock(Instant.parse("2026-05-01T12:00:00Z")));
        requesterHarness.join(requesterHarness.requester());
        requesterHarness.join(requesterHarness.target());
        DuelRequest requesterRequest = requesterHarness.duelRequestService.request(
                requesterHarness.requester(),
                requesterHarness.target(),
                requesterHarness.arenaId(),
                requesterHarness.kitId());
        assertTrue(requesterHarness.queueTicketRepository.create(requesterHarness.queueTicket(
                "queued-requester-before-accept", requesterHarness.requester(), QueueTicketState.SEARCHING)));

        IllegalStateException requesterQueued = assertThrows(
                IllegalStateException.class,
                () -> requesterHarness.duelRequestService.accept(
                        requesterHarness.requester(), requesterHarness.target()));

        assertEquals("requester is already busy", requesterQueued.getMessage());
        assertEquals(
                DuelRequestState.PENDING,
                requesterHarness.requestRepository.find(requesterRequest.id()).orElseThrow().state());
        assertTrue(requesterHarness.matchRepository.findAll().isEmpty());

        Harness targetHarness = new Harness(new AdjustableClock(Instant.parse("2026-05-01T12:00:00Z")));
        targetHarness.join(targetHarness.requester());
        targetHarness.join(targetHarness.target());
        DuelRequest targetRequest = targetHarness.duelRequestService.request(
                targetHarness.requester(),
                targetHarness.target(),
                targetHarness.arenaId(),
                targetHarness.kitId());
        assertTrue(targetHarness.queueTicketRepository.create(targetHarness.queueTicket(
                "queued-target-before-accept", targetHarness.target(), QueueTicketState.PAIRING)));

        IllegalStateException targetQueued = assertThrows(
                IllegalStateException.class,
                () -> targetHarness.duelRequestService.accept(targetHarness.requester(), targetHarness.target()));

        assertEquals("target is already busy", targetQueued.getMessage());
        assertEquals(
                DuelRequestState.PENDING,
                targetHarness.requestRepository.find(targetRequest.id()).orElseThrow().state());
        assertTrue(targetHarness.matchRepository.findAll().isEmpty());
    }

    @Test
    void acceptRejectsRetainedCompletedMatchUntilItIsDrained() {
        Harness harness = new Harness(new AdjustableClock(Instant.parse("2026-05-01T12:00:00Z")));
        harness.join(harness.requester());
        harness.join(harness.target());
        DuelRequest request = harness.duelRequestService.request(
                harness.requester(), harness.target(), harness.arenaId(), harness.kitId());
        harness.matchRepository.save(retainedCompletedMatch(harness));

        IllegalStateException busy = assertThrows(
                IllegalStateException.class,
                () -> harness.duelRequestService.accept(harness.requester(), harness.target()));

        assertEquals("requester is already busy", busy.getMessage());
        assertEquals(DuelRequestState.PENDING, harness.requestRepository.find(request.id()).orElseThrow().state());
        assertEquals(1, harness.matchRepository.findAll().size());
    }

    @Test
    void repeatedPairOperationsUsePendingRequestWhenAcceptedHistoryIsRetained() {
        Harness acceptHarness = new Harness(new AdjustableClock(Instant.parse("2026-05-01T12:00:00Z")));
        acceptHarness.join(acceptHarness.requester());
        acceptHarness.join(acceptHarness.target());
        DuelRequest acceptedHistory = acceptHarness.createAcceptedHistory();
        DuelRequest newPending = acceptHarness.duelRequestService.request(
                acceptHarness.requester(), acceptHarness.target(), acceptHarness.arenaId(), acceptHarness.kitId());

        IllegalStateException duplicate = assertThrows(
                IllegalStateException.class,
                () -> acceptHarness.duelRequestService.request(
                        acceptHarness.requester(), acceptHarness.target(), acceptHarness.arenaId(), acceptHarness.kitId()));
        assertEquals("a pending duel request already exists for these players", duplicate.getMessage());

        Match nextMatch = acceptHarness.duelRequestService.accept(acceptHarness.requester(), acceptHarness.target());

        assertEquals(DuelRequestState.ACCEPTED, acceptHarness.requestRepository.find(acceptedHistory.id()).orElseThrow().state());
        assertEquals(DuelRequestState.ACCEPTED, acceptHarness.requestRepository.find(newPending.id()).orElseThrow().state());
        assertEquals(nextMatch.id(), acceptHarness.matchRepository.findByPlayer(acceptHarness.requester()).orElseThrow().id());

        Harness declineHarness = new Harness(new AdjustableClock(Instant.parse("2026-05-01T12:00:00Z")));
        declineHarness.join(declineHarness.requester());
        declineHarness.join(declineHarness.target());
        DuelRequest declineHistory = declineHarness.createAcceptedHistory();
        DuelRequest declinePending = declineHarness.duelRequestService.request(
                declineHarness.requester(), declineHarness.target(), declineHarness.arenaId(), declineHarness.kitId());

        declineHarness.duelRequestService.decline(declineHarness.requester(), declineHarness.target());

        assertEquals(DuelRequestState.ACCEPTED, declineHarness.requestRepository.find(declineHistory.id()).orElseThrow().state());
        assertTrue(declineHarness.requestRepository.find(declinePending.id()).isEmpty());

        Harness cancelHarness = new Harness(new AdjustableClock(Instant.parse("2026-05-01T12:00:00Z")));
        cancelHarness.join(cancelHarness.requester());
        cancelHarness.join(cancelHarness.target());
        DuelRequest cancelHistory = cancelHarness.createAcceptedHistory();
        DuelRequest cancelPending = cancelHarness.duelRequestService.request(
                cancelHarness.requester(), cancelHarness.target(), cancelHarness.arenaId(), cancelHarness.kitId());

        cancelHarness.duelRequestService.cancel(cancelHarness.requester(), cancelHarness.target());

        assertEquals(DuelRequestState.ACCEPTED, cancelHarness.requestRepository.find(cancelHistory.id()).orElseThrow().state());
        assertTrue(cancelHarness.requestRepository.find(cancelPending.id()).isEmpty());
    }

    @Test
    void acceptMarksRequestAcceptedAndStartsCountdownMatchWithOneReservation() {
        Harness harness = new Harness(new AdjustableClock(Instant.parse("2026-05-01T12:00:00Z")));
        harness.join(harness.requester());
        harness.join(harness.target());

        DuelRequest created = harness.duelRequestService.request(
                harness.requester(), harness.target(), harness.arenaId(), harness.kitId());
        Match match = harness.duelRequestService.accept(harness.requester(), harness.target());

        DuelRequest stored = harness.requestRepository.find(created.id()).orElseThrow();
        assertEquals(DuelRequestState.ACCEPTED, stored.state());
        assertEquals(1, harness.matchRepository.findAll().size());
        assertEquals(match.id(), harness.matchRepository.findByPlayer(harness.requester()).orElseThrow().id());
        assertEquals("COUNTDOWN", match.state().name());

        IllegalStateException arenaBusy = assertThrows(
                IllegalStateException.class,
                () -> harness.arenaRegistryService.reserve(harness.arenaId(), "probe"));
        assertEquals("Arena is already reserved: arena-one", arenaBusy.getMessage());

        assertTrue(
                harness.events.stream().anyMatch(MatchEvent.DuelRequestCreated.class::isInstance),
                "request creation should emit an event");
        assertTrue(
                harness.events.stream().anyMatch(MatchEvent.DuelRequestAccepted.class::isInstance),
                "accept should emit an event");
        assertTrue(
                harness.events.stream().anyMatch(MatchEvent.MatchCountdownStarted.class::isInstance),
                "accept should start the countdown match");
    }

    @Test
    void acceptPersistsAcceptedRequestBeforeCountdownAndUsesMonotonicEventSequences() {
        Harness harness = new Harness(new AdjustableClock(Instant.parse("2026-05-01T12:00:00Z")));
        harness.join(harness.requester());
        harness.join(harness.target());

        DuelRequest created = harness.duelRequestService.request(
                harness.requester(), harness.target(), harness.arenaId(), harness.kitId());
        harness.observeAcceptedStateDuringCountdown(created.id());

        Match match = harness.duelRequestService.accept(harness.requester(), harness.target());

        assertTrue(
                harness.acceptedPersistedBeforeCountdown(),
                "accepted duel request should already be persisted when countdown starts");

        List<MatchEvent> events = harness.events;
        assertEquals(3, events.size(), "request, accept, and countdown should be the only emitted events");
        assertEquals(MatchEvent.DuelRequestCreated.class, events.get(0).getClass());
        assertEquals(MatchEvent.DuelRequestAccepted.class, events.get(1).getClass());
        assertEquals(MatchEvent.MatchCountdownStarted.class, events.get(2).getClass());

        MatchEvent.DuelRequestAccepted acceptedEvent = (MatchEvent.DuelRequestAccepted) events.get(1);
        MatchEvent.MatchCountdownStarted countdownEvent = (MatchEvent.MatchCountdownStarted) events.get(2);
        assertEquals(match.id(), acceptedEvent.matchId());
        assertEquals(match.id(), countdownEvent.matchId());
        assertTrue(events.get(0).sequence() < acceptedEvent.sequence());
        assertTrue(acceptedEvent.sequence() < countdownEvent.sequence());
    }

    @Test
    void duelRequestFlowIgnoresEventSinkFailures() {
        Harness harness = new Harness(
                new AdjustableClock(Instant.parse("2026-05-01T12:00:00Z")),
                event -> {
                    throw new IllegalStateException("listener failed");
                });
        harness.join(harness.requester());
        harness.join(harness.target());

        DuelRequest created = harness.duelRequestService.request(
                harness.requester(), harness.target(), harness.arenaId(), harness.kitId());
        Match match = harness.duelRequestService.accept(harness.requester(), harness.target());

        assertEquals(created.id(), harness.requestRepository.find(created.id()).orElseThrow().id());
        assertEquals(DuelRequestState.ACCEPTED, harness.requestRepository.find(created.id()).orElseThrow().state());
        assertEquals(match.id(), harness.matchRepository.findByPlayer(harness.requester()).orElseThrow().id());
        assertEquals(3, harness.events.size(), "all event attempts should still be observed by the harness");
    }

    @Test
    void declineCancelAndExpiryRemovePendingIntakeWithoutCreatingMatches() {
        AdjustableClock clock = new AdjustableClock(Instant.parse("2026-05-01T12:00:00Z"));
        Harness declineHarness = new Harness(clock);
        declineHarness.join(declineHarness.requester());
        declineHarness.join(declineHarness.target());
        DuelRequest declineRequest = declineHarness.duelRequestService.request(
                declineHarness.requester(), declineHarness.target(), declineHarness.arenaId(), declineHarness.kitId());

        declineHarness.duelRequestService.decline(declineHarness.requester(), declineHarness.target());

        assertTrue(declineHarness.requestRepository.find(declineRequest.id()).isEmpty());
        assertTrue(declineHarness.matchRepository.findAll().isEmpty());

        Harness cancelHarness = new Harness(clock);
        cancelHarness.join(cancelHarness.requester());
        cancelHarness.join(cancelHarness.target());
        DuelRequest cancelRequest = cancelHarness.duelRequestService.request(
                cancelHarness.requester(), cancelHarness.target(), cancelHarness.arenaId(), cancelHarness.kitId());

        cancelHarness.duelRequestService.cancel(cancelHarness.requester(), cancelHarness.target());

        assertTrue(cancelHarness.requestRepository.find(cancelRequest.id()).isEmpty());
        assertTrue(cancelHarness.matchRepository.findAll().isEmpty());

        Harness expiryHarness = new Harness(clock);
        expiryHarness.join(expiryHarness.requester());
        expiryHarness.join(expiryHarness.target());
        DuelRequest expiring = expiryHarness.duelRequestService.request(
                expiryHarness.requester(), expiryHarness.target(), expiryHarness.arenaId(), expiryHarness.kitId());

        clock.advance(Duration.ofSeconds(31));
        List<DuelRequest> expired = expiryHarness.duelRequestService.expirePendingRequests();

        assertEquals(List.of(expiring.id()), expired.stream().map(DuelRequest::id).toList());
        assertTrue(expiryHarness.requestRepository.find(expiring.id()).isEmpty());
        assertTrue(expiryHarness.matchRepository.findAll().isEmpty());
    }

    @Test
    void applicationAndPortSourcesStayFreeOfBukkitPaperImportsAndStaticTimeCalls() throws IOException {
        assertNoForbiddenImports(APPLICATION_MATCHES_DIR);
        assertNoForbiddenImports(PORTS_MATCHES_DIR);
        assertNoForbiddenTimeCalls(APPLICATION_MATCHES_DIR);
    }

    @Test
    void constructionRequiresInjectedQueueAwareAvailabilityService() {
        Constructor<?>[] constructors = DuelRequestService.class.getConstructors();

        assertEquals(1, constructors.length, "DuelRequestService should expose one queue-aware constructor");
        assertTrue(
                List.of(constructors[0].getParameterTypes()).contains(PlayerAvailabilityService.class),
                "DuelRequestService construction must require PlayerAvailabilityService");
    }

    private static void assertNoForbiddenImports(Path directory) throws IOException {
        try (Stream<Path> sources = Files.walk(directory)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String contents = Files.readString(source);
                assertFalse(contents.contains("org.bukkit"), "Source must not import Bukkit: " + source);
                assertFalse(contents.contains("io.papermc.paper"), "Source must not import Paper: " + source);
            }
        }
    }

    private static void assertNoForbiddenTimeCalls(Path directory) throws IOException {
        try (Stream<Path> sources = Files.walk(directory)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String contents = Files.readString(source);
                assertFalse(contents.contains("System.currentTimeMillis"), "Source must not use wall-clock statics: " + source);
                assertFalse(contents.contains("Instant.now"), "Source must use injected Clock: " + source);
                assertFalse(contents.contains("LocalDateTime.now"), "Source must use injected Clock: " + source);
                assertFalse(contents.contains("Thread.sleep"), "Source must not sleep: " + source);
                assertFalse(contents.contains("runTaskLater"), "Source must not schedule from application layer: " + source);
                assertFalse(contents.contains("BukkitScheduler"), "Source must not use Bukkit scheduler: " + source);
            }
        }
    }

    private static final class Harness {
        private final AdjustableClock clock;
        private final InMemoryDuelRequestRepository requestRepository = new InMemoryDuelRequestRepository();
        private final InMemoryMatchRepository matchRepository = new InMemoryMatchRepository();
        private final InMemoryQueueTicketRepository queueTicketRepository = new InMemoryQueueTicketRepository();
        private final FakeArenaResetPort arenaResetPort = new FakeArenaResetPort();
        private final ArenaRegistryService arenaRegistryService =
                new ArenaRegistryService(new InMemoryArenaRegistryRepository(), arenaResetPort);
        private final KitRegistryService kitRegistryService =
                new KitRegistryService(new InMemoryKitRegistryRepository());
        private final FakePlayerStatePort playerStatePort = new FakePlayerStatePort();
        private final PlayerSessionService playerSessionService = new PlayerSessionService(
                new InMemoryPlayerSessionRepository(),
                new InMemoryPendingRestorationRepository(),
                playerStatePort);
        private final FakeMatchPlayerPort matchPlayerPort = new FakeMatchPlayerPort();
        private final List<MatchEvent> events = new ArrayList<>();
        private DuelRequestId observedRequestIdDuringCountdown;
        private boolean acceptedPersistedBeforeCountdown;
        private final MatchLifecycleService matchLifecycleService;
        private final DuelRequestService duelRequestService;

        private Harness(AdjustableClock clock) {
            this(clock, event -> {
            });
        }

        private Harness(AdjustableClock clock, java.util.function.Consumer<MatchEvent> eventObserver) {
            this.clock = clock;
            java.util.function.Consumer<MatchEvent> eventSink = event -> {
                events.add(event);
                if (event instanceof MatchEvent.MatchCountdownStarted && observedRequestIdDuringCountdown != null) {
                    acceptedPersistedBeforeCountdown = requestRepository.find(observedRequestIdDuringCountdown)
                            .map(request -> request.state() == DuelRequestState.ACCEPTED)
                            .orElse(false);
                }
                eventObserver.accept(event);
            };
            this.matchLifecycleService = new MatchLifecycleService(
                    matchRepository,
                    playerSessionService,
                    arenaRegistryService,
                    kitRegistryService,
                    matchPlayerPort,
                    new MatchRuleset(3, 10, true),
                    eventSink);
            this.duelRequestService = new DuelRequestService(
                    requestRepository,
                    matchRepository,
                    arenaRegistryService,
                    kitRegistryService,
                    matchPlayerPort,
                    matchLifecycleService,
                    new PlayerAvailabilityService(matchRepository, requestRepository, queueTicketRepository),
                    this.clock,
                    Duration.ofSeconds(30),
                    eventSink);
            arenaRegistryService.register(arenaDefinition());
            kitRegistryService.register(kitDefinition());
            matchPlayerPort.onlinePlayers.addAll(Set.of(requester(), target(), spectator()));
            playerStatePort.onlinePlayers.addAll(Set.of(requester(), target(), spectator()));
        }

        private PlayerId requester() {
            return player("requester");
        }

        private PlayerId target() {
            return player("target");
        }

        private PlayerId spectator() {
            return player("spectator");
        }

        private ArenaId arenaId() {
            return new ArenaId("arena-one");
        }

        private KitId kitId() {
            return new KitId("kit-one");
        }

        private QueueTicket queueTicket(String seed, PlayerId playerId, QueueTicketState state) {
            return new QueueTicket(
                    new QueueTicketId(UUID.nameUUIDFromBytes(seed.getBytes())),
                    playerId,
                    new QueueKey(QueueMode.RANKED, kitId()),
                    10L,
                    1000,
                    state);
        }

        private void join(PlayerId playerId) {
            playerSessionService.join(playerId);
        }

        private Match startAcceptedDuel() {
            DuelRequest request = duelRequestService.request(requester(), target(), arenaId(), kitId());
            Match match = duelRequestService.accept(requester(), target());
            assertEquals(request.id(), requestRepository.find(request.id()).orElseThrow().id());
            return match;
        }

        private DuelRequest createAcceptedHistory() {
            DuelRequest request = duelRequestService.request(requester(), target(), arenaId(), kitId());
            duelRequestService.accept(requester(), target());
            matchLifecycleService.completeByDeath(target());
            assertEquals(DuelRequestState.ACCEPTED, requestRepository.find(request.id()).orElseThrow().state());
            assertTrue(matchRepository.findAll().isEmpty(), "accepted history setup should tear down the match");
            return request;
        }

        private void observeAcceptedStateDuringCountdown(DuelRequestId duelRequestId) {
            observedRequestIdDuringCountdown = duelRequestId;
            acceptedPersistedBeforeCountdown = false;
        }

        private boolean acceptedPersistedBeforeCountdown() {
            return acceptedPersistedBeforeCountdown;
        }

        private ArenaDefinition arenaDefinition() {
            return new ArenaDefinition(
                    arenaId(),
                    "Arena One",
                    new ArenaCuboid("minecraft:world", 0, 60, 0, 20, 90, 20),
                    new ArenaSpawnPoint("minecraft:world", 2.0d, 70.0d, 2.0d, 0.0f, 0.0f),
                    new ArenaSpawnPoint("minecraft:world", 18.0d, 70.0d, 18.0d, 180.0f, 0.0f),
                    true);
        }

        private KitDefinition kitDefinition() {
            return new KitDefinition(
                    kitId(),
                    "Kit One",
                    new KitInventory(List.of("sword"), List.of("helmet", "chest", "legs", "boots"), List.of("rod"), 0),
                    List.of(),
                    new KitRules(false, false, false, false),
                    true);
        }
    }

    private static Match retainedCompletedMatch(Harness harness) {
        return Match.create(
                        new MatchId(UUID.nameUUIDFromBytes("retained-completed-match".getBytes())),
                        new MatchParticipants(harness.requester(), harness.target()),
                        harness.arenaId(),
                        harness.kitId(),
                        new ArenaReservationId(UUID.nameUUIDFromBytes("retained-reservation".getBytes())),
                        new MatchRuleset(1, 10, true))
                .tickCountdown()
                .complete(MatchOutcome.shutdown(), Instant.parse("2026-05-02T15:10:00Z"));
    }

    private static final class AdjustableClock extends Clock {
        private Instant instant;

        private AdjustableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class FakeArenaResetPort implements ArenaResetPort {
        @Override
        public void reset(ArenaDefinition arenaDefinition) {
        }
    }

    private static final class FakePlayerStatePort implements PlayerStatePort {
        private final Set<PlayerId> onlinePlayers = new java.util.HashSet<>();

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return snapshot(playerId);
        }

        @Override
        public void restore(PlayerId playerId, PlayerSafetySnapshot snapshot) {
        }

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }
    }

    private static final class FakeMatchPlayerPort implements MatchPlayerPort {
        private final Set<PlayerId> onlinePlayers = new java.util.HashSet<>();

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }

        @Override
        public void prepareCombatant(
                PlayerId playerId,
                Match match,
                io.github.xreatlabz.revprac.domain.matches.MatchSide side,
                ArenaDefinition arenaDefinition,
                KitDefinition kitDefinition) {
        }

        @Override
        public void prepareSpectator(PlayerId playerId, Match match, ArenaDefinition arenaDefinition) {
        }

        @Override
        public void clearMatchState(PlayerId playerId) {
        }
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }

    private static PlayerSafetySnapshot snapshot(PlayerId playerId) {
        return new PlayerSafetySnapshot(
                new LocationSnapshot("minecraft:world", 10.0d, 70.0d, 10.0d, 0.0f, 0.0f),
                new InventorySnapshot(List.of(playerId.value().toString()), List.of(), List.of(), List.of(), null, 0),
                new PlayerStatusSnapshot("SURVIVAL", 20.0d, 20, 5.0f, 0.0f, 0, false, false, List.of()));
    }
}
