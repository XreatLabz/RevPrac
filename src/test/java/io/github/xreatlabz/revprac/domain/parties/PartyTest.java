package io.github.xreatlabz.revprac.domain.parties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class PartyTest {

    private static final Path DOMAIN_PARTIES_DIR =
            Path.of("src/main/java/io/github/xreatlabz/revprac/domain/parties");

    @Test
    void joinLeaveAndLeaderPromotionFollowTheInMemoryPartyContract() {
        PlayerId leader = player("leader");
        PlayerId second = player("second");
        PlayerId third = player("third");
        Party party = Party.create(partyId("alpha"), leader).join(second).join(third);

        assertEquals(List.of(leader, second, third), party.members());
        assertEquals(leader, party.leaderId());

        PartyLeaveOutcome memberLeave = party.leave(second);
        Party afterMemberLeave = memberLeave.updatedParty().orElseThrow();
        assertFalse(memberLeave.disbanded());
        assertTrue(memberLeave.promotedLeaderId().isEmpty());
        assertEquals(List.of(leader, third), afterMemberLeave.members());
        assertEquals(leader, afterMemberLeave.leaderId());

        PartyLeaveOutcome leaderLeave = afterMemberLeave.leave(leader);
        Party afterLeaderLeave = leaderLeave.updatedParty().orElseThrow();
        assertEquals(Optional.of(third), leaderLeave.promotedLeaderId());
        assertEquals(third, afterLeaderLeave.leaderId());
        assertEquals(List.of(third), afterLeaderLeave.members());

        PartyLeaveOutcome finalLeave = afterLeaderLeave.leave(third);
        assertTrue(finalLeave.disbanded());
        assertTrue(finalLeave.updatedParty().isEmpty());
    }

    @Test
    void queueEligibilityRequiresAnExactPartySize() {
        PlayerId leader = player("leader");
        PlayerId second = player("second");
        Party party = Party.create(partyId("beta"), leader).join(second);

        PartyQueueEligibilitySnapshot eligible = party.queueEligibility(2);
        PartyQueueEligibilitySnapshot ineligible = party.queueEligibility(3);

        assertTrue(eligible.eligible());
        assertTrue(eligible.reason().isEmpty());
        assertFalse(ineligible.eligible());
        assertEquals(Optional.of("party size 2 does not match required size 3"), ineligible.reason());
    }

    @Test
    void partyRejectsDuplicateMembersAndUnknownLeaves() {
        PlayerId leader = player("leader");
        Party party = Party.create(partyId("gamma"), leader);

        IllegalStateException duplicateJoin =
                assertThrows(IllegalStateException.class, () -> party.join(leader));
        assertEquals("player is already in the party", duplicateJoin.getMessage());

        IllegalStateException unknownLeave =
                assertThrows(IllegalStateException.class, () -> party.leave(player("outsider")));
        assertEquals("player is not in the party", unknownLeave.getMessage());
    }

    @Test
    void partyDomainSourcesStayFreeOfBukkitAndPaperImports() throws IOException {
        assertTrue(Files.isDirectory(DOMAIN_PARTIES_DIR), "Expected party domain directory to exist: " + DOMAIN_PARTIES_DIR);

        try (Stream<Path> sources = Files.walk(DOMAIN_PARTIES_DIR)) {
            List<Path> javaSources = sources
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .toList();

            assertFalse(javaSources.isEmpty(), "Expected party domain source files to exist");

            for (Path source : javaSources) {
                String contents = Files.readString(source);
                assertFalse(contents.contains("org.bukkit"), "Domain source must not import Bukkit: " + source);
                assertFalse(contents.contains("io.papermc.paper"), "Domain source must not import Paper: " + source);
            }
        }
    }

    private static PartyId partyId(String seed) {
        return new PartyId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }
}
