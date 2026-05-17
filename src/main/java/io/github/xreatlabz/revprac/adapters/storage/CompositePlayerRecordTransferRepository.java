package io.github.xreatlabz.revprac.adapters.storage;

import io.github.xreatlabz.revprac.application.players.PlayerRecordBundle;
import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerRecordTransferRepository;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CompositePlayerRecordTransferRepository implements PlayerRecordTransferRepository {

    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final MatchSettlementRepository matchSettlementRepository;

    public CompositePlayerRecordTransferRepository(
            PlayerProfileRepository playerProfileRepository,
            PlayerRatingRepository playerRatingRepository,
            MatchSettlementRepository matchSettlementRepository) {
        this.playerProfileRepository = Objects.requireNonNull(playerProfileRepository, "playerProfileRepository");
        this.playerRatingRepository = Objects.requireNonNull(playerRatingRepository, "playerRatingRepository");
        this.matchSettlementRepository = Objects.requireNonNull(matchSettlementRepository, "matchSettlementRepository");
    }

    @Override
    public PlayerRecordBundle exportBundle(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerProfile profile = playerProfileRepository.find(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown player: " + playerId.value() + "."));
        return new PlayerRecordBundle(
                profile,
                playerRatingRepository.findByPlayer(playerId),
                matchSettlementRepository.findStatsByPlayer(playerId),
                matchSettlementRepository.findAllHistory(playerId));
    }

    @Override
    public void importBundle(PlayerRecordBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        PlayerId playerId = bundle.profile().playerId();
        Optional<PlayerProfile> originalProfile = playerProfileRepository.find(playerId);
        List<PlayerRating> originalRatings = playerRatingRepository.findByPlayer(playerId);
        List<PlayerKitStats> originalStats = matchSettlementRepository.findStatsByPlayer(playerId);
        List<MatchHistoryEntry> originalHistory = matchSettlementRepository.findAllHistory(playerId);
        matchSettlementRepository.validateImportHistoryCompatibility(playerId, bundle.history());
        try {
            playerProfileRepository.upsert(bundle.profile());
            playerRatingRepository.replaceAllForPlayer(playerId, bundle.ratings());
            matchSettlementRepository.importPlayerRecords(playerId, bundle.stats(), bundle.history());
        } catch (RuntimeException exception) {
            rollbackImport(playerId, originalProfile, originalRatings, originalStats, originalHistory, exception);
            throw exception;
        }
    }

    private void rollbackImport(
            PlayerId playerId,
            Optional<PlayerProfile> originalProfile,
            List<PlayerRating> originalRatings,
            List<PlayerKitStats> originalStats,
            List<MatchHistoryEntry> originalHistory,
            RuntimeException originalFailure) {
        try {
            matchSettlementRepository.restoreImportedPlayerRecords(playerId, originalStats, originalHistory);
            playerRatingRepository.replaceAllForPlayer(playerId, originalRatings);
            if (originalProfile.isPresent()) {
                playerProfileRepository.restoreExact(originalProfile.orElseThrow());
            } else {
                playerProfileRepository.delete(playerId);
            }
        } catch (RuntimeException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }
}
