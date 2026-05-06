package io.github.xreatlabz.revprac.application.players;

import io.github.xreatlabz.revprac.application.config.QueueConfig;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import io.github.xreatlabz.revprac.ports.matches.MatchSettlementRepository;
import io.github.xreatlabz.revprac.ports.players.PlayerProfileRepository;
import io.github.xreatlabz.revprac.ports.ratings.PlayerRatingRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PlayerRecordQueryService {

    public static final int MAX_PAGE_SIZE = 10;
    public static final int MAX_HISTORY_PAGE = 100;
    public static final String HISTORY_PAGE_RANGE_MESSAGE =
            "page must be between 1 and " + MAX_HISTORY_PAGE;

    private static final String RECORDS_UNAVAILABLE_MESSAGE = "player records are temporarily unavailable";
    private static final String PAGE_SIZE_RANGE_MESSAGE =
            "pageSize must be between 1 and " + MAX_PAGE_SIZE;
    private static final String UNKNOWN_OPPONENT_NAME = "Unknown player";

    private final KitRegistryService kitRegistryService;
    private final MatchSettlementRepository matchSettlementRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final QueueConfig queueConfig;

    public PlayerRecordQueryService(
            KitRegistryService kitRegistryService,
            MatchSettlementRepository matchSettlementRepository,
            PlayerRatingRepository playerRatingRepository,
            PlayerProfileRepository playerProfileRepository,
            QueueConfig queueConfig) {
        this.kitRegistryService = Objects.requireNonNull(kitRegistryService, "kitRegistryService");
        this.matchSettlementRepository = Objects.requireNonNull(matchSettlementRepository, "matchSettlementRepository");
        this.playerRatingRepository = Objects.requireNonNull(playerRatingRepository, "playerRatingRepository");
        this.playerProfileRepository = Objects.requireNonNull(playerProfileRepository, "playerProfileRepository");
        this.queueConfig = Objects.requireNonNull(queueConfig, "queueConfig");
    }

    public PlayerKitSummaryView summary(PlayerId playerId, KitId kitId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(kitId, "kitId");
        try {
            KitDefinition kitDefinition = requireEnabledKit(kitId);
            Optional<PlayerKitStats> stats = matchSettlementRepository.findStats(playerId, kitId);
            return new PlayerKitSummaryView(
                    kitDefinition.id(),
                    kitDefinition.displayName(),
                    stats.map(PlayerKitStats::matchesPlayed).orElse(0L),
                    stats.map(PlayerKitStats::wins).orElse(0L),
                    stats.map(PlayerKitStats::losses).orElse(0L),
                    stats.map(PlayerKitStats::forfeits).orElse(0L),
                    stats.map(PlayerKitStats::timeouts).orElse(0L),
                    stats.map(PlayerKitStats::shutdowns).orElse(0L),
                    ratingView(playerId, kitDefinition));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    public PlayerMatchHistoryPage recentHistory(PlayerId playerId, int page, int pageSize) {
        Objects.requireNonNull(playerId, "playerId");
        if (page < 1 || page > MAX_HISTORY_PAGE) {
            throw new IllegalArgumentException(HISTORY_PAGE_RANGE_MESSAGE);
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(PAGE_SIZE_RANGE_MESSAGE);
        }
        int offset = (page - 1) * pageSize;
        int limit = pageSize + 1;
        try {
            List<MatchHistoryEntry> history =
                    matchSettlementRepository.findRecentHistory(playerId, limit, offset);
            boolean hasNextPage = history.size() > pageSize;
            List<PlayerMatchHistoryLineItem> items = history.stream()
                    .limit(pageSize)
                    .map(entry -> toHistoryLineItem(playerId, entry))
                    .toList();
            return new PlayerMatchHistoryPage(page, pageSize, items, hasNextPage);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private Optional<PlayerRatingView> ratingView(PlayerId playerId, KitDefinition kitDefinition) {
        if (!kitDefinition.rules().ranked()) {
            return Optional.empty();
        }
        return playerRatingRepository.find(playerId, kitDefinition.id())
                .map(rating -> new PlayerRatingView(rating.rating()))
                .or(() -> Optional.of(new PlayerRatingView(queueConfig.rankedBaseRating())));
    }

    private PlayerMatchHistoryLineItem toHistoryLineItem(PlayerId playerId, MatchHistoryEntry entry) {
        PlayerId opponentId = entry.playerOneId().equals(playerId) ? entry.playerTwoId() : entry.playerOneId();
        String opponentName = playerProfileRepository.find(opponentId)
                .flatMap(PlayerProfile::lastKnownName)
                .orElse(UNKNOWN_OPPONENT_NAME);
        return new PlayerMatchHistoryLineItem(
                entry.matchId(),
                entry.kitId(),
                entry.origin(),
                entry.endReason(),
                opponentId,
                opponentName,
                entry.winnerId().map(playerId::equals),
                entry.completedAt());
    }

    private KitDefinition requireEnabledKit(KitId kitId) {
        return kitRegistryService.kits().stream()
                .filter(kit -> kit.id().equals(kitId))
                .findFirst()
                .filter(KitDefinition::enabled)
                .orElseThrow(() -> new IllegalArgumentException("unknown kit: " + kitId.value()));
    }

    private static IllegalStateException unavailable(RuntimeException exception) {
        return new IllegalStateException(RECORDS_UNAVAILABLE_MESSAGE, exception);
    }
}
