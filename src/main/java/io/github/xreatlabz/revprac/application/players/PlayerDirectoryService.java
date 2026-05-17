package io.github.xreatlabz.revprac.application.players;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlayerDirectoryService {

    private final PlayerProfileRepository playerProfileRepository;

    public PlayerDirectoryService(PlayerProfileRepository playerProfileRepository) {
        this.playerProfileRepository = Objects.requireNonNull(playerProfileRepository, "playerProfileRepository");
    }

    public PlayerDirectoryEntry resolve(String selector) {
        String normalizedSelector = requireNonBlank(selector, "selector");
        Optional<PlayerId> uuidSelector = parseUuid(normalizedSelector);
        if (uuidSelector.isPresent()) {
            return playerProfileRepository.find(uuidSelector.orElseThrow())
                    .map(PlayerDirectoryService::toEntry)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown player: " + normalizedSelector + "."));
        }

        List<PlayerProfile> matches = playerProfileRepository.findByLastKnownNameIgnoreCase(normalizedSelector);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Unknown player: " + normalizedSelector + ".");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Player name is ambiguous; use UUID: " + normalizedSelector + ".");
        }
        return toEntry(matches.getFirst());
    }

    private static PlayerDirectoryEntry toEntry(PlayerProfile profile) {
        return new PlayerDirectoryEntry(
                profile.playerId(),
                profile.lastKnownName().orElse(profile.playerId().value().toString()));
    }

    private static Optional<PlayerId> parseUuid(String selector) {
        try {
            return Optional.of(new PlayerId(UUID.fromString(selector)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
