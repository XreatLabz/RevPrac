package io.github.xreatlabz.revprac.application.config;

import io.github.xreatlabz.revprac.application.result.Err;
import io.github.xreatlabz.revprac.application.result.Ok;
import io.github.xreatlabz.revprac.application.result.Problem;
import io.github.xreatlabz.revprac.application.result.ProblemCategory;
import io.github.xreatlabz.revprac.application.result.Result;
import io.github.xreatlabz.revprac.domain.queues.MatchmakingWindowPolicy;
import io.github.xreatlabz.revprac.ports.config.ConfigSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LoadValidatedConfigService {

    private static final int SUPPORTED_CONFIG_VERSION = 1;
    private static final boolean DEFAULT_FAIL_FAST_ON_ENABLE = true;
    private static final boolean DEFAULT_VERBOSE_LIFECYCLE_LOGS = false;

    public Result<RevPracConfig> load(ConfigSource source) {
        LookupValue configVersionValue = read(source, "config-version");
        if (!configVersionValue.present()) {
            return err("configuration.missing", "Config version is required", "config-version");
        }
        if (!(configVersionValue.value() instanceof Number number)) {
            return err("configuration.invalid-type", "Expected an integer at config-version", "config-version");
        }

        double doubleValue = number.doubleValue();
        if (Math.floor(doubleValue) != doubleValue) {
            return err("configuration.invalid-type", "Expected an integer at config-version", "config-version");
        }

        int configVersion = number.intValue();
        if (configVersion != SUPPORTED_CONFIG_VERSION) {
            return err(
                    "configuration.unsupported-version",
                    "Unsupported config version " + configVersion + "; expected " + SUPPORTED_CONFIG_VERSION,
                    "config-version");
        }

        Boolean failFastOnEnable = readBooleanWithDefault(source, "bootstrap.fail-fast-on-enable", DEFAULT_FAIL_FAST_ON_ENABLE);
        if (failFastOnEnable == null) {
            return err(
                    "configuration.invalid-type",
                    "Expected a boolean at bootstrap.fail-fast-on-enable",
                    "bootstrap.fail-fast-on-enable");
        }

        Boolean verboseLifecycleLogs =
                readBooleanWithDefault(source, "diagnostics.verbose-lifecycle-logs", DEFAULT_VERBOSE_LIFECYCLE_LOGS);
        if (verboseLifecycleLogs == null) {
            return err(
                    "configuration.invalid-type",
                    "Expected a boolean at diagnostics.verbose-lifecycle-logs",
                    "diagnostics.verbose-lifecycle-logs");
        }

        if (hasMalformedMatchesParent(source)) {
            return err("configuration.invalid-type", "Expected a configuration section at matches", "matches");
        }
        if (hasMalformedQueuesParent(source)) {
            return err("configuration.invalid-type", "Expected a configuration section at queues", "queues");
        }

        ParsedPositiveInt duelRequestExpirySeconds = readPositiveIntWithDefault(
                source,
                "matches.duel-request-expiry-seconds",
                MatchConfig.DEFAULT_DUEL_REQUEST_EXPIRY_SECONDS);
        if (!duelRequestExpirySeconds.valid()) {
            return err(duelRequestExpirySeconds.code(), duelRequestExpirySeconds.message(), duelRequestExpirySeconds.path());
        }

        ParsedPositiveInt countdownTicks = readPositiveIntWithDefault(
                source,
                "matches.countdown-ticks",
                MatchConfig.DEFAULT_COUNTDOWN_TICKS);
        if (!countdownTicks.valid()) {
            return err(countdownTicks.code(), countdownTicks.message(), countdownTicks.path());
        }

        ParsedPositiveInt maxDurationTicks = readPositiveIntWithDefault(
                source,
                "matches.max-duration-ticks",
                MatchConfig.DEFAULT_MAX_DURATION_TICKS);
        if (!maxDurationTicks.valid()) {
            return err(maxDurationTicks.code(), maxDurationTicks.message(), maxDurationTicks.path());
        }

        Boolean spectatorsEnabled =
                readBooleanWithDefault(source, "matches.spectators-enabled", MatchConfig.DEFAULT_SPECTATORS_ENABLED);
        if (spectatorsEnabled == null) {
            return err(
                    "configuration.invalid-type",
                    "Expected a boolean at matches.spectators-enabled",
                    "matches.spectators-enabled");
        }

        Result<QueueConfig> queueConfigResult = readQueueConfig(source);
        if (queueConfigResult instanceof Err<QueueConfig> queueConfigErr) {
            return new Err<>(queueConfigErr.problem());
        }
        QueueConfig queueConfig = ((Ok<QueueConfig>) queueConfigResult).value();

        RevPracConfig config = new RevPracConfig(
                configVersion,
                new BootstrapConfig(failFastOnEnable),
                new DiagnosticsConfig(verboseLifecycleLogs),
                new MatchConfig(
                        duelRequestExpirySeconds.value(),
                        countdownTicks.value(),
                        maxDurationTicks.value(),
                        spectatorsEnabled),
                queueConfig);
        return new Ok<>(config);
    }

    private Boolean readBooleanWithDefault(ConfigSource source, String path, boolean defaultValue) {
        LookupValue value = read(source, path);
        if (!value.present()) {
            return defaultValue;
        }
        if (value.value() instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return null;
    }

    private LookupValue read(ConfigSource source, String path) {
        if (source.hasPath(path)) {
            return new LookupValue(true, source.rawValue(path));
        }
        return new LookupValue(false, null);
    }

    private ParsedPositiveInt readPositiveIntWithDefault(ConfigSource source, String path, int defaultValue) {
        LookupValue value = read(source, path);
        if (!value.present()) {
            return new ParsedPositiveInt(true, defaultValue, null, null, path);
        }
        if (!(value.value() instanceof Number number)) {
            return new ParsedPositiveInt(
                    false,
                    null,
                    "configuration.invalid-type",
                    "Expected a positive integer at " + path,
                    path);
        }

        double doubleValue = number.doubleValue();
        if (Math.floor(doubleValue) != doubleValue
                || doubleValue > Integer.MAX_VALUE
                || doubleValue < Integer.MIN_VALUE) {
            return new ParsedPositiveInt(
                    false,
                    null,
                    "configuration.invalid-type",
                    "Expected a positive integer at " + path,
                    path);
        }

        int intValue = number.intValue();
        if (intValue <= 0) {
            return new ParsedPositiveInt(
                    false,
                    null,
                    "configuration.invalid-value",
                    "Expected a positive integer at " + path,
                    path);
        }

        return new ParsedPositiveInt(true, intValue, null, null, path);
    }

    private boolean hasMalformedMatchesParent(ConfigSource source) {
        LookupValue matchesValue = read(source, "matches");
        return matchesValue.present() && !isSectionLikeConfigValue(matchesValue.value());
    }

    private boolean hasMalformedQueuesParent(ConfigSource source) {
        LookupValue queuesValue = read(source, "queues");
        return queuesValue.present() && !isSectionLikeConfigValue(queuesValue.value());
    }

    private Result<QueueConfig> readQueueConfig(ConfigSource source) {
        ParsedPositiveInt matchmakingPeriodTicks = readPositiveIntWithDefault(
                source,
                "queues.matchmaking-period-ticks",
                QueueConfig.DEFAULT_MATCHMAKING_PERIOD_TICKS);
        if (!matchmakingPeriodTicks.valid()) {
            return new Err<>(problem(
                    matchmakingPeriodTicks.code(),
                    matchmakingPeriodTicks.message(),
                    matchmakingPeriodTicks.path()));
        }

        ParsedPositiveInt rankedBaseRating = readPositiveIntWithDefault(
                source,
                "queues.ranked-base-rating",
                QueueConfig.DEFAULT_RANKED_BASE_RATING);
        if (!rankedBaseRating.valid()) {
            return new Err<>(problem(rankedBaseRating.code(), rankedBaseRating.message(), rankedBaseRating.path()));
        }

        ParsedPositiveInt ticksPerSecond = readPositiveIntWithDefault(
                source,
                "queues.ticks-per-second",
                QueueConfig.DEFAULT_TICKS_PER_SECOND);
        if (!ticksPerSecond.valid()) {
            return new Err<>(problem(ticksPerSecond.code(), ticksPerSecond.message(), ticksPerSecond.path()));
        }

        Result<List<MatchmakingWindowPolicy.WindowStep>> rankedWindowsResult = readRankedWindows(source);
        if (rankedWindowsResult instanceof Err<List<MatchmakingWindowPolicy.WindowStep>> rankedWindowsErr) {
            return new Err<>(rankedWindowsErr.problem());
        }

        List<MatchmakingWindowPolicy.WindowStep> rankedWindows =
                ((Ok<List<MatchmakingWindowPolicy.WindowStep>>) rankedWindowsResult).value();
        try {
            return new Ok<>(new QueueConfig(
                    matchmakingPeriodTicks.value(),
                    rankedBaseRating.value(),
                    ticksPerSecond.value(),
                    rankedWindows));
        } catch (IllegalArgumentException exception) {
            return new Err<>(problem("configuration.invalid-value", exception.getMessage(), "queues.ranked-windows"));
        }
    }

    private Result<List<MatchmakingWindowPolicy.WindowStep>> readRankedWindows(ConfigSource source) {
        LookupValue rankedWindowsValue = read(source, "queues.ranked-windows");
        if (!rankedWindowsValue.present()) {
            return new Ok<>(QueueConfig.defaults().rankedWindows());
        }
        if (!(rankedWindowsValue.value() instanceof Iterable<?> entries)) {
            return new Err<>(problem(
                    "configuration.invalid-type",
                    "Expected a list at queues.ranked-windows",
                    "queues.ranked-windows"));
        }

        List<MatchmakingWindowPolicy.WindowStep> rankedWindows = new ArrayList<>();
        long previousWaitSeconds = Long.MIN_VALUE;
        int index = 0;
        for (Object entry : entries) {
            String entryPath = "queues.ranked-windows[" + index + "]";
            if (!(entry instanceof Map<?, ?> entryMap)) {
                return new Err<>(problem("configuration.invalid-type", "Expected a map at " + entryPath, entryPath));
            }

            ParsedNonNegativeLong waitSeconds = readNonNegativeLong(entryMap.get("wait-seconds"), entryPath + ".wait-seconds");
            if (!waitSeconds.valid()) {
                return new Err<>(problem(waitSeconds.code(), waitSeconds.message(), waitSeconds.path()));
            }

            ParsedPositiveInt ratingWindow = readPositiveInt(entryMap.get("rating-window"), entryPath + ".rating-window");
            if (!ratingWindow.valid()) {
                return new Err<>(problem(ratingWindow.code(), ratingWindow.message(), ratingWindow.path()));
            }

            if (waitSeconds.value() <= previousWaitSeconds) {
                return new Err<>(problem(
                        "configuration.invalid-value",
                        "Expected strictly increasing wait-seconds at " + entryPath + ".wait-seconds",
                        entryPath + ".wait-seconds"));
            }

            previousWaitSeconds = waitSeconds.value();
            rankedWindows.add(new MatchmakingWindowPolicy.WindowStep(waitSeconds.value(), ratingWindow.value()));
            index++;
        }

        if (rankedWindows.isEmpty()) {
            return new Err<>(problem(
                    "configuration.invalid-value",
                    "Expected at least one ranked window at queues.ranked-windows",
                    "queues.ranked-windows"));
        }

        return new Ok<>(List.copyOf(rankedWindows));
    }

    private boolean isSectionLikeConfigValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Map<?, ?>) {
            return true;
        }
        if (value.getClass().isArray() || value instanceof Iterable<?>) {
            return false;
        }
        return hasSectionLikeAccessors(value.getClass());
    }

    private boolean hasSectionLikeAccessors(Class<?> type) {
        return hasPublicMethod(type, "getKeys", boolean.class) && hasPublicMethod(type, "getValues", boolean.class);
    }

    private boolean hasPublicMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            type.getMethod(name, parameterTypes);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private Result<RevPracConfig> err(String code, String message, String path) {
        return new Err<>(problem(code, message, path));
    }

    private Problem problem(String code, String message, String path) {
        return new Problem(code, ProblemCategory.CONFIGURATION, message, path);
    }

    private record LookupValue(boolean present, Object value) {
    }

    private record ParsedPositiveInt(boolean valid, Integer value, String code, String message, String path) {
    }

    private ParsedPositiveInt readPositiveInt(Object rawValue, String path) {
        if (!(rawValue instanceof Number number)) {
            return new ParsedPositiveInt(
                    false,
                    null,
                    "configuration.invalid-type",
                    "Expected a positive integer at " + path,
                    path);
        }

        double doubleValue = number.doubleValue();
        if (Math.floor(doubleValue) != doubleValue
                || doubleValue > Integer.MAX_VALUE
                || doubleValue < Integer.MIN_VALUE) {
            return new ParsedPositiveInt(
                    false,
                    null,
                    "configuration.invalid-type",
                    "Expected a positive integer at " + path,
                    path);
        }

        int intValue = number.intValue();
        if (intValue <= 0) {
            return new ParsedPositiveInt(
                    false,
                    null,
                    "configuration.invalid-value",
                    "Expected a positive integer at " + path,
                    path);
        }

        return new ParsedPositiveInt(true, intValue, null, null, path);
    }

    private ParsedNonNegativeLong readNonNegativeLong(Object rawValue, String path) {
        if (!(rawValue instanceof Number number)) {
            return new ParsedNonNegativeLong(
                    false,
                    null,
                    "configuration.invalid-type",
                    "Expected a non-negative integer at " + path,
                    path);
        }

        double doubleValue = number.doubleValue();
        if (Math.floor(doubleValue) != doubleValue || doubleValue > Long.MAX_VALUE || doubleValue < Long.MIN_VALUE) {
            return new ParsedNonNegativeLong(
                    false,
                    null,
                    "configuration.invalid-type",
                    "Expected a non-negative integer at " + path,
                    path);
        }

        long longValue = number.longValue();
        if (longValue < 0) {
            return new ParsedNonNegativeLong(
                    false,
                    null,
                    "configuration.invalid-value",
                    "Expected a non-negative integer at " + path,
                    path);
        }

        return new ParsedNonNegativeLong(true, longValue, null, null, path);
    }

    private record ParsedNonNegativeLong(boolean valid, Long value, String code, String message, String path) {
    }
}
