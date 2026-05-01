package io.github.xreatlabz.revprac.application.config;

import io.github.xreatlabz.revprac.application.result.Err;
import io.github.xreatlabz.revprac.application.result.Ok;
import io.github.xreatlabz.revprac.application.result.Problem;
import io.github.xreatlabz.revprac.application.result.ProblemCategory;
import io.github.xreatlabz.revprac.application.result.Result;
import io.github.xreatlabz.revprac.ports.config.ConfigSource;

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

        RevPracConfig config = new RevPracConfig(
                configVersion,
                new BootstrapConfig(failFastOnEnable),
                new DiagnosticsConfig(verboseLifecycleLogs),
                new MatchConfig(
                        duelRequestExpirySeconds.value(),
                        countdownTicks.value(),
                        maxDurationTicks.value(),
                        spectatorsEnabled));
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
        return matchesValue.present() && isScalarConfigValue(matchesValue.value());
    }

    private boolean isScalarConfigValue(Object value) {
        return value == null
                || value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>;
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
}
