package io.github.xreatlabz.revprac.adapters.paper.queues;

import io.github.xreatlabz.revprac.application.queues.QueueMatchmakingService;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PaperQueueTicker {

    private final JavaPlugin plugin;
    private final QueueMatchmakingService queueMatchmakingService;
    private final long periodTicks;
    private BukkitTask task;
    private boolean cancelled;

    public PaperQueueTicker(JavaPlugin plugin, QueueMatchmakingService queueMatchmakingService, long periodTicks) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.queueMatchmakingService = Objects.requireNonNull(queueMatchmakingService, "queueMatchmakingService");
        if (periodTicks <= 0L) {
            throw new IllegalArgumentException("periodTicks must be positive");
        }
        this.periodTicks = periodTicks;
    }

    public synchronized void start() {
        if (task != null) {
            return;
        }
        cancelled = false;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long tick;
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                tick = plugin.getServer().getCurrentTick();
            }
            queueMatchmakingService.tick(tick);
        }, periodTicks, periodTicks);
    }

    public synchronized long currentTick() {
        return plugin.getServer().getCurrentTick();
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
