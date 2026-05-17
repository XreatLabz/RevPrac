package io.github.xreatlabz.revprac.adapters.paper.players;

import io.github.xreatlabz.revprac.application.players.PlayerRecordBundle;
import io.github.xreatlabz.revprac.application.players.PlayerRecordTransferFiles;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.matches.MatchEndReason;
import io.github.xreatlabz.revprac.domain.matches.MatchHistoryEntry;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchOrigin;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerProfile;
import io.github.xreatlabz.revprac.domain.ratings.PlayerRating;
import io.github.xreatlabz.revprac.domain.stats.PlayerKitStats;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PaperPlayerRecordTransferFiles implements PlayerRecordTransferFiles {

    static final int SCHEMA_VERSION = 1;
    private static final String INVALID_IMPORT_PREFIX = "Import file is invalid: ";
    private static final String SIMPLE_IMPORT_FILE_MESSAGE = "Import file must be a simple .yml filename.";
    private static final String EXPORT_DIRECTORY = "exports/player-records";
    private static final String IMPORT_DIRECTORY = "imports/player-records";

    private final Path dataDirectory;

    public PaperPlayerRecordTransferFiles(Path dataDirectory) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
    }

    @Override
    public String export(PlayerRecordBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        Path relativePath = Path.of(
                EXPORT_DIRECTORY,
                bundle.profile().playerId().value() + ".yml");
        Path exportFile = dataDirectory.resolve(relativePath);
        try {
            Files.createDirectories(exportFile.getParent());
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.set("schema-version", SCHEMA_VERSION);
            writeProfile(configuration, bundle.profile());
            configuration.set("ratings", serializeRatings(bundle.ratings()));
            configuration.set("stats", serializeStats(bundle.stats()));
            configuration.set("history", serializeHistory(bundle.history()));
            configuration.save(exportFile.toFile());
            return relativePath.toString().replace('\\', '/');
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export player records.", exception);
        }
    }

    @Override
    public PlayerRecordBundle importFromFile(String simpleFileName) {
        validateSimpleImportFileName(simpleFileName);
        Path importFile = dataDirectory.resolve(Path.of(IMPORT_DIRECTORY, simpleFileName));
        if (Files.notExists(importFile)) {
            throw invalid("file does not exist.");
        }

        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(importFile.toFile());
        } catch (IOException | InvalidConfigurationException exception) {
            throw invalid("could not be read.", exception);
        }
        if (!isExactInteger(configuration.get("schema-version"), SCHEMA_VERSION)) {
            throw invalid("schema-version must be 1.");
        }

        PlayerProfile profile = readProfile(configuration);
        List<PlayerRating> ratings = readRatings(profile.playerId(), requireList(configuration.get("ratings"), "ratings"));
        List<PlayerKitStats> stats = readStats(profile.playerId(), requireList(configuration.get("stats"), "stats"));
        List<MatchHistoryEntry> history =
                readHistory(profile.playerId(), requireList(configuration.get("history"), "history"));
        try {
            return new PlayerRecordBundle(profile, ratings, stats, history);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage(), exception);
        }
    }

    private static void writeProfile(YamlConfiguration configuration, PlayerProfile profile) {
        configuration.set("profile.player-id", profile.playerId().value().toString());
        configuration.set("profile.last-known-name", profile.lastKnownName().orElse(null));
        configuration.set("profile.first-seen-at", profile.firstSeenAt().toEpochMilli());
        configuration.set("profile.last-seen-at", profile.lastSeenAt().toEpochMilli());
    }

    private static List<Map<String, Object>> serializeRatings(List<PlayerRating> ratings) {
        List<Map<String, Object>> serialized = new ArrayList<>(ratings.size());
        for (PlayerRating rating : ratings) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("kit-id", rating.kitId().value());
            map.put("rating", rating.rating());
            map.put("wins", rating.wins());
            map.put("losses", rating.losses());
            map.put("updated-at", rating.updatedAt().toEpochMilli());
            serialized.add(map);
        }
        return serialized;
    }

    private static List<Map<String, Object>> serializeStats(List<PlayerKitStats> stats) {
        List<Map<String, Object>> serialized = new ArrayList<>(stats.size());
        for (PlayerKitStats playerKitStats : stats) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("kit-id", playerKitStats.kitId().value());
            map.put("matches-played", playerKitStats.matchesPlayed());
            map.put("wins", playerKitStats.wins());
            map.put("losses", playerKitStats.losses());
            map.put("forfeits", playerKitStats.forfeits());
            map.put("timeouts", playerKitStats.timeouts());
            map.put("shutdowns", playerKitStats.shutdowns());
            map.put("updated-at", playerKitStats.updatedAt().toEpochMilli());
            serialized.add(map);
        }
        return serialized;
    }

    private static List<Map<String, Object>> serializeHistory(List<MatchHistoryEntry> history) {
        List<Map<String, Object>> serialized = new ArrayList<>(history.size());
        for (MatchHistoryEntry historyEntry : history) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("match-id", historyEntry.matchId().value().toString());
            map.put("player-one-id", historyEntry.playerOneId().value().toString());
            map.put("player-two-id", historyEntry.playerTwoId().value().toString());
            map.put("arena-id", historyEntry.arenaId().value());
            map.put("kit-id", historyEntry.kitId().value());
            map.put("origin", historyEntry.origin().name());
            map.put("end-reason", historyEntry.endReason().name());
            map.put("winner-id", historyEntry.winnerId().map(PlayerId::value).map(UUID::toString).orElse(null));
            map.put("loser-id", historyEntry.loserId().map(PlayerId::value).map(UUID::toString).orElse(null));
            map.put("active-ticks", historyEntry.activeTicks());
            map.put("completed-at", historyEntry.completedAt().toEpochMilli());
            serialized.add(map);
        }
        return serialized;
    }

    private static PlayerProfile readProfile(YamlConfiguration configuration) {
        return new PlayerProfile(
                requirePlayerId(configuration.getString("profile.player-id"), "profile.player-id"),
                optionalString(configuration.get("profile.last-known-name"), "profile.last-known-name"),
                requireInstant(configuration.get("profile.first-seen-at"), "profile.first-seen-at"),
                requireInstant(configuration.get("profile.last-seen-at"), "profile.last-seen-at"));
    }

    private static List<PlayerRating> readRatings(PlayerId playerId, List<?> rawRatings) {
        List<PlayerRating> ratings = new ArrayList<>(rawRatings.size());
        for (int index = 0; index < rawRatings.size(); index++) {
            Map<?, ?> values = requireMap(rawRatings.get(index), "ratings[" + index + "]");
            ratings.add(new PlayerRating(
                    playerId,
                    new KitId(requireString(values.get("kit-id"), "ratings[" + index + "].kit-id")),
                    requireInt(values.get("rating"), "ratings[" + index + "].rating"),
                    requireInt(values.get("wins"), "ratings[" + index + "].wins"),
                    requireInt(values.get("losses"), "ratings[" + index + "].losses"),
                    requireInstant(values.get("updated-at"), "ratings[" + index + "].updated-at")));
        }
        return List.copyOf(ratings);
    }

    private static List<PlayerKitStats> readStats(PlayerId playerId, List<?> rawStats) {
        List<PlayerKitStats> stats = new ArrayList<>(rawStats.size());
        for (int index = 0; index < rawStats.size(); index++) {
            Map<?, ?> values = requireMap(rawStats.get(index), "stats[" + index + "]");
            stats.add(new PlayerKitStats(
                    playerId,
                    new KitId(requireString(values.get("kit-id"), "stats[" + index + "].kit-id")),
                    requireLong(values.get("matches-played"), "stats[" + index + "].matches-played"),
                    requireLong(values.get("wins"), "stats[" + index + "].wins"),
                    requireLong(values.get("losses"), "stats[" + index + "].losses"),
                    requireLong(values.get("forfeits"), "stats[" + index + "].forfeits"),
                    requireLong(values.get("timeouts"), "stats[" + index + "].timeouts"),
                    requireLong(values.get("shutdowns"), "stats[" + index + "].shutdowns"),
                    requireInstant(values.get("updated-at"), "stats[" + index + "].updated-at")));
        }
        return List.copyOf(stats);
    }

    private static List<MatchHistoryEntry> readHistory(PlayerId playerId, List<?> rawHistory) {
        List<MatchHistoryEntry> history = new ArrayList<>(rawHistory.size());
        for (int index = 0; index < rawHistory.size(); index++) {
            Map<?, ?> values = requireMap(rawHistory.get(index), "history[" + index + "]");
            MatchHistoryEntry historyEntry = new MatchHistoryEntry(
                    requireMatchId(values.get("match-id"), "history[" + index + "].match-id"),
                    requirePlayerId(values.get("player-one-id"), "history[" + index + "].player-one-id"),
                    requirePlayerId(values.get("player-two-id"), "history[" + index + "].player-two-id"),
                    new ArenaId(requireString(values.get("arena-id"), "history[" + index + "].arena-id")),
                    new KitId(requireString(values.get("kit-id"), "history[" + index + "].kit-id")),
                    requireEnum(values.get("origin"), MatchOrigin.class, "history[" + index + "].origin"),
                    requireEnum(values.get("end-reason"), MatchEndReason.class, "history[" + index + "].end-reason"),
                    optionalPlayerId(values.get("winner-id"), "history[" + index + "].winner-id"),
                    optionalPlayerId(values.get("loser-id"), "history[" + index + "].loser-id"),
                    requireInt(values.get("active-ticks"), "history[" + index + "].active-ticks"),
                    requireInstant(values.get("completed-at"), "history[" + index + "].completed-at"));
            if (!historyEntry.playerOneId().equals(playerId) && !historyEntry.playerTwoId().equals(playerId)) {
                throw invalid("history[" + index + "] must include the imported player.");
            }
            history.add(historyEntry);
        }
        return List.copyOf(history);
    }

    private static void validateSimpleImportFileName(String simpleFileName) {
        if (simpleFileName == null || !simpleFileName.matches("[A-Za-z0-9._-]+\\.yml")) {
            throw new IllegalArgumentException(SIMPLE_IMPORT_FILE_MESSAGE);
        }
    }

    private static List<?> requireList(Object value, String path) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw invalid(path + " must be a YAML list.");
    }

    private static Map<?, ?> requireMap(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw invalid(path + " must be a YAML object.");
    }

    private static Optional<String> optionalString(Object value, String path) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String stringValue) {
            return Optional.of(stringValue);
        }
        throw invalid(path + " must be a string.");
    }

    private static String requireString(Object value, String path) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        throw invalid(path + " is required.");
    }

    private static MatchId requireMatchId(Object value, String path) {
        return new MatchId(requireUuid(value, path));
    }

    private static PlayerId requirePlayerId(Object value, String path) {
        return new PlayerId(requireUuid(value, path));
    }

    private static PlayerId requirePlayerId(String value, String path) {
        return new PlayerId(requireUuid(value, path));
    }

    private static UUID requireUuid(Object value, String path) {
        return requireUuid(value instanceof String stringValue ? stringValue : null, path);
    }

    private static UUID requireUuid(String value, String path) {
        if (value == null || value.isBlank()) {
            throw invalid(path + " is required.");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw invalid(path + " must be a UUID.", exception);
        }
    }

    private static Optional<PlayerId> optionalPlayerId(Object value, String path) {
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(requirePlayerId(value, path));
    }

    private static Instant requireInstant(Object value, String path) {
        return Instant.ofEpochMilli(requireLong(value, path));
    }

    private static int requireInt(Object value, String path) {
        if (value instanceof Number number) {
            double numericValue = number.doubleValue();
            if (Double.isFinite(numericValue)
                    && numericValue >= Integer.MIN_VALUE
                    && numericValue <= Integer.MAX_VALUE
                    && Math.rint(numericValue) == numericValue) {
                return (int) numericValue;
            }
        }
        throw invalid(path + " must be an integer.");
    }

    private static long requireLong(Object value, String path) {
        if (value instanceof Number number) {
            Long exactValue = tryExactLong(number);
            if (exactValue != null) {
                return exactValue;
            }
        }
        throw invalid(path + " must be an integer.");
    }

    private static <E extends Enum<E>> E requireEnum(Object value, Class<E> enumType, String path) {
        String stringValue = requireString(value, path);
        try {
            return Enum.valueOf(enumType, stringValue);
        } catch (IllegalArgumentException exception) {
            throw invalid(path + " is invalid.", exception);
        }
    }

    private static boolean isExactInteger(Object value, int expected) {
        return value instanceof Number number && tryExactLong(number) == expected;
    }

    private static Long tryExactLong(Number number) {
        if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
            return number.longValue();
        }
        if (number instanceof BigInteger bigInteger) {
            if (bigInteger.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0
                    && bigInteger.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
                return bigInteger.longValue();
            }
            return null;
        }
        if (number instanceof BigDecimal bigDecimal) {
            try {
                return bigDecimal.longValueExact();
            } catch (ArithmeticException ignored) {
                return null;
            }
        }

        double numericValue = number.doubleValue();
        if (Double.isFinite(numericValue)
                && numericValue >= Long.MIN_VALUE
                && numericValue <= Long.MAX_VALUE
                && Math.rint(numericValue) == numericValue) {
            try {
                return BigDecimal.valueOf(numericValue).longValueExact();
            } catch (ArithmeticException ignored) {
                return null;
            }
        }
        return null;
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException(INVALID_IMPORT_PREFIX + reason);
    }

    private static IllegalArgumentException invalid(String reason, Exception cause) {
        return new IllegalArgumentException(INVALID_IMPORT_PREFIX + reason, cause);
    }
}
