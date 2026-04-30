package io.github.xreatlabz.revprac.bootstrap;

import io.github.xreatlabz.revprac.adapters.paper.PaperConfigSource;
import io.github.xreatlabz.revprac.adapters.paper.PaperLifecycleReporter;
import io.github.xreatlabz.revprac.adapters.paper.players.PaperPlayerSessionListener;
import io.github.xreatlabz.revprac.adapters.paper.players.PaperPlayerStateAdapter;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.application.config.LoadValidatedConfigService;
import io.github.xreatlabz.revprac.application.config.RevPracConfig;
import io.github.xreatlabz.revprac.application.result.Err;
import io.github.xreatlabz.revprac.application.result.Ok;
import io.github.xreatlabz.revprac.application.result.Problem;
import io.github.xreatlabz.revprac.application.result.Result;
import io.github.xreatlabz.revprac.ports.lifecycle.LifecycleReporter;
import java.nio.file.Path;
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
        PlayerSessionService playerSessionService = new PlayerSessionService(
                new InMemoryPlayerSessionRepository(),
                new InMemoryPendingRestorationRepository(),
                new PaperPlayerStateAdapter(plugin.getServer()));
        PaperPlayerSessionListener playerSessionListener = new PaperPlayerSessionListener(plugin, playerSessionService);
        plugin.getServer().getPluginManager().registerEvents(playerSessionListener, plugin);
        plugin.getServer().getOnlinePlayers().forEach(playerSessionListener::trackPlayerAfterJoin);
        if (config.diagnostics().verboseLifecycleLogs()) {
            lifecycleReporter.info("RevPrac runtime bootstrapped.");
        }
        return new Ok<>(new BootstrapRuntime(config, lifecycleReporter, playerSessionService));
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
