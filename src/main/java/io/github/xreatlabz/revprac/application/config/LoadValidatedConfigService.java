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

        RevPracConfig config = new RevPracConfig(
                configVersion,
                new BootstrapConfig(failFastOnEnable),
                new DiagnosticsConfig(verboseLifecycleLogs));
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

    private Result<RevPracConfig> err(String code, String message, String path) {
        return new Err<>(problem(code, message, path));
    }

    private Problem problem(String code, String message, String path) {
        return new Problem(code, ProblemCategory.CONFIGURATION, message, path);
    }

    private record LookupValue(boolean present, Object value) {
    }
}
