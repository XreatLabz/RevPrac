package io.github.xreatlabz.revprac.domain.tournaments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class TournamentTest {

    private static final Path DOMAIN_TOURNAMENTS_DIR =
            Path.of("src/main/java/io/github/xreatlabz/revprac/domain/tournaments");

    @Test
    void lifecycleMovesFromDraftToCompletedAndCapturesWinner() {
        PlayerId first = player("first");
        PlayerId second = player("second");
        Tournament tournament = Tournament.create(tournamentId("spring"), " Spring Open ", 4);

        tournament = tournament.open(Instant.parse("2026-05-17T12:00:00Z"));
        tournament = tournament.register(first);
        tournament = tournament.register(second);
        tournament = tournament.start(Instant.parse("2026-05-17T12:05:00Z"));
        tournament = tournament.complete(first, Instant.parse("2026-05-17T12:30:00Z"));

        assertEquals("Spring Open", tournament.name());
        assertEquals(TournamentState.COMPLETED, tournament.state());
        assertEquals(List.of(first, second), tournament.entrants());
        assertEquals(Optional.of(first), tournament.winnerId());
    }

    @Test
    void tournamentRejectsInvalidLifecycleTransitionsAndEntrantState() {
        Tournament tournament = Tournament.create(tournamentId("summer"), "Summer Open", 2);
        PlayerId first = player("first");
        PlayerId second = player("second");
        PlayerId outsider = player("outsider");

        IllegalStateException registerBeforeOpen =
                assertThrows(IllegalStateException.class, () -> tournament.register(first));
        assertEquals("tournament must be open before this operation", registerBeforeOpen.getMessage());

        Tournament openTournament = tournament.open(Instant.parse("2026-05-17T13:00:00Z"));
        Tournament fullTournamentState = openTournament.register(first).register(second);

        IllegalStateException duplicateRegistration =
                assertThrows(IllegalStateException.class, () -> fullTournamentState.register(first));
        assertEquals("player is already registered", duplicateRegistration.getMessage());

        IllegalStateException fullTournamentFailure =
                assertThrows(IllegalStateException.class, () -> fullTournamentState.register(outsider));
        assertEquals("tournament is full", fullTournamentFailure.getMessage());

        Tournament started = fullTournamentState.start(Instant.parse("2026-05-17T13:05:00Z"));
        IllegalStateException outsiderWinner = assertThrows(
                IllegalStateException.class,
                () -> started.complete(outsider, Instant.parse("2026-05-17T13:30:00Z")));
        assertEquals("winner must be a registered entrant", outsiderWinner.getMessage());
    }

    @Test
    void tournamentDomainSourcesStayFreeOfBukkitAndPaperImports() throws IOException {
        assertTrue(
                Files.isDirectory(DOMAIN_TOURNAMENTS_DIR),
                "Expected tournament domain directory to exist: " + DOMAIN_TOURNAMENTS_DIR);

        try (Stream<Path> sources = Files.walk(DOMAIN_TOURNAMENTS_DIR)) {
            List<Path> javaSources = sources
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .toList();

            assertFalse(javaSources.isEmpty(), "Expected tournament domain source files to exist");

            for (Path source : javaSources) {
                String contents = Files.readString(source);
                assertFalse(contents.contains("org.bukkit"), "Domain source must not import Bukkit: " + source);
                assertFalse(contents.contains("io.papermc.paper"), "Domain source must not import Paper: " + source);
            }
        }
    }

    private static TournamentId tournamentId(String seed) {
        return new TournamentId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }
}
