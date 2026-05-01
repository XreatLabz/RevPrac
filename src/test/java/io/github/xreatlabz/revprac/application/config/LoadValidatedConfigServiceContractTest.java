package io.github.xreatlabz.revprac.application.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.application.result.Err;
import io.github.xreatlabz.revprac.application.result.Ok;
import io.github.xreatlabz.revprac.application.result.Problem;
import io.github.xreatlabz.revprac.application.result.ProblemCategory;
import io.github.xreatlabz.revprac.application.result.Result;
import io.github.xreatlabz.revprac.ports.config.ConfigSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class LoadValidatedConfigServiceContractTest {

    private final LoadValidatedConfigService service = new LoadValidatedConfigService();

    @Test
    void validConfigParsesIntoImmutableRevPracConfig() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "bootstrap.fail-fast-on-enable", false,
                "diagnostics.verbose-lifecycle-logs", true,
                "matches.duel-request-expiry-seconds", 45,
                "matches.countdown-ticks", 60,
                "matches.max-duration-ticks", 4000,
                "matches.spectators-enabled", false)));

        RevPracConfig config = assertOk(result);

        assertEquals(1, config.configVersion());
        assertTrue(config.getClass().isRecord(), "RevPracConfig should be an immutable record");
        assertTrue(config.bootstrap().getClass().isRecord(), "BootstrapConfig should be an immutable record");
        assertFalse(config.bootstrap().failFastOnEnable());
        assertTrue(config.diagnostics().getClass().isRecord(), "DiagnosticsConfig should be an immutable record");
        assertTrue(config.diagnostics().verboseLifecycleLogs());
        assertTrue(config.matches().getClass().isRecord(), "MatchConfig should be an immutable record");
        assertEquals(45, config.matches().duelRequestExpirySeconds());
        assertEquals(60, config.matches().countdownTicks());
        assertEquals(4000, config.matches().maxDurationTicks());
        assertFalse(config.matches().spectatorsEnabled());
    }

    @Test
    void missingOptionalBootstrapAndDiagnosticsValuesUseDocumentedDefaults() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of("config-version", 1)));

        RevPracConfig config = assertOk(result);

        assertTrue(config.bootstrap().failFastOnEnable());
        assertFalse(config.diagnostics().verboseLifecycleLogs());
        assertEquals(30, config.matches().duelRequestExpirySeconds());
        assertEquals(100, config.matches().countdownTicks());
        assertEquals(12000, config.matches().maxDurationTicks());
        assertTrue(config.matches().spectatorsEnabled());
    }

    @Test
    void explicitMapMatchesParentUsesDocumentedDefaults() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "matches", Map.of())));

        RevPracConfig config = assertOk(result);

        assertEquals(30, config.matches().duelRequestExpirySeconds());
        assertEquals(100, config.matches().countdownTicks());
        assertEquals(12000, config.matches().maxDurationTicks());
        assertTrue(config.matches().spectatorsEnabled());
    }

    @Test
    void invalidConfigVersionReturnsProblemNamingConfigVersionPath() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of("config-version", 2)));

        Problem problem = assertErr(result);

        assertEquals("config-version", problem.path());
    }

    @Test
    void invalidBooleanReturnsProblemNamingConfigPath() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "bootstrap.fail-fast-on-enable", "yes")));

        Problem problem = assertErr(result);

        assertEquals("bootstrap.fail-fast-on-enable", problem.path());
    }

    @Test
    void nonPositiveMatchRequestExpiryReturnsProblemNamingExactPath() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "matches.duel-request-expiry-seconds", 0)));

        Problem problem = assertErr(result);

        assertEquals("matches.duel-request-expiry-seconds", problem.path());
    }

    @Test
    void nonWholeMatchCountdownReturnsProblemNamingExactPath() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "matches.countdown-ticks", 12.5d)));

        Problem problem = assertErr(result);

        assertEquals("matches.countdown-ticks", problem.path());
    }

    @Test
    void malformedMatchesParentReturnsConfigurationProblemAtMatchesPath() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "matches", "not-a-section")));

        Problem problem = assertErr(result);

        assertEquals(ProblemCategory.CONFIGURATION, problem.category());
        assertEquals("matches", problem.path());
    }

    @Test
    void listMatchesParentReturnsConfigurationProblemAtMatchesPath() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "matches", List.of("not-a-section"))));

        Problem problem = assertErr(result);

        assertEquals(ProblemCategory.CONFIGURATION, problem.category());
        assertEquals("matches", problem.path());
    }

    @Test
    void arrayMatchesParentReturnsConfigurationProblemAtMatchesPath() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "matches", new Object[] {"not-a-section"})));

        Problem problem = assertErr(result);

        assertEquals(ProblemCategory.CONFIGURATION, problem.category());
        assertEquals("matches", problem.path());
    }

    @Test
    void opaqueObjectMatchesParentReturnsConfigurationProblemAtMatchesPath() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "matches", new Object())));

        Problem problem = assertErr(result);

        assertEquals(ProblemCategory.CONFIGURATION, problem.category());
        assertEquals("matches", problem.path());
    }

    @Test
    void nonPositiveMatchMaxDurationReturnsConfigurationProblemNamingExactPath() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "matches.max-duration-ticks", 0)));

        Problem problem = assertErr(result);

        assertEquals(ProblemCategory.CONFIGURATION, problem.category());
        assertEquals("matches.max-duration-ticks", problem.path());
    }

    @Test
    void invalidMatchSpectatorsFlagReturnsProblemNamingExactPath() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "matches.spectators-enabled", "true")));

        Problem problem = assertErr(result);

        assertEquals("matches.spectators-enabled", problem.path());
    }

    @Test
    void loadValidatedConfigServiceHasNoBukkitOrPaperDependency() throws IOException {
        Path serviceSource = Path.of("src/main/java/io/github/xreatlabz/revprac/application/config/LoadValidatedConfigService.java");

        assertTrue(Files.exists(serviceSource), "Expected source file to exist: " + serviceSource);
        String source = Files.readString(serviceSource);

        assertFalse(source.contains("org.bukkit"), "LoadValidatedConfigService must not depend on Bukkit");
        assertFalse(source.contains("io.papermc.paper"), "LoadValidatedConfigService must not depend on Paper");
    }

    private static RevPracConfig assertOk(Result<RevPracConfig> result) {
        Ok<?> ok = assertInstanceOf(Ok.class, result, "Expected a successful Result");
        return assertInstanceOf(RevPracConfig.class, ok.value());
    }

    private static Problem assertErr(Result<RevPracConfig> result) {
        Err<?> err = assertInstanceOf(Err.class, result, "Expected a failed Result");
        return err.problem();
    }

    private record MapConfigSource(Map<String, Object> values) implements ConfigSource {

        @Override
        public Object rawValue(String path) {
            return values.get(path);
        }

        @Override
        public boolean hasPath(String path) {
            return values.containsKey(path);
        }

        @Override
        public String sourceDescription() {
            return "test config";
        }
    }
}
