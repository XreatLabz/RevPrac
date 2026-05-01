package io.github.xreatlabz.revprac.adapters.paper.matches;

import io.github.xreatlabz.revprac.application.matches.MatchLifecycleService;
import io.github.xreatlabz.revprac.ports.matches.MatchRepository;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PaperMatchTicker {

    private final Plugin plugin;
    private final MatchLifecycleService matchLifecycleService;
    private final MatchRepository matchRepository;
    private final PaperMatchPlayerAdapter matchPlayerAdapter;
    private BukkitTask task;
    private boolean cancelled;

    public PaperMatchTicker(
            Plugin plugin,
            MatchLifecycleService matchLifecycleService,
            MatchRepository matchRepository,
            PaperMatchPlayerAdapter matchPlayerAdapter) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.matchLifecycleService = Objects.requireNonNull(matchLifecycleService, "matchLifecycleService");
        this.matchRepository = Objects.requireNonNull(matchRepository, "matchRepository");
        this.matchPlayerAdapter = Objects.requireNonNull(matchPlayerAdapter, "matchPlayerAdapter");
    }

    public synchronized void start() {
        if (task != null) {
            return;
        }
        cancelled = false;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (cancelled) {
                return;
            }
            matchLifecycleService.tick();
            matchPlayerAdapter.synchronizeCountdownState(matchRepository.findAll());
        }, 1L, 1L);
    }

    public synchronized void cancel() {
        cancelled = true;
        if (task == null) {
            return;
        }
        task.cancel();
        task = null;
    }
}
