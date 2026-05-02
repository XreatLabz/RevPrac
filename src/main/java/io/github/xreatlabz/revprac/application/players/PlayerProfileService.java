package io.github.xreatlabz.revprac.application.players;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class PlayerProfileService {

    private final PlayerProfileRepository playerProfileRepository;

    public PlayerProfileService(PlayerProfileRepository playerProfileRepository) {
        this.playerProfileRepository = Objects.requireNonNull(playerProfileRepository, "playerProfileRepository");
    }

    public PlayerProfile touch(PlayerId playerId, String lastKnownName, Instant seenAt) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lastKnownName, "lastKnownName");
        Objects.requireNonNull(seenAt, "seenAt");
        Optional<String> normalizedName = Optional.of(lastKnownName);
        PlayerProfile profile = playerProfileRepository
                .find(playerId)
                .map(existing -> new PlayerProfile(
                        playerId,
                        normalizedName,
                        existing.firstSeenAt(),
                        seenAt))
                .orElseGet(() -> new PlayerProfile(playerId, normalizedName, seenAt, seenAt));
        playerProfileRepository.upsert(profile);
        return profile;
    }
}
