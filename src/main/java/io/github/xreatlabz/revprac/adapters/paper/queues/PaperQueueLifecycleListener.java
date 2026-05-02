package io.github.xreatlabz.revprac.adapters.paper.queues;

import io.github.xreatlabz.revprac.application.queues.QueueService;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PaperQueueLifecycleListener implements Listener {

    private final QueueService queueService;

    public PaperQueueLifecycleListener(QueueService queueService) {
        this.queueService = Objects.requireNonNull(queueService, "queueService");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        queueService.handleQuit(new PlayerId(event.getPlayer().getUniqueId()));
    }
}
