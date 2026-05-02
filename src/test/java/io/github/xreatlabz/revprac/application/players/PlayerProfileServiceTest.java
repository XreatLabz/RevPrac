package io.github.xreatlabz.revprac.application.players;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

final class PlayerProfileServiceTest {

    @Test
    void touchCreatesANewProfileWithFirstAndLastSeenSetToTheTouchInstant() {
        FakePlayerProfileRepository repository = new FakePlayerProfileRepository();
        PlayerProfileService service = new PlayerProfileService(repository);
        PlayerId playerId = player("new-profile");
        Instant seenAt = Instant.parse("2026-05-02T11:00:00Z");

        PlayerProfile profile = service.touch(playerId, " Xreat ", seenAt);

        assertEquals(playerId, profile.playerId());
        assertEquals(Optional.of("Xreat"), profile.lastKnownName());
        assertEquals(seenAt, profile.firstSeenAt());
        assertEquals(seenAt, profile.lastSeenAt());
        assertEquals(profile, repository.find(playerId).orElseThrow());
    }

    @Test
    void touchUpdatesTheNameAndLastSeenWithoutChangingTheOriginalFirstSeenInstant() {
        FakePlayerProfileRepository repository = new FakePlayerProfileRepository();
        PlayerProfileService service = new PlayerProfileService(repository);
        PlayerId playerId = player("returning-profile");
        Instant firstSeenAt = Instant.parse("2026-05-02T09:00:00Z");
        Instant previousLastSeenAt = Instant.parse("2026-05-02T10:00:00Z");
        Instant newLastSeenAt = Instant.parse("2026-05-02T11:00:00Z");
        repository.upsert(new PlayerProfile(playerId, Optional.of("OldName"), firstSeenAt, previousLastSeenAt));

        PlayerProfile profile = service.touch(playerId, "NewName", newLastSeenAt);

        assertEquals(Optional.of("NewName"), profile.lastKnownName());
        assertEquals(firstSeenAt, profile.firstSeenAt());
        assertEquals(newLastSeenAt, profile.lastSeenAt());
        assertEquals(profile, repository.find(playerId).orElseThrow());
    }

    @Test
    void touchKeepsLastSeenStableWhenTheClockMovesBackward() {
        FakePlayerProfileRepository repository = new FakePlayerProfileRepository();
        PlayerProfileService service = new PlayerProfileService(repository);
        PlayerId playerId = player("clock-rollback-profile");
        Instant firstSeenAt = Instant.parse("2026-05-02T10:00:00Z");
        Instant previousLastSeenAt = Instant.parse("2026-05-02T11:00:00Z");
        Instant rolledBackSeenAt = Instant.parse("2026-05-02T09:30:00Z");
        repository.upsert(new PlayerProfile(playerId, Optional.of("OldName"), firstSeenAt, previousLastSeenAt));

        PlayerProfile profile = service.touch(playerId, "NewName", rolledBackSeenAt);

        assertEquals(Optional.of("NewName"), profile.lastKnownName());
        assertEquals(firstSeenAt, profile.firstSeenAt());
        assertEquals(previousLastSeenAt, profile.lastSeenAt());
        assertEquals(profile, repository.find(playerId).orElseThrow());
    }

    @Test
    void touchDoesNotRegressLastSeenWhenRolledBackTimeStillFollowsFirstSeen() {
        FakePlayerProfileRepository repository = new FakePlayerProfileRepository();
        PlayerProfileService service = new PlayerProfileService(repository);
        PlayerId playerId = player("clock-rollback-after-first-seen-profile");
        Instant firstSeenAt = Instant.parse("2026-05-02T10:00:00Z");
        Instant previousLastSeenAt = Instant.parse("2026-05-02T11:00:00Z");
        Instant rolledBackSeenAt = Instant.parse("2026-05-02T10:30:00Z");
        repository.upsert(new PlayerProfile(playerId, Optional.of("OldName"), firstSeenAt, previousLastSeenAt));

        PlayerProfile profile = service.touch(playerId, "NewName", rolledBackSeenAt);

        assertEquals(Optional.of("NewName"), profile.lastKnownName());
        assertEquals(firstSeenAt, profile.firstSeenAt());
        assertEquals(previousLastSeenAt, profile.lastSeenAt());
        assertEquals(profile, repository.find(playerId).orElseThrow());
    }

    private static PlayerId player(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }

    private static final class FakePlayerProfileRepository implements PlayerProfileRepository {
        private final Map<PlayerId, PlayerProfile> profiles = new ConcurrentHashMap<>();

        @Override
        public Optional<PlayerProfile> find(PlayerId playerId) {
            return Optional.ofNullable(profiles.get(playerId));
        }

        @Override
        public void upsert(PlayerProfile profile) {
            profiles.put(profile.playerId(), profile);
        }
    }
}
