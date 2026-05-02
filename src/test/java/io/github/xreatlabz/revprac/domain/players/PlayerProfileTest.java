package io.github.xreatlabz.revprac.domain.players;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PlayerProfileTest {

    @Test
    void profileRequiresPlayerIdNameAndChronologicalSeenInstants() {
        PlayerId playerId = player("profile-contract");
        Instant firstSeenAt = Instant.parse("2026-05-02T10:00:00Z");
        Instant lastSeenAt = Instant.parse("2026-05-02T10:15:00Z");

        PlayerProfile profile = new PlayerProfile(playerId, Optional.of(" Xreat "), firstSeenAt, lastSeenAt);

        assertEquals(playerId, profile.playerId());
        assertEquals(Optional.of("Xreat"), profile.lastKnownName());
        assertEquals(firstSeenAt, profile.firstSeenAt());
        assertEquals(lastSeenAt, profile.lastSeenAt());
        assertThrows(NullPointerException.class, () -> new PlayerProfile(null, Optional.of("Xreat"), firstSeenAt, lastSeenAt));
        assertThrows(NullPointerException.class, () -> new PlayerProfile(playerId, null, firstSeenAt, lastSeenAt));
        assertThrows(NullPointerException.class, () -> new PlayerProfile(playerId, Optional.empty(), null, lastSeenAt));
        assertThrows(NullPointerException.class, () -> new PlayerProfile(playerId, Optional.empty(), firstSeenAt, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerProfile(playerId, Optional.of("   "), firstSeenAt, lastSeenAt));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerProfile(playerId, Optional.of("Xreat"), lastSeenAt, firstSeenAt));
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }
}
