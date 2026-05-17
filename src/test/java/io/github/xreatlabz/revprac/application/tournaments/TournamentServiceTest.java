package io.github.xreatlabz.revprac.application.tournaments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryTournamentRepository;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.tournaments.Tournament;
import io.github.xreatlabz.revprac.domain.tournaments.TournamentId;
import io.github.xreatlabz.revprac.domain.tournaments.TournamentState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class TournamentServiceTest {

    private static final Path APPLICATION_TOURNAMENTS_DIR =
            Path.of("src/main/java/io/github/xreatlabz/revprac/application/tournaments");
    private static final Path PORTS_TOURNAMENTS_DIR =
            Path.of("src/main/java/io/github/xreatlabz/revprac/ports/tournaments");

    @Test
    void createOpenRegisterStartCompleteAndStatusStayWithinTheServiceBoundary() {
        TournamentService tournamentService = new TournamentService(new InMemoryTournamentRepository());
        PlayerId first = player("first");
        PlayerId second = player("second");

        Tournament created = tournamentService.createTournament("Weekend Cup", 8);
        Tournament opened = tournamentService.openTournament(created.id(), Instant.parse("2026-05-17T14:00:00Z"));
        Tournament registered = tournamentService.register(created.id(), first);
        registered = tournamentService.register(created.id(), second);
        Tournament started = tournamentService.startTournament(created.id(), Instant.parse("2026-05-17T14:10:00Z"));
        Tournament completed = tournamentService.completeTournament(
                created.id(),
                first,
                Instant.parse("2026-05-17T14:45:00Z"));

        assertEquals(TournamentState.DRAFT, created.state());
        assertEquals(TournamentState.OPEN, opened.state());
        assertEquals(2, registered.entrantCount());
        assertEquals(TournamentState.STARTED, started.state());
        assertEquals(Optional.of(first), completed.winnerId());
        assertEquals(completed, tournamentService.status(created.id()));
    }

    @Test
    void missingTournamentAndInvalidStartAreRejected() {
        TournamentService tournamentService = new TournamentService(new InMemoryTournamentRepository());
        TournamentId missingId = new TournamentId(UUID.nameUUIDFromBytes("missing".getBytes()));

        IllegalStateException missingTournament = assertThrows(
                IllegalStateException.class,
                () -> tournamentService.status(missingId));
        assertEquals("tournament not found", missingTournament.getMessage());

        Tournament created = tournamentService.createTournament("Solo Cup", 4);
        tournamentService.openTournament(created.id(), Instant.parse("2026-05-17T15:00:00Z"));
        tournamentService.register(created.id(), player("only-player"));

        IllegalStateException invalidStart = assertThrows(
                IllegalStateException.class,
                () -> tournamentService.startTournament(created.id(), Instant.parse("2026-05-17T15:05:00Z")));
        assertEquals("tournament requires at least two entrants to start", invalidStart.getMessage());
    }

    @Test
    void tournamentApplicationAndPortSourcesStayFreeOfBukkitAndPaperImports() throws IOException {
        assertNoPaperImports(APPLICATION_TOURNAMENTS_DIR);
        assertNoPaperImports(PORTS_TOURNAMENTS_DIR);
    }

    private static void assertNoPaperImports(Path directory) throws IOException {
        assertTrue(Files.isDirectory(directory), "Expected directory to exist: " + directory);
        try (Stream<Path> sources = Files.walk(directory)) {
            List<Path> javaSources = sources
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .toList();
            assertFalse(javaSources.isEmpty(), "Expected source files to exist in " + directory);
            for (Path source : javaSources) {
                String contents = Files.readString(source);
                assertFalse(contents.contains("org.bukkit"), "Source must not import Bukkit: " + source);
                assertFalse(contents.contains("io.papermc.paper"), "Source must not import Paper: " + source);
            }
        }
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }
}
