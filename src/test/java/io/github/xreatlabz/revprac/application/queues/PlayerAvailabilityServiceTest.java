package io.github.xreatlabz.revprac.application.queues;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryDuelRequestRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryQueueTicketRepository;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.DuelRequest;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestId;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestState;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOutcome;
import io.github.xreatlabz.revprac.domain.matches.MatchParticipants;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.queues.QueueKey;
import io.github.xreatlabz.revprac.domain.queues.QueueMode;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketId;
import io.github.xreatlabz.revprac.domain.queues.QueueTicketState;
import io.github.xreatlabz.revprac.ports.matches.DuelRequestRepository;
import io.github.xreatlabz.revprac.ports.matches.MatchRepository;
import io.github.xreatlabz.revprac.ports.queues.QueueTicketRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class PlayerAvailabilityServiceTest {

    private static final Path PORTS_QUEUES_DIR = Path.of("src/main/java/io/github/xreatlabz/revprac/ports/queues");
    private static final Path APPLICATION_QUEUES_DIR =
            Path.of("src/main/java/io/github/xreatlabz/revprac/application/queues");
    private static final List<Path> QUEUE_STORAGE_ADAPTERS = List.of(
            Path.of("src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryQueueTicketRepository.java"),
            Path.of("src/main/java/io/github/xreatlabz/revprac/adapters/storage/InMemoryQueueRatingRepository.java"));

    @Test
    void availablePlayersAreAllowedForQueueAndDuel() {
        Harness harness = new Harness();
        PlayerId playerId = player("available");

        assertFalse(harness.availabilityService.isQueued(playerId));
        assertDoesNotThrow(() -> harness.availabilityService.requireAvailableForQueue(playerId));
        assertDoesNotThrow(() -> harness.availabilityService.requireAvailableForDuel(playerId, "requester"));
    }

    @Test
    void activeMatchParticipantsAndSpectatorsAreBusyButCompletedMatchesAreIgnored() {
        Harness participantHarness = new Harness();
        Match activeMatch = match("active", ruleset(true));
        assertTrue(participantHarness.matchRepository.create(activeMatch));

        IllegalStateException participantBusy = assertThrows(
                IllegalStateException.class,
                () -> participantHarness.availabilityService.requireAvailableForQueue(activeMatch.participants().playerOne()));
        assertEquals("player is already busy", participantBusy.getMessage());

        Harness spectatorHarness = new Harness();
        Match activeSpectatorMatch = match("spectator", ruleset(true))
                .tickCountdown()
                .tickCountdown()
                .addSpectator(player("spectator-player"));
        assertTrue(spectatorHarness.matchRepository.create(activeSpectatorMatch));

        IllegalStateException spectatorBusy = assertThrows(
                IllegalStateException.class,
                () -> spectatorHarness.availabilityService.requireAvailableForDuel(player("spectator-player"), "target"));
        assertEquals("target is already busy", spectatorBusy.getMessage());

        Harness completedHarness = new Harness();
        Match completed = match("completed", ruleset(true))
                .tickCountdown()
                .tickCountdown()
                .complete(MatchOutcome.shutdown());
        completedHarness.matchRepository.save(completed);

        assertDoesNotThrow(() -> completedHarness.availabilityService.requireAvailableForQueue(
                completed.participants().playerOne()));
    }

    @Test
    void pendingDuelRequestsMakeRequesterAndTargetBusy() {
        Harness harness = new Harness();
        DuelRequest pending = request("pending", "requester", "target", DuelRequestState.PENDING);
        assertTrue(harness.duelRequestRepository.create(pending));

        IllegalStateException requesterBusy = assertThrows(
                IllegalStateException.class,
                () -> harness.availabilityService.requireAvailableForDuel(pending.requesterId(), "requester"));
        assertEquals("requester is already busy", requesterBusy.getMessage());

        IllegalStateException targetBusy = assertThrows(
                IllegalStateException.class,
                () -> harness.availabilityService.requireAvailableForDuel(pending.targetId(), "target"));
        assertEquals("target is already busy", targetBusy.getMessage());

        Harness acceptedHarness = new Harness();
        DuelRequest accepted = request("accepted", "accepted-requester", "accepted-target", DuelRequestState.PENDING)
                .accept();
        assertTrue(acceptedHarness.duelRequestRepository.create(accepted));

        assertDoesNotThrow(() -> acceptedHarness.availabilityService.requireAvailableForQueue(accepted.requesterId()));
        assertDoesNotThrow(() -> acceptedHarness.availabilityService.requireAvailableForDuel(accepted.targetId(), "target"));
    }

    @Test
    void activeQueueTicketsMakePlayersBusyAndTerminalTicketsDoNot() {
        Harness searchingHarness = new Harness();
        QueueTicket searching = ticket("searching", "queued-player", QueueTicketState.SEARCHING);
        assertTrue(searchingHarness.queueTicketRepository.create(searching));

        assertTrue(searchingHarness.availabilityService.isQueued(searching.playerId()));
        IllegalStateException searchingBusy = assertThrows(
                IllegalStateException.class,
                () -> searchingHarness.availabilityService.requireAvailableForQueue(searching.playerId()));
        assertEquals("player is already busy", searchingBusy.getMessage());

        Harness pairingHarness = new Harness();
        QueueTicket pairing = ticket("pairing", "pairing-player", QueueTicketState.SEARCHING).markPairing();
        pairingHarness.queueTicketRepository.save(pairing);

        assertTrue(pairingHarness.availabilityService.isQueued(pairing.playerId()));
        IllegalStateException pairingBusy = assertThrows(
                IllegalStateException.class,
                () -> pairingHarness.availabilityService.requireAvailableForDuel(pairing.playerId(), "requester"));
        assertEquals("requester is already busy", pairingBusy.getMessage());

        Harness terminalHarness = new Harness();
        QueueTicket terminal = ticket("matched", "finished-player", QueueTicketState.SEARCHING)
                .markPairing()
                .markMatched();
        terminalHarness.queueTicketRepository.save(terminal);

        assertFalse(terminalHarness.availabilityService.isQueued(terminal.playerId()));
        assertDoesNotThrow(() -> terminalHarness.availabilityService.requireAvailableForQueue(terminal.playerId()));
    }

    @Test
    void queuePortsApplicationAndStorageAdaptersStayFreeOfBukkitPaperImports() throws IOException {
        assertNoForbiddenImports(PORTS_QUEUES_DIR);
        assertNoForbiddenImports(APPLICATION_QUEUES_DIR);
        for (Path source : QUEUE_STORAGE_ADAPTERS) {
            assertTrue(Files.isRegularFile(source), "Expected queue storage adapter to exist: " + source);
            assertNoForbiddenImports(source);
        }
    }

    private static void assertNoForbiddenImports(Path sourceOrDirectory) throws IOException {
        if (Files.isDirectory(sourceOrDirectory)) {
            try (Stream<Path> sources = Files.walk(sourceOrDirectory)) {
                for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                    assertNoForbiddenImports(source);
                }
            }
            return;
        }

        String contents = Files.readString(sourceOrDirectory);
        assertFalse(contents.contains("org.bukkit"), "Source must not import Bukkit: " + sourceOrDirectory);
        assertFalse(contents.contains("io.papermc.paper"), "Source must not import Paper: " + sourceOrDirectory);
    }

    private static QueueTicket ticket(String seed, String playerSeed, QueueTicketState state) {
        return new QueueTicket(
                new QueueTicketId(UUID.nameUUIDFromBytes(seed.getBytes())),
                player(playerSeed),
                new QueueKey(QueueMode.RANKED, new KitId("nodebuff")),
                10L,
                1000,
                state);
    }

    private static DuelRequest request(
            String seed, String requesterSeed, String targetSeed, DuelRequestState state) {
        Instant createdAt = Instant.parse("2026-05-01T12:00:00Z");
        return new DuelRequest(
                new DuelRequestId(UUID.nameUUIDFromBytes(seed.getBytes())),
                player(requesterSeed),
                player(targetSeed),
                new ArenaId("arena-" + seed),
                new KitId("kit-" + seed),
                state,
                createdAt,
                createdAt.plusSeconds(30));
    }

    private static Match match(String seed, MatchRuleset ruleset) {
        return Match.create(
                new MatchId(UUID.nameUUIDFromBytes(seed.getBytes())),
                new MatchParticipants(player(seed + "-one"), player(seed + "-two")),
                new ArenaId("arena-" + seed),
                new KitId("kit-" + seed),
                new ArenaReservationId(UUID.nameUUIDFromBytes((seed + "-reservation").getBytes())),
                ruleset);
    }

    private static MatchRuleset ruleset(boolean spectatorsEnabled) {
        return new MatchRuleset(2, 5, spectatorsEnabled);
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }

    private static final class Harness {
        private final DuelRequestRepository duelRequestRepository = new InMemoryDuelRequestRepository();
        private final MatchRepository matchRepository = new InMemoryMatchRepository();
        private final QueueTicketRepository queueTicketRepository = new InMemoryQueueTicketRepository();
        private final PlayerAvailabilityService availabilityService =
                new PlayerAvailabilityService(matchRepository, duelRequestRepository, queueTicketRepository);
    }
}
