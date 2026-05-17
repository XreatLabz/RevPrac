package io.github.xreatlabz.revprac.application.config;

import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.application.result.Err;
import io.github.xreatlabz.revprac.application.result.Ok;
import io.github.xreatlabz.revprac.application.result.Problem;
import io.github.xreatlabz.revprac.application.result.ProblemCategory;
import io.github.xreatlabz.revprac.application.result.Result;
import io.github.xreatlabz.revprac.ports.config.ConfigSource;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class LoadValidatedConfigServiceContractTest {

    private static final String QUEUE_CONFIG_TYPE = "io.github.xreatlabz.revprac.application.config.QueueConfig";
    private static final String STORAGE_CONFIG_TYPE = "io.github.xreatlabz.revprac.application.config.StorageConfig";
    private static final String WINDOW_STEP_TYPE =
            "io.github.xreatlabz.revprac.domain.queues.MatchmakingWindowPolicy$WindowStep";

    private final LoadValidatedConfigService service = new LoadValidatedConfigService();

    @Test
    void validConfigParsesIntoImmutableRevPracConfig() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.ofEntries(
                Map.entry("config-version", 1),
                Map.entry("bootstrap.fail-fast-on-enable", false),
                Map.entry("diagnostics.verbose-lifecycle-logs", true),
                Map.entry("matches.duel-request-expiry-seconds", 45),
                Map.entry("matches.countdown-ticks", 60),
                Map.entry("matches.max-duration-ticks", 4000),
                Map.entry("matches.spectators-enabled", false),
                Map.entry("queues.matchmaking-period-ticks", 10),
                Map.entry("queues.ranked-base-rating", 1337),
                Map.entry("queues.ticks-per-second", 40),
                Map.entry(
                        "queues.ranked-windows",
                        List.of(
                                Map.of("wait-seconds", 0, "rating-window", 60),
                                Map.of("wait-seconds", 15, "rating-window", 120))),
                Map.entry("storage.backend", "sqlite"),
                Map.entry("storage.sqlite-path", "custom/revprac.db"),
                Map.entry("storage.pool-maximum-size", 8))));

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
        Object queueConfig = readAccessor(config, "queues");
        assertTrue(queueConfig.getClass().isRecord(), "QueueConfig should be an immutable record");
        assertEquals(10, readAccessor(queueConfig, "matchmakingPeriodTicks"));
        assertEquals(1337, readAccessor(queueConfig, "rankedBaseRating"));
        assertEquals(40, readAccessor(queueConfig, "ticksPerSecond"));
        List<?> rankedWindows = assertInstanceOf(List.class, readAccessor(queueConfig, "rankedWindows"));
        assertEquals(2, rankedWindows.size());
        assertEquals(15L, readAccessor(rankedWindows.get(1), "waitSeconds"));
        assertEquals(120, readAccessor(rankedWindows.get(1), "ratingWindow"));
        Object storageConfig = readAccessor(config, "storage");
        assertTrue(storageConfig.getClass().isRecord(), "StorageConfig should be an immutable record");
        assertEquals("sqlite", readAccessor(storageConfig, "backend"));
        assertEquals("custom/revprac.db", readAccessor(storageConfig, "sqlitePath"));
        assertEquals(null, readAccessor(storageConfig, "postgresql"));
        assertEquals(8, readAccessor(storageConfig, "poolMaximumSize"));
    }

    @Test
    void missingOptionalBootstrapDiagnosticsMatchQueueAndStorageValuesUseDocumentedDefaults() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of("config-version", 1)));

        RevPracConfig config = assertOk(result);

        assertTrue(config.bootstrap().failFastOnEnable());
        assertFalse(config.diagnostics().verboseLifecycleLogs());
        assertEquals(30, config.matches().duelRequestExpirySeconds());
        assertEquals(100, config.matches().countdownTicks());
        assertEquals(12000, config.matches().maxDurationTicks());
        assertTrue(config.matches().spectatorsEnabled());
        Object queueConfig = readAccessor(config, "queues");
        List<?> rankedWindows = assertInstanceOf(List.class, readAccessor(queueConfig, "rankedWindows"));
        assertEquals(20, readAccessor(queueConfig, "matchmakingPeriodTicks"));
        assertEquals(1000, readAccessor(queueConfig, "rankedBaseRating"));
        assertEquals(20, readAccessor(queueConfig, "ticksPerSecond"));
        assertEquals(5, rankedWindows.size());
        assertEquals(0L, readAccessor(rankedWindows.get(0), "waitSeconds"));
        assertEquals(50, readAccessor(rankedWindows.get(0), "ratingWindow"));
        assertEquals(45L, readAccessor(rankedWindows.get(4), "waitSeconds"));
        assertEquals(400, readAccessor(rankedWindows.get(4), "ratingWindow"));
        Object storageConfig = readAccessor(config, "storage");
        assertEquals("sqlite", readAccessor(storageConfig, "backend"));
        assertEquals("data/revprac.db", readAccessor(storageConfig, "sqlitePath"));
        assertEquals(null, readAccessor(storageConfig, "postgresql"));
        assertEquals(4, readAccessor(storageConfig, "poolMaximumSize"));
    }

    @Test
    void explicitMapParentsUseDocumentedDefaults() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "matches", Map.of(),
                "queues", Map.of(),
                "storage", Map.of())));

        RevPracConfig config = assertOk(result);

        assertEquals(30, config.matches().duelRequestExpirySeconds());
        assertEquals(100, config.matches().countdownTicks());
        assertEquals(12000, config.matches().maxDurationTicks());
        assertTrue(config.matches().spectatorsEnabled());
        Object queueConfig = readAccessor(config, "queues");
        assertEquals(20, readAccessor(queueConfig, "matchmakingPeriodTicks"));
        assertEquals(1000, readAccessor(queueConfig, "rankedBaseRating"));
        assertEquals(20, readAccessor(queueConfig, "ticksPerSecond"));
        assertEquals(5, assertInstanceOf(List.class, readAccessor(queueConfig, "rankedWindows")).size());
        Object storageConfig = readAccessor(config, "storage");
        assertEquals("sqlite", readAccessor(storageConfig, "backend"));
        assertEquals("data/revprac.db", readAccessor(storageConfig, "sqlitePath"));
        assertEquals(null, readAccessor(storageConfig, "postgresql"));
        assertEquals(4, readAccessor(storageConfig, "poolMaximumSize"));
    }

    @Test
    void postgresqlStorageConfigParsesWithOptionalSchema() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.ofEntries(
                Map.entry("config-version", 1),
                Map.entry("storage.backend", "postgresql"),
                Map.entry("storage.postgresql.jdbc-url", "jdbc:postgresql://localhost:5432/revprac"),
                Map.entry("storage.postgresql.username", "revprac"),
                Map.entry("storage.postgresql.password", "secret"),
                Map.entry("storage.postgresql.schema", "practice"),
                Map.entry("storage.pool-maximum-size", 6))));

        RevPracConfig config = assertOk(result);
        Object storageConfig = readAccessor(config, "storage");
        Object postgresql = readAccessor(storageConfig, "postgresql");

        assertEquals("postgresql", readAccessor(storageConfig, "backend"));
        assertEquals(null, readAccessor(storageConfig, "sqlitePath"));
        assertEquals(6, readAccessor(storageConfig, "poolMaximumSize"));
        assertTrue(postgresql.getClass().isRecord(), "PostgreSqlConfig should be an immutable record");
        assertEquals("jdbc:postgresql://localhost:5432/revprac", readAccessor(postgresql, "jdbcUrl"));
        assertEquals("revprac", readAccessor(postgresql, "username"));
        assertEquals("secret", readAccessor(postgresql, "password"));
        assertEquals("practice", readAccessor(postgresql, "schema"));
    }

    @Test
    void postgresqlStorageConfigAllowsMissingOrBlankSqlitePath() {
        RevPracConfig missingSqlitePath = assertOk(service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "storage.backend", "postgresql",
                "storage.postgresql.jdbc-url", "jdbc:postgresql://localhost:5432/revprac",
                "storage.postgresql.username", "revprac",
                "storage.postgresql.password", "secret"))));
        RevPracConfig blankSqlitePath = assertOk(service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "storage.backend", "postgresql",
                "storage.sqlite-path", "   ",
                "storage.postgresql.jdbc-url", "jdbc:postgresql://localhost:5432/revprac",
                "storage.postgresql.username", "revprac",
                "storage.postgresql.password", "secret"))));

        assertEquals(null, readAccessor(readAccessor(missingSqlitePath, "storage"), "sqlitePath"));
        assertEquals(null, readAccessor(readAccessor(blankSqlitePath, "storage"), "sqlitePath"));
    }

    @Test
    void sqliteStorageConfigRejectsBlankSqlitePath() {
        Problem problem = assertErr(service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "storage.backend", "sqlite",
                "storage.sqlite-path", "   "))));

        assertEquals("storage.sqlite-path", problem.path());
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
    void malformedQueuesParentReturnsConfigurationProblemAtQueuesPath() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "queues", "not-a-section")));

        Problem problem = assertErr(result);

        assertEquals(ProblemCategory.CONFIGURATION, problem.category());
        assertEquals("queues", problem.path());
    }

    @Test
    void malformedStorageParentReturnsConfigurationProblemAtStoragePath() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "storage", "not-a-section")));

        Problem problem = assertErr(result);

        assertEquals(ProblemCategory.CONFIGURATION, problem.category());
        assertEquals("storage", problem.path());
    }

    @Test
    void invalidStorageBackendReturnsProblemNamingExactPath() {
        Result<RevPracConfig> result = service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "storage.backend", "mysql")));

        Problem problem = assertErr(result);

        assertEquals(ProblemCategory.CONFIGURATION, problem.category());
        assertEquals("storage.backend", problem.path());
    }

    @Test
    void missingPostgresqlFieldsReturnProblemsNamingExactPaths() {
        Problem missingJdbcUrl = assertErr(service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "storage.backend", "postgresql",
                "storage.postgresql.username", "revprac",
                "storage.postgresql.password", "secret"))));
        Problem blankUsername = assertErr(service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "storage.backend", "postgresql",
                "storage.postgresql.jdbc-url", "jdbc:postgresql://localhost:5432/revprac",
                "storage.postgresql.username", "   ",
                "storage.postgresql.password", "secret"))));

        assertEquals("storage.postgresql.jdbc-url", missingJdbcUrl.path());
        assertEquals("storage.postgresql.username", blankUsername.path());
    }

    @Test
    void invalidStoragePoolSizeReturnsProblemNamingExactPath() {
        Problem zeroProblem = assertErr(service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "storage.pool-maximum-size", 0))));
        Problem nonWholeProblem = assertErr(service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "storage.pool-maximum-size", 2.5d))));

        assertEquals("storage.pool-maximum-size", zeroProblem.path());
        assertEquals("storage.pool-maximum-size", nonWholeProblem.path());
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
    void invalidQueueScalarsReturnProblemsNamingExactPaths() {
        Problem matchmakingPeriodProblem = assertErr(service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "queues.matchmaking-period-ticks", 0))));
        Problem baseRatingProblem = assertErr(service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "queues.ranked-base-rating", -1))));
        Problem ticksPerSecondProblem = assertErr(service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "queues.ticks-per-second", "fast"))));

        assertEquals("queues.matchmaking-period-ticks", matchmakingPeriodProblem.path());
        assertEquals("queues.ranked-base-rating", baseRatingProblem.path());
        assertEquals("queues.ticks-per-second", ticksPerSecondProblem.path());
    }

    @Test
    void invalidRankedWindowsReturnProblemsNamingExactPaths() {
        Problem listProblem = assertErr(service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "queues.ranked-windows", "not-a-list"))));
        Problem entryProblem = assertErr(service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "queues.ranked-windows", List.of("bad-entry")))));
        Problem valueProblem = assertErr(service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "queues.ranked-windows", List.of(Map.of(
                        "wait-seconds", -1,
                        "rating-window", 50))))));
        Problem duplicateProblem = assertErr(service.load(new MapConfigSource(Map.of(
                "config-version", 1,
                "queues.ranked-windows", List.of(
                        Map.of("wait-seconds", 0, "rating-window", 50),
                        Map.of("wait-seconds", 0, "rating-window", 100))))));

        assertEquals("queues.ranked-windows", listProblem.path());
        assertEquals("queues.ranked-windows[0]", entryProblem.path());
        assertEquals("queues.ranked-windows[0].wait-seconds", valueProblem.path());
        assertEquals("queues.ranked-windows[1].wait-seconds", duplicateProblem.path());
    }

    @Test
    void queueConfigDefaultsAndConstructorRejectInvalidWindowDefinitions() throws ReflectiveOperationException {
        Class<?> queueConfigType = Class.forName(QUEUE_CONFIG_TYPE);
        Class<?> windowStepType = Class.forName(WINDOW_STEP_TYPE);

        Object defaults = queueConfigType.getMethod("defaults").invoke(null);
        assertEquals(20, queueConfigType.getMethod("matchmakingPeriodTicks").invoke(defaults));
        assertEquals(1000, queueConfigType.getMethod("rankedBaseRating").invoke(defaults));
        assertEquals(20, queueConfigType.getMethod("ticksPerSecond").invoke(defaults));
        List<?> defaultWindows = assertInstanceOf(List.class, queueConfigType.getMethod("rankedWindows").invoke(defaults));
        assertEquals(5, defaultWindows.size());

        List<?> unorderedWindows = List.of(
                instantiateRecord(windowStepType, Map.of("waitSeconds", 10L, "ratingWindow", 100)),
                instantiateRecord(windowStepType, Map.of("waitSeconds", 5L, "ratingWindow", 150)));
        InvocationTargetException exception = assertInstanceOf(
                InvocationTargetException.class,
                org.junit.jupiter.api.Assertions.assertThrows(
                        InvocationTargetException.class,
                        () -> queueConfigType
                                .getDeclaredConstructor(int.class, int.class, int.class, List.class)
                                .newInstance(20, 1000, 20, unorderedWindows)));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void storageConfigDefaultsAndConstructorRejectInvalidValues() throws ReflectiveOperationException {
        Class<?> storageConfigType = Class.forName(STORAGE_CONFIG_TYPE);

        Object defaults = storageConfigType.getMethod("defaults").invoke(null);
        assertEquals("sqlite", storageConfigType.getMethod("backend").invoke(defaults));
        assertEquals("data/revprac.db", storageConfigType.getMethod("sqlitePath").invoke(defaults));
        assertEquals(null, storageConfigType.getMethod("postgresql").invoke(defaults));
        assertEquals(4, storageConfigType.getMethod("poolMaximumSize").invoke(defaults));

        Class<?> postgresqlConfigType = Class.forName(STORAGE_CONFIG_TYPE + "$PostgreSqlConfig");
        Object postgresqlConfig = postgresqlConfigType
                .getDeclaredConstructor(String.class, String.class, String.class, String.class)
                .newInstance(
                        "jdbc:postgresql://localhost:5432/revprac",
                        "revprac",
                        "secret",
                        "practice");

        assertInstanceOf(
                IllegalArgumentException.class,
                assertThrows(
                                InvocationTargetException.class,
                                () -> storageConfigType
                                        .getDeclaredConstructor(String.class, String.class, postgresqlConfigType, int.class)
                                        .newInstance("mysql", "data/revprac.db", null, 4))
                        .getCause());
        assertInstanceOf(
                IllegalArgumentException.class,
                assertThrows(
                                InvocationTargetException.class,
                                () -> storageConfigType
                                        .getDeclaredConstructor(String.class, String.class, postgresqlConfigType, int.class)
                                        .newInstance("sqlite", "data/revprac.db", null, 0))
                        .getCause());
        assertInstanceOf(
                IllegalArgumentException.class,
                assertThrows(
                                InvocationTargetException.class,
                                () -> storageConfigType
                                        .getDeclaredConstructor(String.class, String.class, postgresqlConfigType, int.class)
                                        .newInstance("postgresql", null, null, 4))
                        .getCause());
        Object postgresqlWithoutSqlitePath = storageConfigType
                .getDeclaredConstructor(String.class, String.class, postgresqlConfigType, int.class)
                .newInstance("postgresql", "   ", postgresqlConfig, 4);
        assertEquals("postgresql", storageConfigType.getMethod("backend").invoke(postgresqlWithoutSqlitePath));
        assertEquals(null, storageConfigType.getMethod("sqlitePath").invoke(postgresqlWithoutSqlitePath));
        assertEquals(
                "practice",
                postgresqlConfigType.getMethod("schema").invoke(postgresqlConfig));
        assertInstanceOf(
                IllegalArgumentException.class,
                assertThrows(
                                InvocationTargetException.class,
                                () -> postgresqlConfigType
                                        .getDeclaredConstructor(String.class, String.class, String.class, String.class)
                                        .newInstance("jdbc:postgresql://localhost:5432/revprac", " ", "secret", "practice"))
                        .getCause());
    }

    @Test
    void loadValidatedConfigServiceHasNoBukkitOrPaperDependency() throws IOException {
        Path serviceSource = Path.of("src/main/java/io/github/xreatlabz/revprac/application/config/LoadValidatedConfigService.java");
        Path storageConfigSource = Path.of("src/main/java/io/github/xreatlabz/revprac/application/config/StorageConfig.java");

        assertTrue(Files.exists(serviceSource), "Expected source file to exist: " + serviceSource);
        assertTrue(Files.exists(storageConfigSource), "Expected source file to exist: " + storageConfigSource);
        String source = Files.readString(serviceSource);
        String storageSource = Files.readString(storageConfigSource);

        assertFalse(source.contains("org.bukkit"), "LoadValidatedConfigService must not depend on Bukkit");
        assertFalse(source.contains("io.papermc.paper"), "LoadValidatedConfigService must not depend on Paper");
        assertFalse(storageSource.contains("org.bukkit"), "StorageConfig must not depend on Bukkit");
        assertFalse(storageSource.contains("io.papermc.paper"), "StorageConfig must not depend on Paper");
    }

    private static RevPracConfig assertOk(Result<RevPracConfig> result) {
        Ok<?> ok = assertInstanceOf(Ok.class, result, "Expected a successful Result");
        return assertInstanceOf(RevPracConfig.class, ok.value());
    }

    private static Problem assertErr(Result<RevPracConfig> result) {
        Err<?> err = assertInstanceOf(Err.class, result, "Expected a failed Result");
        return err.problem();
    }

    private static Object readAccessor(Object target, String accessorName) {
        try {
            return target.getClass().getMethod(accessorName).invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not read accessor " + accessorName + " from " + target.getClass().getName(), exception);
        }
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
