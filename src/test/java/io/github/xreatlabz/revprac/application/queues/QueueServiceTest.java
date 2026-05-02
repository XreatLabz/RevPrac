package io.github.xreatlabz.revprac.application.queues;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryDuelRequestRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryKitRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryQueueTicketRepository;
import io.github.xreatlabz.revprac.application.config.QueueConfig;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.application.ratings.RatingService;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitInventory;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchSide;
import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.domain.queues.QueueKey;
import io.github.xreatlabz.revprac.domain.queues.QueueMode;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketId;
import io.github.xreatlabz.revprac.domain.queues.QueuedMatchAssignment;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.ports.matches.MatchPlayerPort;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import io.github.xreatlabz.revprac.ports.queues.QueueTicketRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class QueueServiceTest {

    private static final Path APPLICATION_QUEUES_DIR =
            Path.of("src/main/java/io/github/xreatlabz/revprac/application/queues");
    private static final List<String> FORBIDDEN_TIME_AND_SCHEDULER_SNIPPETS = List.of(
            "System.currentTimeMillis(",
            "System.nanoTime(",
            "Instant.now(",
            "LocalDate.now(",
            "LocalDateTime.now(",
            "LocalTime.now(",
            "OffsetDateTime.now(",
            "ZonedDateTime.now(",
            "Clock.system",
            "Bukkit.getScheduler(",
            "getScheduler(",
            "runTask(",
            "runTaskLater(",
            "runTaskTimer(");

    @Test
    void joinMovesOnlineAvailableLobbyPlayerToQueueAndCreatesAnUnrankedTicket() {
        Harness harness = new Harness(new InMemoryQueueTicketRepository());
        PlayerId playerId = player("unranked-player");
        harness.join(playerId);

        QueueTicket ticket = harness.queueService.join(playerId, QueueMode.UNRANKED, harness.unrankedKitId(), 42L);

        assertEquals(playerId, ticket.playerId());
        assertEquals(new QueueKey(QueueMode.UNRANKED, harness.unrankedKitId()), ticket.key());
        assertEquals(0, ticket.searchRating());
        assertEquals(PlayerContext.QUEUE, harness.playerSessions.find(playerId).orElseThrow().context());
        assertEquals(ticket, harness.queueService.ticket(playerId).orElseThrow());
    }

    @Test
    void rankedJoinRejectsKitsThatDoNotAllowRankedQueues() {
        Harness harness = new Harness(new InMemoryQueueTicketRepository());
        PlayerId playerId = player("unranked-kit-player");
        harness.join(playerId);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> harness.queueService.join(playerId, QueueMode.RANKED, harness.unrankedKitId(), 7L));

        assertEquals("ranked queue is disabled for kit: boxing", failure.getMessage());
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(playerId).orElseThrow().context());
        assertTrue(harness.queueService.ticket(playerId).isEmpty());
    }

    @Test
    void joinUsesPlayerStatePortForOnlineChecksInsteadOfMatchPlayerState() {
        Harness harness = new Harness(new InMemoryQueueTicketRepository());
        PlayerId playerId = player("player-state-only-online");
        harness.playerStatePort.onlinePlayers.add(playerId);
        assertDoesNotThrow(() -> harness.playerSessionService.join(playerId));

        QueueTicket ticket = harness.queueService.join(playerId, QueueMode.UNRANKED, harness.rankedKitId(), 9L);

        assertEquals(playerId, ticket.playerId());
        assertEquals(PlayerContext.QUEUE, harness.playerSessions.find(playerId).orElseThrow().context());
    }

    @Test
    void shutdownAllUsesPlayerStatePortToDropOfflineTicketsWithoutRestore() {
        Harness harness = new Harness(new InMemoryQueueTicketRepository());
        PlayerId playerId = player("offline-by-player-state");
        harness.join(playerId);
        harness.queueService.join(playerId, QueueMode.UNRANKED, harness.rankedKitId(), 10L);
        harness.playerStatePort.onlinePlayers.remove(playerId);

        harness.queueService.shutdownAll();

        assertTrue(harness.queueService.ticket(playerId).isEmpty());
        assertTrue(harness.playerStatePort.restoredPlayers.isEmpty());
        assertEquals(PlayerContext.QUEUE, harness.playerSessions.find(playerId).orElseThrow().context());
    }

    @Test
    void duplicateAndCrossModeJoinsAreRejectedWhileTheOriginalTicketRemainsActive() {
        Harness harness = new Harness(new InMemoryQueueTicketRepository());
        PlayerId playerId = player("busy-player");
        harness.join(playerId);
        QueueTicket firstTicket = harness.queueService.join(playerId, QueueMode.UNRANKED, harness.rankedKitId(), 10L);

        IllegalStateException duplicateJoin = assertThrows(
                IllegalStateException.class,
                () -> harness.queueService.join(playerId, QueueMode.UNRANKED, harness.rankedKitId(), 11L));
        assertEquals("player is already busy", duplicateJoin.getMessage());

        IllegalStateException crossModeJoin = assertThrows(
                IllegalStateException.class,
                () -> harness.queueService.join(playerId, QueueMode.RANKED, harness.rankedKitId(), 12L));
        assertEquals("player is already busy", crossModeJoin.getMessage());

        assertEquals(firstTicket, harness.queueService.ticket(playerId).orElseThrow());
        assertEquals(PlayerContext.QUEUE, harness.playerSessions.find(playerId).orElseThrow().context());
    }

    @Test
    void leaveDeletesTheTicketAndReturnsThePlayerToLobby() {
        Harness harness = new Harness(new InMemoryQueueTicketRepository());
        PlayerId playerId = player("leave-player");
        harness.join(playerId);
        QueueTicket created = harness.queueService.join(playerId, QueueMode.RANKED, harness.rankedKitId(), 15L);

        QueueTicket left = harness.queueService.leave(playerId);

        assertEquals(created, left);
        assertTrue(harness.queueService.ticket(playerId).isEmpty());
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(playerId).orElseThrow().context());
    }

    @Test
    void firstRankedJoinPersistsADurableRatingSeedForThePlayerAndKit() {
        Harness harness = new Harness(new InMemoryQueueTicketRepository());
        PlayerId playerId = player("durable-seed-player");
        harness.join(playerId);

        QueueTicket ticket = harness.queueService.join(playerId, QueueMode.RANKED, harness.rankedKitId(), 17L);

        assertEquals(QueueConfig.DEFAULT_RANKED_BASE_RATING, ticket.searchRating());
        assertEquals(
                new PlayerRating(
                        playerId,
                        harness.rankedKitId(),
                        QueueConfig.DEFAULT_RANKED_BASE_RATING,
                        0,
                        0,
                        Instant.parse("2026-05-01T12:00:00Z")),
                harness.ratingRepository.find(playerId, harness.rankedKitId()).orElseThrow());
    }

    @Test
    void failedLeaveRestoreKeepsTheActiveTicketForRetry() {
        Harness harness = new Harness(new InMemoryQueueTicketRepository());
        PlayerId playerId = player("failed-leave-restore-player");
        harness.join(playerId);
        QueueTicket created = harness.queueService.join(playerId, QueueMode.RANKED, harness.rankedKitId(), 16L);
        harness.playerStatePort.failingRestores.add(playerId);

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> harness.queueService.leave(playerId));

        assertTrue(failure.getMessage().contains("restore failed"));
        assertEquals(created, harness.queueService.ticket(playerId).orElseThrow());
        assertEquals(PlayerContext.QUEUE, harness.playerSessions.find(playerId).orElseThrow().context());
    }

    @Test
    void handleQuitDeletesTheTicketAndLeavesPlayerSessionQuitHandlingAvailable() {
        Harness harness = new Harness(new InMemoryQueueTicketRepository());
        PlayerId playerId = player("quit-player");
        harness.join(playerId);
        harness.queueService.join(playerId, QueueMode.RANKED, harness.rankedKitId(), 20L);

        harness.queueService.handleQuit(playerId);

        assertTrue(harness.queueService.ticket(playerId).isEmpty());
        assertEquals(PlayerContext.QUEUE, harness.playerSessions.find(playerId).orElseThrow().context());

        harness.playerSessionService.quit(playerId);

        assertTrue(harness.pendingRestorations.find(playerId).isPresent());
    }

    @Test
    void closeIntakeRejectsNewJoinsAndShutdownAllDrainsQueuedPlayers() {
        Harness intakeHarness = new Harness(new InMemoryQueueTicketRepository());
        PlayerId blockedPlayer = player("blocked-player");
        intakeHarness.join(blockedPlayer);
        intakeHarness.queueService.closeIntake();

        IllegalStateException intakeClosed = assertThrows(
                IllegalStateException.class,
                () -> intakeHarness.queueService.join(blockedPlayer, QueueMode.UNRANKED, intakeHarness.rankedKitId(), 30L));
        assertEquals("queue intake is closed", intakeClosed.getMessage());

        Harness shutdownHarness = new Harness(new InMemoryQueueTicketRepository());
        PlayerId firstPlayer = player("shutdown-first");
        PlayerId secondPlayer = player("shutdown-second");
        shutdownHarness.join(firstPlayer);
        shutdownHarness.join(secondPlayer);
        shutdownHarness.queueService.join(firstPlayer, QueueMode.UNRANKED, shutdownHarness.rankedKitId(), 31L);
        shutdownHarness.queueService.join(secondPlayer, QueueMode.RANKED, shutdownHarness.rankedKitId(), 32L);

        shutdownHarness.queueService.shutdownAll();

        assertTrue(shutdownHarness.queueService.ticket(firstPlayer).isEmpty());
        assertTrue(shutdownHarness.queueService.ticket(secondPlayer).isEmpty());
        assertEquals(PlayerContext.LOBBY, shutdownHarness.playerSessions.find(firstPlayer).orElseThrow().context());
        assertEquals(PlayerContext.LOBBY, shutdownHarness.playerSessions.find(secondPlayer).orElseThrow().context());

        IllegalStateException shutdownClosed = assertThrows(
                IllegalStateException.class,
                () -> shutdownHarness.queueService.join(firstPlayer, QueueMode.UNRANKED, shutdownHarness.rankedKitId(), 33L));
        assertEquals("queue intake is closed", shutdownClosed.getMessage());
    }

    @Test
    void shutdownAllKeepsOnlineTicketsWhenLobbyRestoreFailsAndDrainsSuccessfulTickets() {
        Harness harness = new Harness(new InMemoryQueueTicketRepository());
        PlayerId flakyPlayer = player("shutdown-flaky-player");
        PlayerId healthyPlayer = player("shutdown-healthy-player");
        harness.join(flakyPlayer);
        harness.join(healthyPlayer);
        QueueTicket flakyTicket = harness.queueService.join(flakyPlayer, QueueMode.UNRANKED, harness.rankedKitId(), 34L);
        harness.queueService.join(healthyPlayer, QueueMode.RANKED, harness.rankedKitId(), 35L);
        harness.playerStatePort.failingRestores.add(flakyPlayer);

        IllegalStateException failure = assertThrows(IllegalStateException.class, harness.queueService::shutdownAll);

        assertTrue(failure.getMessage().contains("restore failed"));
        assertEquals(flakyTicket, harness.queueService.ticket(flakyPlayer).orElseThrow());
        assertTrue(harness.queueService.ticket(healthyPlayer).isEmpty());
        assertEquals(PlayerContext.QUEUE, harness.playerSessions.find(flakyPlayer).orElseThrow().context());
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(healthyPlayer).orElseThrow().context());

        harness.playerStatePort.failingRestores.remove(flakyPlayer);

        harness.queueService.shutdownAll();

        assertTrue(harness.queueService.ticket(flakyPlayer).isEmpty());
        assertTrue(harness.queueService.ticket(healthyPlayer).isEmpty());
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(flakyPlayer).orElseThrow().context());
    }

    @Test
    void failedTicketCreationRollsThePlayerBackToLobby() {
        Harness harness = new Harness(new RejectingCreateQueueTicketRepository());
        PlayerId playerId = player("rollback-player");
        harness.join(playerId);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> harness.queueService.join(playerId, QueueMode.RANKED, harness.rankedKitId(), 44L));

        assertEquals("player already has an active queue ticket", failure.getMessage());
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(playerId).orElseThrow().context());
        assertTrue(harness.queueService.ticket(playerId).isEmpty());
        assertTrue(harness.playerStatePort.restoredPlayers.contains(playerId));
    }

    @Test
    void failedRatingServiceLookupAfterQueueTransitionRollsThePlayerBackToLobby() {
        Harness harness = new Harness(new InMemoryQueueTicketRepository(), new RatingService(new ThrowingRatingRepository()));
        PlayerId playerId = player("rating-lookup-failure-player");
        harness.join(playerId);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> harness.queueService.join(playerId, QueueMode.RANKED, harness.rankedKitId(), 45L));

        assertEquals("rating lookup failed", failure.getMessage());
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(playerId).orElseThrow().context());
        assertTrue(harness.queueService.ticket(playerId).isEmpty());
        assertTrue(harness.playerStatePort.restoredPlayers.contains(playerId));
    }

    @Test
    void failedRatingSeedPersistenceAfterTicketCreationDeletesTheTicketAndRollsThePlayerBackToLobby() {
        Harness harness = new Harness(new InMemoryQueueTicketRepository(), new RatingService(new ThrowingSeedSaveRatingRepository()));
        PlayerId playerId = player("rating-seed-save-failure-player");
        harness.join(playerId);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> harness.queueService.join(playerId, QueueMode.RANKED, harness.rankedKitId(), 46L));

        assertEquals("rating seed save failed", failure.getMessage());
        assertEquals(PlayerContext.LOBBY, harness.playerSessions.find(playerId).orElseThrow().context());
        assertTrue(harness.queueService.ticket(playerId).isEmpty());
        assertTrue(harness.playerStatePort.restoredPlayers.contains(playerId));
    }

    @Test
    void applicationQueuesStayFreeOfBukkitPaperImportsAndStaticTimeSchedulerCalls() throws IOException {
        try (Stream<Path> sources = Files.walk(APPLICATION_QUEUES_DIR)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String contents = Files.readString(source);
                assertFalse(contents.contains("org.bukkit"), "Source must not import Bukkit: " + source);
                assertFalse(contents.contains("io.papermc.paper"), "Source must not import Paper: " + source);
                for (String snippet : FORBIDDEN_TIME_AND_SCHEDULER_SNIPPETS) {
                    assertFalse(contents.contains(snippet), "Source must not use static time or scheduler calls: " + source);
                }
            }
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

    private static final class Harness {
        private final QueueTicketRepository queueTicketRepository;
        private final InMemoryPendingRestorationRepository pendingRestorations = new InMemoryPendingRestorationRepository();
        private final InMemoryPlayerSessionRepository playerSessions = new InMemoryPlayerSessionRepository();
        private final FakePlayerStatePort playerStatePort = new FakePlayerStatePort();
        private final FakePlayerRatingRepository ratingRepository;
        private final PlayerSessionService playerSessionService =
                new PlayerSessionService(playerSessions, pendingRestorations, playerStatePort);
        private final FakeMatchPlayerPort matchPlayerPort = new FakeMatchPlayerPort();
        private final KitRegistryService kitRegistryService =
                new KitRegistryService(new InMemoryKitRegistryRepository());
        private final PlayerAvailabilityService availabilityService = new PlayerAvailabilityService(
                new InMemoryMatchRepository(),
                new InMemoryDuelRequestRepository(),
                createQueueRepositoryPlaceholder());
        private final QueueService queueService;

        private Harness(QueueTicketRepository queueTicketRepository) {
            FakePlayerRatingRepository ratingRepository = new FakePlayerRatingRepository();
            this.queueTicketRepository = queueTicketRepository;
            this.ratingRepository = ratingRepository;
            this.queueService = new QueueService(
                    queueTicketRepository,
                    new RatingService(ratingRepository),
                    new PlayerAvailabilityService(
                            new InMemoryMatchRepository(),
                            new InMemoryDuelRequestRepository(),
                            queueTicketRepository),
                    playerSessionService,
                    kitRegistryService,
                    playerStatePort,
                    Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC),
                    QueueConfig.defaults());
            kitRegistryService.register(kitDefinition(rankedKitId(), "Ranked Kit", true));
            kitRegistryService.register(kitDefinition(unrankedKitId(), "Boxing", false));
        }

        private Harness(QueueTicketRepository queueTicketRepository, RatingService ratingService) {
            this.queueTicketRepository = queueTicketRepository;
            this.ratingRepository = null;
            this.queueService = new QueueService(
                    queueTicketRepository,
                    ratingService,
                    new PlayerAvailabilityService(
                            new InMemoryMatchRepository(),
                            new InMemoryDuelRequestRepository(),
                            queueTicketRepository),
                    playerSessionService,
                    kitRegistryService,
                    playerStatePort,
                    Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC),
                    QueueConfig.defaults());
            kitRegistryService.register(kitDefinition(rankedKitId(), "Ranked Kit", true));
            kitRegistryService.register(kitDefinition(unrankedKitId(), "Boxing", false));
        }

        private void join(PlayerId playerId) {
            matchPlayerPort.onlinePlayers.add(playerId);
            playerStatePort.onlinePlayers.add(playerId);
            assertDoesNotThrow(() -> playerSessionService.join(playerId));
        }

        private KitId rankedKitId() {
            return new KitId("nodebuff");
        }

        private KitId unrankedKitId() {
            return new KitId("boxing");
        }

        private static QueueTicketRepository createQueueRepositoryPlaceholder() {
            return new InMemoryQueueTicketRepository();
        }
    }

    private static final class FakePlayerStatePort implements PlayerStatePort {
        private final Set<PlayerId> onlinePlayers = new HashSet<>();
        private final Set<PlayerId> failingRestores = new HashSet<>();
        private final List<PlayerId> restoredPlayers = new java.util.ArrayList<>();

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return snapshot(playerId);
        }

        @Override
        public void restore(PlayerId playerId, PlayerSafetySnapshot snapshot) {
            restoredPlayers.add(playerId);
            if (failingRestores.contains(playerId)) {
                throw new IllegalStateException("restore failed for " + playerId.value());
            }
        }

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }
    }

    private static final class FakePlayerRatingRepository implements PlayerRatingRepository {
        private final Map<RatingKey, PlayerRating> ratings = new ConcurrentHashMap<>();

        @Override
        public Optional<PlayerRating> find(PlayerId playerId, KitId kitId) {
            return Optional.ofNullable(ratings.get(new RatingKey(playerId, kitId)));
        }

        @Override
        public void upsert(PlayerRating rating) {
            ratings.put(new RatingKey(rating.playerId(), rating.kitId()), rating);
        }
    }

    private static final class ThrowingRatingRepository implements PlayerRatingRepository {

        @Override
        public Optional<PlayerRating> find(PlayerId playerId, KitId kitId) {
            throw new IllegalStateException("rating lookup failed");
        }

        @Override
        public void upsert(PlayerRating rating) {
            throw new UnsupportedOperationException("not needed for QueueService tests");
        }
    }

    private static final class ThrowingSeedSaveRatingRepository implements PlayerRatingRepository {

        @Override
        public Optional<PlayerRating> find(PlayerId playerId, KitId kitId) {
            return Optional.empty();
        }

        @Override
        public void upsert(PlayerRating rating) {
            throw new IllegalStateException("rating seed save failed");
        }
    }

    private record RatingKey(PlayerId playerId, KitId kitId) {
    }

    private static final class FakeMatchPlayerPort implements MatchPlayerPort {
        private final Set<PlayerId> onlinePlayers = new HashSet<>();

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }

        @Override
        public void prepareCombatant(
                PlayerId playerId,
                Match match,
                MatchSide side,
                io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition arenaDefinition,
                KitDefinition kitDefinition) {
            throw new UnsupportedOperationException("not needed for QueueService tests");
        }

        @Override
        public void prepareSpectator(
                PlayerId playerId,
                Match match,
                io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition arenaDefinition) {
            throw new UnsupportedOperationException("not needed for QueueService tests");
        }

        @Override
        public void clearMatchState(PlayerId playerId) {
            throw new UnsupportedOperationException("not needed for QueueService tests");
        }
    }

    private static final class RejectingCreateQueueTicketRepository implements QueueTicketRepository {

        @Override
        public Optional<QueueTicket> find(QueueTicketId ticketId) {
            return Optional.empty();
        }

        @Override
        public Collection<QueueTicket> findAll() {
            return List.of();
        }

        @Override
        public Optional<QueueTicket> findByPlayer(PlayerId playerId) {
            return Optional.empty();
        }

        @Override
        public Collection<QueueTicket> findSearchingByKey(QueueKey queueKey) {
            return List.of();
        }

        @Override
        public boolean create(QueueTicket ticket) {
            return false;
        }

        @Override
        public void save(QueueTicket ticket) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<QueuedMatchAssignment> claimPair(QueueTicketId firstId, QueueTicketId secondId) {
            return Optional.empty();
        }

        @Override
        public void restoreSearching(QueueTicketId firstId, QueueTicketId secondId) {
        }

        @Override
        public void delete(QueueTicketId ticketId) {
        }

        @Override
        public void deleteByPlayer(PlayerId playerId) {
        }
    }

    private static KitDefinition kitDefinition(KitId kitId, String displayName, boolean ranked) {
        return new KitDefinition(
                kitId,
                displayName,
                new KitInventory(List.of("sword"), List.of("helmet", "chest", "legs", "boots"), List.of("rod"), 0),
                List.of(),
                new KitRules(false, false, false, ranked),
                true);
    }
}
