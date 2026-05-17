package io.github.xreatlabz.revprac.application.parties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryPartyRepository;
import io.github.xreatlabz.revprac.domain.parties.PartyId;
import io.github.xreatlabz.revprac.domain.parties.PartyLeaveOutcome;
import io.github.xreatlabz.revprac.domain.parties.PartyQueueEligibilitySnapshot;
import io.github.xreatlabz.revprac.domain.parties.PartyStatus;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class PartyServiceTest {

    private static final Path APPLICATION_PARTIES_DIR =
            Path.of("src/main/java/io/github/xreatlabz/revprac/application/parties");
    private static final Path PORTS_PARTIES_DIR =
            Path.of("src/main/java/io/github/xreatlabz/revprac/ports/parties");

    @Test
    void createJoinStatusEligibilityAndLeaveStayWithinTheServiceBoundary() {
        PartyService partyService = new PartyService(new InMemoryPartyRepository());
        PlayerId leader = player("leader");
        PlayerId second = player("second");

        PartyStatus created = partyService.createParty(leader);
        PartyId partyId = created.id();
        PartyStatus joined = partyService.joinParty(partyId, second);
        PartyQueueEligibilitySnapshot eligibility = partyService.queueEligibility(partyId, 2);
        PartyLeaveOutcome leaderLeave = partyService.leaveParty(leader);

        assertEquals(List.of(leader), created.members());
        assertEquals(List.of(leader, second), joined.members());
        assertTrue(eligibility.eligible());
        assertEquals(Optional.of(second), leaderLeave.promotedLeaderId());
        assertEquals(List.of(second), leaderLeave.statusAfterLeave().orElseThrow().members());
        assertEquals(Optional.of(second), partyService.statusByMember(second).map(PartyStatus::leaderId));
    }

    @Test
    void duplicatePartyMembershipAndUnknownStatusAreRejected() {
        PartyService partyService = new PartyService(new InMemoryPartyRepository());
        PlayerId leader = player("leader");
        PlayerId second = player("second");
        PlayerId outsider = player("outsider");

        PartyStatus created = partyService.createParty(leader);
        partyService.createParty(second);

        IllegalStateException duplicateJoin = assertThrows(
                IllegalStateException.class,
                () -> partyService.joinParty(created.id(), second));
        assertEquals("player is already in a party", duplicateJoin.getMessage());

        IllegalStateException missingStatus = assertThrows(
                IllegalStateException.class,
                () -> partyService.leaveParty(outsider));
        assertEquals("player is not in a party", missingStatus.getMessage());
    }

    @Test
    void leavingTheLastMemberDisbandsTheParty() {
        PartyService partyService = new PartyService(new InMemoryPartyRepository());
        PlayerId leader = player("solo-leader");
        PartyStatus created = partyService.createParty(leader);

        PartyLeaveOutcome leaveOutcome = partyService.leaveParty(leader);

        assertTrue(leaveOutcome.disbanded());
        assertTrue(partyService.statusByMember(leader).isEmpty());
        IllegalStateException missingParty = assertThrows(
                IllegalStateException.class,
                () -> partyService.status(created.id()));
        assertEquals("party not found", missingParty.getMessage());
    }

    @Test
    void partyApplicationAndPortSourcesStayFreeOfBukkitAndPaperImports() throws IOException {
        assertNoPaperImports(APPLICATION_PARTIES_DIR);
        assertNoPaperImports(PORTS_PARTIES_DIR);
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
