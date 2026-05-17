package io.github.xreatlabz.revprac.application.players;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PlayerDirectoryServiceTest {

    @Test
    void resolvesByUuidBeforeNameLookup() {
        MapPlayerProfileRepository repository = new MapPlayerProfileRepository();
        PlayerProfile profile = profile("uuid-target", "Alpha");
        repository.upsert(profile);
        PlayerDirectoryService service = new PlayerDirectoryService(repository);

        PlayerDirectoryEntry entry = service.resolve(profile.playerId().value().toString());

        assertEquals(profile.playerId(), entry.playerId());
        assertEquals("Alpha", entry.displayName());
        assertEquals("Alpha (" + profile.playerId().value() + ")", entry.displayLabel());
    }

    @Test
    void resolvesExactCaseInsensitiveLastKnownName() {
        MapPlayerProfileRepository repository = new MapPlayerProfileRepository();
        PlayerProfile profile = profile("name-target", "SharpShooter");
        repository.upsert(profile);
        PlayerDirectoryService service = new PlayerDirectoryService(repository);

        PlayerDirectoryEntry entry = service.resolve("sharpshooter");

        assertEquals(profile.playerId(), entry.playerId());
        assertEquals("SharpShooter", entry.displayName());
    }

    @Test
    void rejectsAmbiguousExactNameMatches() {
        MapPlayerProfileRepository repository = new MapPlayerProfileRepository();
        repository.upsert(profile("ambiguous-one", "DupName"));
        repository.upsert(profile("ambiguous-two", "dupname"));
        PlayerDirectoryService service = new PlayerDirectoryService(repository);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve("DupName"));

        assertEquals("Player name is ambiguous; use UUID: DupName.", failure.getMessage());
    }

    @Test
    void rejectsUnknownSelectors() {
        PlayerDirectoryService service = new PlayerDirectoryService(new MapPlayerProfileRepository());

        IllegalArgumentException nameFailure = assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve("MissingPlayer"));
        IllegalArgumentException uuidFailure = assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(playerId("missing-uuid").value().toString()));

        assertEquals("Unknown player: MissingPlayer.", nameFailure.getMessage());
        assertEquals(
                "Unknown player: " + playerId("missing-uuid").value() + ".",
                uuidFailure.getMessage());
    }

    private static PlayerProfile profile(String seed, String name) {
        return new PlayerProfile(
                playerId(seed),
                Optional.of(name),
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-04T00:00:00Z"));
    }

    private static PlayerId playerId(String seed) {
        return new PlayerId(UUID.nameUUIDFromBytes(seed.getBytes()));
    }

    private static final class MapPlayerProfileRepository implements PlayerProfileRepository {
        private final Map<PlayerId, PlayerProfile> profiles = new HashMap<>();

        @Override
        public Optional<PlayerProfile> find(PlayerId playerId) {
            return Optional.ofNullable(profiles.get(playerId));
        }

        @Override
        public List<PlayerProfile> findByLastKnownNameIgnoreCase(String lastKnownName) {
            return profiles.values().stream()
                    .filter(profile -> profile.lastKnownName()
                            .map(name -> name.equalsIgnoreCase(lastKnownName))
                            .orElse(false))
                    .toList();
        }

        @Override
        public void upsert(PlayerProfile profile) {
            profiles.put(profile.playerId(), profile);
        }

        @Override
        public void restoreExact(PlayerProfile profile) {
            profiles.put(profile.playerId(), profile);
        }

        @Override
        public void delete(PlayerId playerId) {
            profiles.remove(playerId);
        }
    }
}
