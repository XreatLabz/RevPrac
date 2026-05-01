package io.github.xreatlabz.revprac.adapters.paper.matches;

import io.github.xreatlabz.revprac.application.matches.MatchLifecycleService;
import io.github.xreatlabz.revprac.domain.matches.MatchState;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.matches.MatchRepository;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.entity.Projectile;

public final class PaperMatchLifecycleListener implements Listener {

    private final MatchLifecycleService matchLifecycleService;
    private final MatchRepository matchRepository;
    private final PaperMatchPlayerAdapter matchPlayerAdapter;

    public PaperMatchLifecycleListener(
            MatchLifecycleService matchLifecycleService,
            MatchRepository matchRepository,
            PaperMatchPlayerAdapter matchPlayerAdapter) {
        this.matchLifecycleService = Objects.requireNonNull(matchLifecycleService, "matchLifecycleService");
        this.matchRepository = Objects.requireNonNull(matchRepository, "matchRepository");
        this.matchPlayerAdapter = Objects.requireNonNull(matchPlayerAdapter, "matchPlayerAdapter");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Objects.requireNonNull(event, "event");
        PlayerId playerId = new PlayerId(event.getPlayer().getUniqueId());
        if (!hasActiveMatch(playerId)) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setShouldDropExperience(false);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setNewExp(0);
        event.setNewLevel(0);
        event.setNewTotalExp(0);

        matchLifecycleService.completeByDeath(playerId);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        matchLifecycleService.handleQuit(new PlayerId(event.getPlayer().getUniqueId()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        if (!matchPlayerAdapter.isCountdownFrozen(new PlayerId(event.getPlayer().getUniqueId()))) {
            return;
        }
        event.setTo(event.getFrom());
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (matchPlayerAdapter.isSpectator(new PlayerId(player.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolveDamagingPlayer(event);
        if (attacker == null) {
            return;
        }
        if (matchPlayerAdapter.isSpectator(new PlayerId(attacker.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (matchPlayerAdapter.isSpectator(new PlayerId(event.getPlayer().getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    private boolean hasActiveMatch(PlayerId playerId) {
        return matchRepository.findByPlayer(playerId)
                .filter(match -> match.state() != MatchState.COMPLETED)
                .isPresent();
    }

    private Player resolveDamagingPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
