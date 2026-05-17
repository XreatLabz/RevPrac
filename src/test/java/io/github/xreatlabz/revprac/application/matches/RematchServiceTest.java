package io.github.xreatlabz.revprac.application.matches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryArenaRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryDuelRequestRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryKitRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchSettlementRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryQueueTicketRepository;
import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.application.queues.PlayerAvailabilityService;
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
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.matches.MatchParticipants;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.matches.MatchState;
import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.domain.stats.MatchSettlement;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import io.github.xreatlabz.revprac.ports.arenas.ArenaResetPort;
import io.github.xreatlabz.revprac.ports.matches.MatchPlayerPort;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class RematchServiceTest {

    @Test
    void latestMutualMatchIsChosenAcrossParticipantOrderAndOriginWithFreshRequestExpiry() {
        AdjustableClock clock = new AdjustableClock(Instant.parse("2026-05-06T12:00:00Z"));
        Harness harness = new Harness(clock);
        harness.recordCompletedMatch(
                "older-direct",
                MatchOrigin.DIRECT_DUEL,
                harness.requester(),
                harness.target(),
                new ArenaId("arena-old"),
                new KitId("kit-old"),
                Instant.parse("2026-05-06T11:59:20Z"));
        harness.recordCompletedMatch(
                "latest-queue",
                MatchOrigin.QUEUE_RANKED,
                harness.target(),
                harness.requester(),
                new ArenaId("arena-new"),
                new KitId("kit-new"),
                Instant.parse("2026-05-06T11:59:50Z"));

        DuelRequest created = harness.rematchService.request(harness.requester(), harness.target());

        assertEquals(new ArenaId("arena-new"), created.arenaId());
        assertEquals(new KitId("kit-new"), created.kitId());
        assertEquals(clock.instant(), created.createdAt());
        assertEquals(clock.instant().plusSeconds(30), created.expiresAt());
    }

    @Test
    void rematchSelectionPrefersNewestEligibleMatchEvenWhenHistoryOrderIsOutOfOrder() {
        AdjustableClock clock = new AdjustableClock(Instant.parse("2026-05-06T12:00:00Z"));
        Harness harness = new Harness(clock);
        MatchHistoryEntry older = historyEntry(
                "older-rematch",
                MatchOrigin.DIRECT_DUEL,
                harness.requester(),
                harness.target(),
                new ArenaId("arena-old"),
                new KitId("kit-old"),
                Instant.parse("2026-05-06T11:59:10Z"));
        MatchHistoryEntry newest = historyEntry(
                "newer-rematch",
                MatchOrigin.QUEUE_RANKED,
                harness.target(),
                harness.requester(),
                new ArenaId("arena-new"),
                new KitId("kit-new"),
                Instant.parse("2026-05-06T11:59:50Z"));
        RematchService rematchService = new RematchService(
                new OutOfOrderHistoryRepository(List.of(older, newest, older)),
                harness.duelRequestService,
                clock,
                Duration.ofSeconds(30));

        DuelRequest created = rematchService.request(harness.requester(), harness.target());

        assertEquals(new ArenaId("arena-new"), created.arenaId());
        assertEquals(new KitId("kit-new"), created.kitId());
    }

    @Test
    void rematchRejectsMissingHistoryAndExpiredHistoryAtTheExclusiveCutoff() {
        AdjustableClock noHistoryClock = new AdjustableClock(Instant.parse("2026-05-06T12:00:00Z"));
        Harness noHistoryHarness = new Harness(noHistoryClock);

        IllegalStateException noHistory = assertThrows(
                IllegalStateException.class,
                () -> noHistoryHarness.rematchService.request(noHistoryHarness.requester(), noHistoryHarness.target()));
        assertEquals("no recent match found for rematch", noHistory.getMessage());

        AdjustableClock cutoffClock = new AdjustableClock(Instant.parse("2026-05-06T12:00:30Z"));
        Harness cutoffHarness = new Harness(cutoffClock);
        cutoffHarness.recordCompletedMatch(
                "expired",
                MatchOrigin.DIRECT_DUEL,
                cutoffHarness.requester(),
                cutoffHarness.target(),
                cutoffHarness.arenaId(),
                cutoffHarness.kitId(),
                Instant.parse("2026-05-06T12:00:00Z"));

        IllegalStateException expired = assertThrows(
                IllegalStateException.class,
                () -> cutoffHarness.rematchService.request(cutoffHarness.requester(), cutoffHarness.target()));
        assertEquals("no recent match found for rematch", expired.getMessage());
    }

    @Test
    void rematchBubblesDuplicateBusyIntakeAndMissingResourceFailuresFromNormalRequests() {
        AdjustableClock duplicateClock = new AdjustableClock(Instant.parse("2026-05-06T12:00:00Z"));
        Harness duplicateHarness = new Harness(duplicateClock);
        duplicateHarness.recordCompletedMatch(
                "duplicate-source",
                MatchOrigin.DIRECT_DUEL,
                duplicateHarness.requester(),
                duplicateHarness.target(),
                duplicateHarness.arenaId(),
                duplicateHarness.kitId(),
                duplicateClock.instant().minusSeconds(5));
        duplicateHarness.duelRequestService.request(
                duplicateHarness.requester(), duplicateHarness.target(), duplicateHarness.arenaId(), duplicateHarness.kitId());

        IllegalStateException duplicate = assertThrows(
                IllegalStateException.class,
                () -> duplicateHarness.rematchService.request(duplicateHarness.requester(), duplicateHarness.target()));
        assertEquals("a pending duel request already exists for these players", duplicate.getMessage());

        AdjustableClock busyClock = new AdjustableClock(Instant.parse("2026-05-06T12:00:00Z"));
        Harness busyHarness = new Harness(busyClock);
        busyHarness.recordCompletedMatch(
                "busy-source",
                MatchOrigin.DIRECT_DUEL,
                busyHarness.requester(),
                busyHarness.target(),
                busyHarness.arenaId(),
                busyHarness.kitId(),
                busyClock.instant().minusSeconds(5));
        busyHarness.startAcceptedDuel();

        IllegalStateException busy = assertThrows(
                IllegalStateException.class,
                () -> busyHarness.rematchService.request(busyHarness.requester(), busyHarness.target()));
        assertEquals("requester is already busy", busy.getMessage());

        AdjustableClock intakeClock = new AdjustableClock(Instant.parse("2026-05-06T12:00:00Z"));
        Harness intakeHarness = new Harness(intakeClock);
        intakeHarness.recordCompletedMatch(
                "intake-source",
                MatchOrigin.DIRECT_DUEL,
                intakeHarness.requester(),
                intakeHarness.target(),
                intakeHarness.arenaId(),
                intakeHarness.kitId(),
                intakeClock.instant().minusSeconds(5));
        intakeHarness.duelRequestService.closeIntake();

        IllegalStateException intakeClosed = assertThrows(
                IllegalStateException.class,
                () -> intakeHarness.rematchService.request(intakeHarness.requester(), intakeHarness.target()));
        assertEquals("duel request intake is closed", intakeClosed.getMessage());

        AdjustableClock missingArenaClock = new AdjustableClock(Instant.parse("2026-05-06T12:00:00Z"));
        Harness missingArenaHarness = new Harness(missingArenaClock);
        missingArenaHarness.recordCompletedMatch(
                "missing-arena-source",
                MatchOrigin.DIRECT_DUEL,
                missingArenaHarness.requester(),
                missingArenaHarness.target(),
                new ArenaId("missing-arena"),
                missingArenaHarness.kitId(),
                missingArenaClock.instant().minusSeconds(5));

        IllegalArgumentException missingArena = assertThrows(
                IllegalArgumentException.class,
                () -> missingArenaHarness.rematchService.request(
                        missingArenaHarness.requester(), missingArenaHarness.target()));
        assertEquals("unknown arena: missing-arena", missingArena.getMessage());

        AdjustableClock missingKitClock = new AdjustableClock(Instant.parse("2026-05-06T12:00:00Z"));
        Harness missingKitHarness = new Harness(missingKitClock);
        missingKitHarness.recordCompletedMatch(
                "missing-kit-source",
                MatchOrigin.DIRECT_DUEL,
                missingKitHarness.requester(),
                missingKitHarness.target(),
                missingKitHarness.arenaId(),
                new KitId("missing-kit"),
                missingKitClock.instant().minusSeconds(5));

        IllegalArgumentException missingKit = assertThrows(
                IllegalArgumentException.class,
                () -> missingKitHarness.rematchService.request(
                        missingKitHarness.requester(), missingKitHarness.target()));
        assertEquals("unknown kit: missing-kit", missingKit.getMessage());
    }

    private static final class Harness {
        private final AdjustableClock clock;
        private final InMemoryDuelRequestRepository requestRepository = new InMemoryDuelRequestRepository();
        private final InMemoryMatchRepository matchRepository = new InMemoryMatchRepository();
        private final InMemoryMatchSettlementRepository settlementRepository = new InMemoryMatchSettlementRepository();
        private final InMemoryQueueTicketRepository queueTicketRepository = new InMemoryQueueTicketRepository();
        private final ArenaRegistryService arenaRegistryService =
                new ArenaRegistryService(new InMemoryArenaRegistryRepository(), new NoOpArenaResetPort());
        private final KitRegistryService kitRegistryService =
                new KitRegistryService(new InMemoryKitRegistryRepository());
        private final FakePlayerStatePort playerStatePort = new FakePlayerStatePort();
        private final PlayerSessionService playerSessionService = new PlayerSessionService(
                new InMemoryPlayerSessionRepository(),
                new InMemoryPendingRestorationRepository(),
                playerStatePort);
        private final FakeMatchPlayerPort matchPlayerPort = new FakeMatchPlayerPort();
        private final MatchLifecycleService matchLifecycleService;
        private final DuelRequestService duelRequestService;
        private final RematchService rematchService;

        private Harness(AdjustableClock clock) {
            this.clock = clock;
            this.matchLifecycleService = new MatchLifecycleService(
                    matchRepository,
                    playerSessionService,
                    arenaRegistryService,
                    kitRegistryService,
                    matchPlayerPort,
                    new MatchRuleset(3, 20, true),
                    event -> {
                    });
            this.duelRequestService = new DuelRequestService(
                    requestRepository,
                    matchRepository,
                    arenaRegistryService,
                    kitRegistryService,
                    matchPlayerPort,
                    matchLifecycleService,
                    new PlayerAvailabilityService(matchRepository, requestRepository, queueTicketRepository),
                    clock,
                    Duration.ofSeconds(30),
                    event -> {
                    });
            this.rematchService = new RematchService(
                    settlementRepository, duelRequestService, clock, Duration.ofSeconds(30));

            arenaRegistryService.register(arenaDefinition(arenaId(), "Arena One"));
            arenaRegistryService.register(arenaDefinition(new ArenaId("arena-old"), "Arena Old"));
            arenaRegistryService.register(arenaDefinition(new ArenaId("arena-new"), "Arena New"));
            kitRegistryService.register(kitDefinition(kitId(), "Kit One"));
            kitRegistryService.register(kitDefinition(new KitId("kit-old"), "Kit Old"));
            kitRegistryService.register(kitDefinition(new KitId("kit-new"), "Kit New"));
            matchPlayerPort.onlinePlayers.addAll(Set.of(requester(), target()));
            playerStatePort.onlinePlayers.addAll(Set.of(requester(), target()));
            playerSessionService.join(requester());
            playerSessionService.join(target());
        }

        private PlayerId requester() {
            return player("requester");
        }

        private PlayerId target() {
            return player("target");
        }

        private ArenaId arenaId() {
            return new ArenaId("arena-one");
        }

        private KitId kitId() {
            return new KitId("kit-one");
        }

        private void startAcceptedDuel() {
            duelRequestService.request(requester(), target(), arenaId(), kitId());
            duelRequestService.accept(requester(), target());
        }

        private void recordCompletedMatch(
                String seed,
                MatchOrigin origin,
                PlayerId firstPlayer,
                PlayerId secondPlayer,
                ArenaId arenaId,
                KitId kitId,
                Instant completedAt) {
            Match match = new Match(
                    new io.github.xreatlabz.revprac.domain.matches.MatchId(UUID.nameUUIDFromBytes(seed.getBytes())),
                    new MatchParticipants(firstPlayer, secondPlayer),
                    arenaId,
                    kitId,
                    origin,
                    new ArenaReservationId(UUID.nameUUIDFromBytes((seed + "-reservation").getBytes())),
                    new MatchRuleset(1, 200, true),
                    MatchState.COMPLETED,
                    0,
                    10,
                    Set.of(),
                    Optional.of(MatchOutcome.win(firstPlayer, secondPlayer)),
                    Optional.of(completedAt));
            new MatchSettlementService(settlementRepository).settle(match);
            assertTrue(settlementRepository.findHistory(match.id()).isPresent());
        }

        private ArenaDefinition arenaDefinition(ArenaId arenaId, String displayName) {
            return new ArenaDefinition(
                    arenaId,
                    displayName,
                    new ArenaCuboid("minecraft:world", 0, 60, 0, 20, 90, 20),
                    new ArenaSpawnPoint("minecraft:world", 2.0d, 70.0d, 2.0d, 0.0f, 0.0f),
                    new ArenaSpawnPoint("minecraft:world", 18.0d, 70.0d, 18.0d, 180.0f, 0.0f),
                    true);
        }

        private KitDefinition kitDefinition(KitId kitId, String displayName) {
            return new KitDefinition(
                    kitId,
                    displayName,
                    new KitInventory(List.of("sword"), List.of("helmet", "chest", "legs", "boots"), List.of("rod"), 0),
                    List.of(),
                    new KitRules(false, false, false, false),
                    true);
        }
    }

    private static MatchHistoryEntry historyEntry(
            String seed,
            MatchOrigin origin,
            PlayerId firstPlayer,
            PlayerId secondPlayer,
            ArenaId arenaId,
            KitId kitId,
            Instant completedAt) {
        return new MatchHistoryEntry(
                new io.github.xreatlabz.revprac.domain.matches.MatchId(UUID.nameUUIDFromBytes(seed.getBytes())),
                firstPlayer,
                secondPlayer,
                arenaId,
                kitId,
                origin,
                MatchEndReason.WIN,
                Optional.of(firstPlayer),
                Optional.of(secondPlayer),
                10,
                completedAt);
    }

    private static final class OutOfOrderHistoryRepository implements MatchSettlementRepository {
        private final List<MatchHistoryEntry> history;

        private OutOfOrderHistoryRepository(List<MatchHistoryEntry> history) {
            this.history = List.copyOf(history);
        }

        @Override
        public boolean record(MatchSettlement settlement) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public Optional<MatchHistoryEntry> findHistory(io.github.xreatlabz.revprac.domain.matches.MatchId matchId) {
            return history.stream().filter(entry -> entry.matchId().equals(matchId)).findFirst();
        }

        @Override
        public Optional<PlayerKitStats> findStats(PlayerId playerId, KitId kitId) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public List<PlayerKitStats> findStatsByPlayer(PlayerId playerId) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public List<MatchHistoryEntry> findRecentHistory(PlayerId playerId, int limit, int offset) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public List<MatchHistoryEntry> findAllHistory(PlayerId playerId) {
            return history.stream()
                    .filter(entry -> entry.playerOneId().equals(playerId) || entry.playerTwoId().equals(playerId))
                    .toList();
        }

        @Override
        public void validateImportHistoryCompatibility(PlayerId playerId, List<MatchHistoryEntry> history) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public void importPlayerRecords(PlayerId playerId, List<PlayerKitStats> stats, List<MatchHistoryEntry> history) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public void restoreImportedPlayerRecords(
                PlayerId playerId,
                List<PlayerKitStats> stats,
                List<MatchHistoryEntry> history) {
            throw new UnsupportedOperationException("not needed");
        }
    }

    private static final class AdjustableClock extends Clock {
        private final Instant instant;

        private AdjustableClock(Instant instant) {
            this.instant = instant;
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

    private static final class FakePlayerStatePort implements PlayerStatePort {
        private final Set<PlayerId> onlinePlayers = new java.util.HashSet<>();

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return new PlayerSafetySnapshot(
                    new LocationSnapshot("minecraft:world", 10.0d, 70.0d, 10.0d, 0.0f, 0.0f),
                    new InventorySnapshot(List.of(playerId.value().toString()), List.of(), List.of(), List.of(), null, 0),
                    new PlayerStatusSnapshot("SURVIVAL", 20.0d, 20, 5.0f, 0.0f, 0, false, false, List.of()));
        }

        @Override
        public void restore(PlayerId playerId, PlayerSafetySnapshot snapshot) {
        }

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }
    }

    private static final class NoOpArenaResetPort implements ArenaResetPort {
        @Override
        public void reset(ArenaDefinition arenaDefinition) {
        }
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }
}
