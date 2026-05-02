package io.github.xreatlabz.revprac.adapters.paper.players;

import io.github.xreatlabz.revprac.application.players.PlayerProfileService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.time.Clock;
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
    private final PlayerProfileService playerProfileService;
    private final Clock clock;

    public PaperPlayerSessionListener(Plugin plugin, PlayerSessionService playerSessionService) {
        this(plugin, playerSessionService, null, Clock.systemUTC());
    }

    public PaperPlayerSessionListener(
            Plugin plugin,
            PlayerSessionService playerSessionService,
            PlayerProfileService playerProfileService,
            Clock clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerSessionService = Objects.requireNonNull(playerSessionService, "playerSessionService");
        this.playerProfileService = playerProfileService;
        this.clock = Objects.requireNonNull(clock, "clock");
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
                if (playerProfileService != null) {
                    playerProfileService.touch(playerId, player.getName(), clock.instant());
                }
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerSessionService.quit(new PlayerId(event.getPlayer().getUniqueId()));
    }
}
