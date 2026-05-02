package io.github.xreatlabz.revprac.domain.ratings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PlayerRatingTest {

    @Test
    void playerRatingRequiresIdentityPositiveRatingNonNegativeRecordAndTimestamp() {
        PlayerId playerId = player("rating-contract");
        KitId kitId = new KitId("nodebuff");
        Instant updatedAt = Instant.parse("2026-05-02T10:30:00Z");

        PlayerRating rating = new PlayerRating(playerId, kitId, 1240, 12, 3, updatedAt);

        assertEquals(playerId, rating.playerId());
        assertEquals(kitId, rating.kitId());
        assertEquals(1240, rating.rating());
        assertEquals(12, rating.wins());
        assertEquals(3, rating.losses());
        assertEquals(updatedAt, rating.updatedAt());
        assertThrows(NullPointerException.class, () -> new PlayerRating(null, kitId, 1240, 12, 3, updatedAt));
        assertThrows(NullPointerException.class, () -> new PlayerRating(playerId, null, 1240, 12, 3, updatedAt));
        assertThrows(NullPointerException.class, () -> new PlayerRating(playerId, kitId, 1240, 12, 3, null));
        assertThrows(IllegalArgumentException.class, () -> new PlayerRating(playerId, kitId, 0, 12, 3, updatedAt));
        assertThrows(IllegalArgumentException.class, () -> new PlayerRating(playerId, kitId, -1, 12, 3, updatedAt));
        assertThrows(IllegalArgumentException.class, () -> new PlayerRating(playerId, kitId, 1240, -1, 3, updatedAt));
        assertThrows(IllegalArgumentException.class, () -> new PlayerRating(playerId, kitId, 1240, 12, -1, updatedAt));
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }
}
