package io.github.xreatlabz.revprac.adapters.paper.players;

import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class PaperPlayerSessionListener implements Listener {

    private final Plugin plugin;
    private final PlayerSessionService playerSessionService;

    public PaperPlayerSessionListener(Plugin plugin, PlayerSessionService playerSessionService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerSessionService = Objects.requireNonNull(playerSessionService, "playerSessionService");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        trackPlayerAfterJoin(event.getPlayer());
    }

    public void trackPlayerAfterJoin(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerId playerId = new PlayerId(player.getUniqueId());
        player.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                playerSessionService.join(playerId);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerSessionService.quit(new PlayerId(event.getPlayer().getUniqueId()));
    }
}
