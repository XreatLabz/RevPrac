package io.github.xreatlabz.revprac.bootstrap;

import io.github.xreatlabz.revprac.adapters.paper.PaperConfigSource;
import io.github.xreatlabz.revprac.adapters.paper.PaperLifecycleReporter;
import io.github.xreatlabz.revprac.adapters.paper.arenas.PaperArenaRegistryFiles;
import io.github.xreatlabz.revprac.adapters.paper.arenas.PaperArenaResetAdapter;
import io.github.xreatlabz.revprac.adapters.paper.commands.RevPracAdminCommand;
import io.github.xreatlabz.revprac.adapters.paper.commands.RevPracDuelCommand;
import io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitLoadoutAdapter;
import io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitRegistryFiles;
import io.github.xreatlabz.revprac.adapters.paper.matches.PaperMatchLifecycleListener;
import io.github.xreatlabz.revprac.adapters.paper.matches.PaperMatchPlayerAdapter;
import io.github.xreatlabz.revprac.adapters.paper.matches.PaperMatchTicker;
import io.github.xreatlabz.revprac.adapters.paper.players.PaperPlayerSessionListener;
import io.github.xreatlabz.revprac.adapters.paper.players.PaperPlayerStateAdapter;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryArenaRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryDuelRequestRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryKitRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.application.config.LoadValidatedConfigService;
import io.github.xreatlabz.revprac.application.config.RevPracConfig;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.matches.DuelRequestService;
import io.github.xreatlabz.revprac.application.matches.MatchLifecycleService;
import io.github.xreatlabz.revprac.application.result.Err;
import io.github.xreatlabz.revprac.application.result.Ok;
import io.github.xreatlabz.revprac.application.result.Problem;
import io.github.xreatlabz.revprac.application.result.ProblemCategory;
import io.github.xreatlabz.revprac.application.result.Result;
import io.github.xreatlabz.revprac.domain.matches.MatchEvent;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.ports.matches.MatchRepository;
import io.github.xreatlabz.revprac.ports.lifecycle.LifecycleReporter;
import java.time.Clock;
import java.time.Duration;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

public final class RevPracBootstrap {

    private final LoadValidatedConfigService loadValidatedConfigService;

    public RevPracBootstrap() {
        this(new LoadValidatedConfigService());
    }

    RevPracBootstrap(LoadValidatedConfigService loadValidatedConfigService) {
        this.loadValidatedConfigService = loadValidatedConfigService;
    }

    public Result<BootstrapRuntime> enable(JavaPlugin plugin) {
        plugin.saveDefaultConfig();

        Path configPath = plugin.getDataFolder().toPath().resolve("config.yml");
        PaperConfigSource configSource = new PaperConfigSource(plugin.getConfig(), configPath);
        LifecycleReporter lifecycleReporter = new PaperLifecycleReporter(plugin.getLogger());

        Result<RevPracConfig> configResult = loadValidatedConfigService.load(configSource);
        if (configResult instanceof Err<RevPracConfig> err) {
            handleStartupFailure(plugin, lifecycleReporter, configSource, err.problem());
            return new Err<>(err.problem());
        }

        RevPracConfig config = ((Ok<RevPracConfig>) configResult).value();
        PaperArenaRegistryFiles arenaRegistryFiles = new PaperArenaRegistryFiles(plugin.getDataFolder().toPath());
        PaperKitRegistryFiles kitRegistryFiles = new PaperKitRegistryFiles(plugin.getDataFolder().toPath());
        ArenaRegistryService arenaRegistryService =
                new ArenaRegistryService(new InMemoryArenaRegistryRepository(), new PaperArenaResetAdapter(plugin.getLogger()));
        KitRegistryService kitRegistryService = new KitRegistryService(new InMemoryKitRegistryRepository());
        try {
            arenaRegistryFiles.load().forEach(arenaRegistryService::register);
        } catch (RuntimeException exception) {
            Problem problem = new Problem(
                    "registry.invalid",
                    ProblemCategory.CONFIGURATION,
                    exception.getMessage(),
                    "bootstrap.registries.arenas");
            handleStartupFailure(plugin, lifecycleReporter, configSource, problem);
            return new Err<>(problem);
        }
        try {
            kitRegistryFiles.load().forEach(kitRegistryService::register);
        } catch (RuntimeException exception) {
            Problem problem = new Problem(
                    "registry.invalid",
                    ProblemCategory.CONFIGURATION,
                    exception.getMessage(),
                    "bootstrap.registries.kits");
            handleStartupFailure(plugin, lifecycleReporter, configSource, problem);
            return new Err<>(problem);
        }

        PlayerSessionService playerSessionService = new PlayerSessionService(
                new InMemoryPlayerSessionRepository(),
                new InMemoryPendingRestorationRepository(),
                new PaperPlayerStateAdapter(plugin.getServer()));
        PaperKitLoadoutAdapter kitLoadoutAdapter = new PaperKitLoadoutAdapter();
        MatchRepository matchRepository = new InMemoryMatchRepository();
        MatchRuleset matchRuleset = new MatchRuleset(
                config.matches().countdownTicks(),
                config.matches().maxDurationTicks(),
                config.matches().spectatorsEnabled());
        PaperMatchPlayerAdapter matchPlayerAdapter = new PaperMatchPlayerAdapter(plugin.getServer(), kitLoadoutAdapter);
        Consumer<MatchEvent> eventSink = event -> {
        };
        MatchLifecycleService matchLifecycleService = new MatchLifecycleService(
                matchRepository,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerAdapter,
                matchRuleset,
                eventSink);
        DuelRequestService duelRequestService = new DuelRequestService(
                new InMemoryDuelRequestRepository(),
                matchRepository,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerAdapter,
                matchLifecycleService,
                Clock.systemUTC(),
                Duration.ofSeconds(config.matches().duelRequestExpirySeconds()),
                eventSink);
        PaperPlayerSessionListener playerSessionListener = new PaperPlayerSessionListener(plugin, playerSessionService);
        PaperMatchLifecycleListener matchLifecycleListener =
                new PaperMatchLifecycleListener(matchLifecycleService, matchRepository, matchPlayerAdapter);
        PaperMatchTicker paperMatchTicker =
                new PaperMatchTicker(plugin, matchLifecycleService, matchRepository, matchPlayerAdapter);
        plugin.getServer().getPluginManager().registerEvents(playerSessionListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(matchLifecycleListener, plugin);
        plugin.getServer().getOnlinePlayers().forEach(playerSessionListener::trackPlayerAfterJoin);
        BootstrapRuntime runtime = new BootstrapRuntime(
                config,
                lifecycleReporter,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                arenaRegistryFiles,
                kitRegistryFiles,
                duelRequestService,
                matchLifecycleService,
                paperMatchTicker);
        Objects.requireNonNull(plugin.getCommand("revprac"), "revprac command must be declared in plugin.yml")
                .setExecutor(new RevPracAdminCommand(runtime, kitLoadoutAdapter));
        Objects.requireNonNull(plugin.getCommand("duel"), "duel command must be declared in plugin.yml")
                .setExecutor(new RevPracDuelCommand(plugin.getServer(), duelRequestService, matchLifecycleService));
        paperMatchTicker.start();
        if (config.diagnostics().verboseLifecycleLogs()) {
            lifecycleReporter.info("RevPrac runtime bootstrapped.");
        }
        return new Ok<>(runtime);
    }

    private void handleStartupFailure(
            JavaPlugin plugin,
            LifecycleReporter lifecycleReporter,
            PaperConfigSource configSource,
            Problem problem) {
        lifecycleReporter.startupFailed(problem);

        if (configSource.booleanValueOrDefault("bootstrap.fail-fast-on-enable", true)) {
            throw new IllegalStateException("RevPrac bootstrap failed at " + problem.path() + ": " + problem.message());
        }

        plugin.getServer().getPluginManager().disablePlugin(plugin);
    }
}
