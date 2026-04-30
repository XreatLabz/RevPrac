package io.github.xreatlabz.revprac.application.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.application.result.Err;
import io.github.xreatlabz.revprac.application.result.Ok;
import io.github.xreatlabz.revprac.application.result.Problem;
import io.github.xreatlabz.revprac.application.result.Result;
import io.github.xreatlabz.revprac.ports.config.ConfigSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class LoadValidatedConfigServiceContractTest {

    private final LoadValidatedConfigService service = new LoadValidatedConfigService();

    @Test
    void validConfigParsesIntoImmutableRevPracConfig() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "bootstrap.fail-fast-on-enable", false,
                "diagnostics.verbose-lifecycle-logs", true)));

        RevPracConfig config = assertOk(result);

        assertEquals(1, config.configVersion());
        assertTrue(config.getClass().isRecord(), "RevPracConfig should be an immutable record");
        assertTrue(config.bootstrap().getClass().isRecord(), "BootstrapConfig should be an immutable record");
        assertFalse(config.bootstrap().failFastOnEnable());
        assertTrue(config.diagnostics().getClass().isRecord(), "DiagnosticsConfig should be an immutable record");
        assertTrue(config.diagnostics().verboseLifecycleLogs());
    }

    @Test
    void missingOptionalBootstrapAndDiagnosticsValuesUseDocumentedDefaults() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of("config-version", 1)));

        RevPracConfig config = assertOk(result);

        assertTrue(config.bootstrap().failFastOnEnable());
        assertFalse(config.diagnostics().verboseLifecycleLogs());
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
