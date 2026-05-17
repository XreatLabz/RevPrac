package io.github.xreatlabz.revprac.application.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryQueueTicketRepository;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.matches.MatchParticipants;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.matches.MatchState;
import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PendingRestoration;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerSession;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.domain.players.PotionEffectSnapshot;
import io.github.xreatlabz.revprac.domain.players.TransitionReason;
import io.github.xreatlabz.revprac.domain.queues.QueueKey;
import io.github.xreatlabz.revprac.domain.queues.QueueMode;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketId;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketState;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import io.github.xreatlabz.revprac.ports.recovery.RuntimeRecoveryRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class RuntimeRecoveryServiceTest {

    @Test
    void bootstrapRestoresOnlineSessionsAndTurnsOfflineManagedSessionsIntoPendingRestorations() {
        Harness harness = new Harness();
        PlayerId onlinePlayer = player("recovery-online-session");
        PlayerId offlinePlayer = player("recovery-offline-session");
        PlayerSafetySnapshot onlineSnapshot = snapshot("online");
        PlayerSafetySnapshot offlineSnapshot = snapshot("offline");
        harness.statePort.onlinePlayers.add(onlinePlayer);
        harness.recovery.sessions.put(
                onlinePlayer,
                new PlayerSession(onlinePlayer, PlayerContext.MATCH, onlineSnapshot));
        harness.recovery.sessions.put(
                offlinePlayer,
                new PlayerSession(offlinePlayer, PlayerContext.QUEUE, offlineSnapshot));

        harness.service.recoverBootstrapState();

        assertEquals(
                new PlayerSession(onlinePlayer, PlayerContext.MATCH, onlineSnapshot),
                harness.playerSessions.find(onlinePlayer).orElseThrow());
        PendingRestoration restoration = harness.pendingRestorations.find(offlinePlayer).orElseThrow();
        assertEquals(offlineSnapshot, restoration.snapshot());
        assertEquals(TransitionReason.PLUGIN_DISABLE, restoration.reason());
        assertTrue(harness.playerSessions.find(offlinePlayer).isEmpty());
        assertTrue(harness.recovery.sessions.get(offlinePlayer) == null);
    }

    @Test
    void bootstrapOnlyRecoversOnlineQueueTicketsAndResetsPairingTicketsToSearching() {
        Harness harness = new Harness();
        PlayerId onlinePlayer = player("recovery-online-ticket");
        PlayerId offlinePlayer = player("recovery-offline-ticket");
        QueueTicket onlinePairing = ticket("pairing-ticket", onlinePlayer, QueueTicketState.PAIRING);
        QueueTicket offlineSearching = ticket("offline-ticket", offlinePlayer, QueueTicketState.SEARCHING);
        harness.statePort.onlinePlayers.add(onlinePlayer);
        harness.recovery.queueTickets.put(onlinePairing.id(), onlinePairing);
        harness.recovery.queueTickets.put(offlineSearching.id(), offlineSearching);

        harness.service.recoverBootstrapState();

        QueueTicket recovered = harness.queueTickets.findByPlayer(onlinePlayer).orElseThrow();
        assertEquals(onlinePairing.id(), recovered.id());
        assertEquals(QueueTicketState.SEARCHING, recovered.state());
        assertTrue(harness.queueTickets.findByPlayer(offlinePlayer).isEmpty());
    }

    @Test
    void recoverPlayerLazilyRestoresAStoredQueueTicketWhenThePlayerRejoins() {
        Harness harness = new Harness();
        PlayerId playerId = player("recovery-lazy-ticket");
        QueueTicket storedTicket = ticket("lazy-ticket", playerId, QueueTicketState.SEARCHING);
        harness.recovery.queueTickets.put(storedTicket.id(), storedTicket);

        harness.service.recoverBootstrapState();
        assertTrue(harness.queueTickets.findByPlayer(playerId).isEmpty());

        harness.statePort.onlinePlayers.add(playerId);
        harness.service.recoverPlayer(playerId);

        assertEquals(storedTicket, harness.queueTickets.findByPlayer(playerId).orElseThrow());
    }

    @Test
    void activeMatchesRecoverAsFreshCountdownOnlyWhenBothCombatantsAreOnlineAndDropSpectators() {
        Harness harness = new Harness();
        PlayerId first = player("recovery-match-first");
        PlayerId second = player("recovery-match-second");
        PlayerId spectator = player("recovery-match-spectator");
        PlayerId offlineOpponent = player("recovery-match-offline-opponent");
        Match activeMatch = activeMatch("recover-active-match", first, second, Set.of(spectator));
        Match unsafeMatch = activeMatch("skip-active-match", first, offlineOpponent, Set.of());
        harness.statePort.onlinePlayers.addAll(Set.of(first, second, spectator));
        harness.recovery.matches.put(activeMatch.id(), activeMatch);
        harness.recovery.matches.put(unsafeMatch.id(), unsafeMatch);

        harness.service.recoverBootstrapState();

        Match recovered = harness.matches.find(activeMatch.id()).orElseThrow();
        assertEquals(MatchState.COUNTDOWN, recovered.state());
        assertEquals(activeMatch.ruleset().countdownTicks(), recovered.countdownTicksRemaining());
        assertEquals(0, recovered.activeTicksElapsed());
        assertTrue(recovered.spectators().isEmpty());
        assertTrue(harness.matches.find(unsafeMatch.id()).isEmpty());
    }

    @Test
    void completedMatchesRecoverWithoutOnlineCombatants() {
        Harness harness = new Harness();
        PlayerId winner = player("recovery-completed-winner");
        PlayerId loser = player("recovery-completed-loser");
        Match completed = activeMatch("recover-completed-match", winner, loser, Set.of())
                .complete(MatchOutcome.win(winner, loser), Instant.parse("2026-05-07T12:00:00Z"));
        harness.recovery.matches.put(completed.id(), completed);

        harness.service.recoverBootstrapState();

        assertEquals(completed, harness.matches.find(completed.id()).orElseThrow());
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)));
    }

    private static QueueTicket ticket(String seed, PlayerId playerId, QueueTicketState state) {
        return new QueueTicket(
                new QueueTicketId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8))),
                playerId,
                new QueueKey(QueueMode.RANKED, new KitId("nodebuff")),
                123L,
                1100,
                state);
    }

    private static Match activeMatch(String seed, PlayerId first, PlayerId second, Set<PlayerId> spectators) {
        return new Match(
                new MatchId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8))),
                new MatchParticipants(first, second),
                new ArenaId("arena-recovery"),
                new KitId("nodebuff"),
                MatchOrigin.QUEUE_RANKED,
                new ArenaReservationId(UUID.nameUUIDFromBytes((seed + "-reservation").getBytes(StandardCharsets.UTF_8))),
                new MatchRuleset(5, 200, true),
                MatchState.ACTIVE,
                0,
                12,
                spectators,
                Optional.empty(),
                Optional.empty());
    }

    private static PlayerSafetySnapshot snapshot(String seed) {
        return new PlayerSafetySnapshot(
                new LocationSnapshot("minecraft:overworld", 10.0d, 64.0d, -5.0d, 90.0f, 0.0f),
                new InventorySnapshot(
                        List.of(seed + "-sword"),
                        List.of(seed + "-helmet"),
                        List.of(),
                        List.of(seed + "-pearl"),
                        null,
                        0),
                new PlayerStatusSnapshot(
                        "SURVIVAL",
                        20.0d,
                        20,
                        5.0f,
                        0.25f,
                        3,
                        false,
                        false,
                        List.of(new PotionEffectSnapshot("minecraft:speed", 120, 1, false, true, true))));
    }

    private static final class Harness {
        private final FakeRuntimeRecoveryRepository recovery = new FakeRuntimeRecoveryRepository();
        private final InMemoryPlayerSessionRepository playerSessions = new InMemoryPlayerSessionRepository();
        private final InMemoryPendingRestorationRepository pendingRestorations = new InMemoryPendingRestorationRepository();
        private final InMemoryQueueTicketRepository queueTickets = new InMemoryQueueTicketRepository();
        private final InMemoryMatchRepository matches = new InMemoryMatchRepository();
        private final FakePlayerStatePort statePort = new FakePlayerStatePort();
        private final RuntimeRecoveryService service = new RuntimeRecoveryService(
                recovery,
                playerSessions,
                pendingRestorations,
                queueTickets,
                matches,
                statePort);
    }

    private static final class FakeRuntimeRecoveryRepository implements RuntimeRecoveryRepository {
        private final Map<PlayerId, PlayerSession> sessions = new HashMap<>();
        private final Map<PlayerId, PendingRestoration> restorations = new HashMap<>();
        private final Map<QueueTicketId, QueueTicket> queueTickets = new HashMap<>();
        private final Map<MatchId, Match> matches = new HashMap<>();

        @Override
        public List<PlayerSession> playerSessions() {
            return List.copyOf(sessions.values());
        }

        @Override
        public void savePlayerSession(PlayerSession session) {
            sessions.put(session.playerId(), session);
        }

        @Override
        public void deletePlayerSession(PlayerId playerId) {
            sessions.remove(playerId);
        }

        @Override
        public List<PendingRestoration> pendingRestorations() {
            return List.copyOf(restorations.values());
        }

        @Override
        public void savePendingRestoration(PendingRestoration restoration) {
            restorations.put(restoration.playerId(), restoration);
        }

        @Override
        public void deletePendingRestoration(PlayerId playerId) {
            restorations.remove(playerId);
        }

        @Override
        public List<QueueTicket> queueTickets() {
            return List.copyOf(queueTickets.values());
        }

        @Override
        public Optional<QueueTicket> queueTicket(PlayerId playerId) {
            return queueTickets.values().stream()
                    .filter(ticket -> ticket.playerId().equals(playerId))
                    .findFirst();
        }

        @Override
        public void saveQueueTicket(QueueTicket ticket, Instant joinedAt) {
            queueTickets.put(ticket.id(), ticket);
        }

        @Override
        public void deleteQueueTicket(QueueTicketId ticketId) {
            queueTickets.remove(ticketId);
        }

        @Override
        public void deleteQueueTicketByPlayer(PlayerId playerId) {
            queueTickets.entrySet().removeIf(entry -> entry.getValue().playerId().equals(playerId));
        }

        @Override
        public List<Match> matches() {
            return List.copyOf(matches.values());
        }

        @Override
        public void saveMatch(Match match) {
            matches.put(match.id(), match);
        }

        @Override
        public void deleteMatch(MatchId matchId) {
            matches.remove(matchId);
        }
    }

    private static final class FakePlayerStatePort implements PlayerStatePort {
        private final Set<PlayerId> onlinePlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final List<PlayerId> restoreCalls = new ArrayList<>();

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return snapshot(playerId.value().toString());
        }

        @Override
        public void restore(PlayerId playerId, PlayerSafetySnapshot snapshot) {
            restoreCalls.add(playerId);
        }

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }
    }
}
