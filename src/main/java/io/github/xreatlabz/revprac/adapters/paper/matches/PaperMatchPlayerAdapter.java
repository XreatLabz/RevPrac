package io.github.xreatlabz.revprac.adapters.paper.matches;

import io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitLoadoutAdapter;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchState;
import io.github.xreatlabz.revprac.domain.matches.MatchSide;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.matches.MatchPlayerPort;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public final class PaperMatchPlayerAdapter implements MatchPlayerPort {

    private final Server server;
    private final PaperKitLoadoutAdapter kitLoadoutAdapter;
    private final Set<PlayerId> countdownFrozenPlayers = ConcurrentHashMap.newKeySet();
    private final Set<PlayerId> spectators = ConcurrentHashMap.newKeySet();

    public PaperMatchPlayerAdapter(Server server, PaperKitLoadoutAdapter kitLoadoutAdapter) {
        this.server = Objects.requireNonNull(server, "server");
        this.kitLoadoutAdapter = Objects.requireNonNull(kitLoadoutAdapter, "kitLoadoutAdapter");
    }

    @Override
    public boolean isOnline(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Player player = server.getPlayer(playerId.value());
        return player != null && player.isOnline();
    }

    @Override
    public void prepareCombatant(
            PlayerId playerId,
            Match match,
            MatchSide side,
            ArenaDefinition arenaDefinition,
            KitDefinition kitDefinition) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(arenaDefinition, "arenaDefinition");
        Objects.requireNonNull(kitDefinition, "kitDefinition");

        Player player = requireOnlinePlayer(playerId);
        teleport(player, resolveCombatantLocation(arenaDefinition, side));
        kitLoadoutAdapter.apply(player, kitDefinition);
        configureCombatantState(player);

        spectators.remove(playerId);
        if (match.state() == MatchState.COUNTDOWN) {
            countdownFrozenPlayers.add(playerId);
        } else {
            countdownFrozenPlayers.remove(playerId);
        }
    }

    @Override
    public void prepareSpectator(PlayerId playerId, Match match, ArenaDefinition arenaDefinition) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(arenaDefinition, "arenaDefinition");

        Player player = requireOnlinePlayer(playerId);
        teleport(player, resolveSpectatorLocation(arenaDefinition));
        player.closeInventory();
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFallDistance(0.0f);

        countdownFrozenPlayers.remove(playerId);
        spectators.add(playerId);
    }

    @Override
    public void clearMatchState(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        countdownFrozenPlayers.remove(playerId);
        spectators.remove(playerId);

        Player player = server.getPlayer(playerId.value());
        if (player == null || !player.isOnline()) {
            return;
        }

        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFallDistance(0.0f);
    }

    boolean isCountdownFrozen(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return countdownFrozenPlayers.contains(playerId);
    }

    boolean isSpectator(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return spectators.contains(playerId);
    }

    void synchronizeCountdownState(Collection<Match> matches) {
        Objects.requireNonNull(matches, "matches");
        Set<PlayerId> stillFrozen = new HashSet<>();
        for (Match match : matches) {
            if (match.state() != MatchState.COUNTDOWN) {
                continue;
            }
            stillFrozen.add(match.participants().playerOne());
            stillFrozen.add(match.participants().playerTwo());
        }
        countdownFrozenPlayers.retainAll(stillFrozen);
    }

    private Location resolveCombatantLocation(ArenaDefinition arenaDefinition, MatchSide side) {
        return switch (side) {
            case ONE -> toLocation(arenaDefinition.spawnOne());
            case TWO -> toLocation(arenaDefinition.spawnTwo());
        };
    }

    private Location resolveSpectatorLocation(ArenaDefinition arenaDefinition) {
        return toLocation(arenaDefinition.spawnOne());
    }

    private Location toLocation(ArenaSpawnPoint spawnPoint) {
        return new Location(
                resolveWorld(spawnPoint.worldKey()),
                spawnPoint.x(),
                spawnPoint.y(),
                spawnPoint.z(),
                spawnPoint.yaw(),
                spawnPoint.pitch());
    }

    private World resolveWorld(String worldKey) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(worldKey);
        if (namespacedKey == null) {
            throw new IllegalArgumentException("Invalid world key: " + worldKey);
        }
        World world = server.getWorld(namespacedKey);
        if (world == null) {
            throw new IllegalStateException("World is not loaded: " + worldKey);
        }
        return world;
    }

    private Player requireOnlinePlayer(PlayerId playerId) {
        Player player = server.getPlayer(playerId.value());
        if (player == null || !player.isOnline()) {
            throw new IllegalStateException("Player is not currently online: " + playerId.value());
        }
        return player;
    }

    private void teleport(Player player, Location location) {
        if (!player.teleport(location)) {
            throw new IllegalStateException("Failed to teleport player " + player.getUniqueId() + " for match preparation");
        }
    }

    private void configureCombatantState(Player player) {
        player.closeInventory();
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExp(0.0f);
        player.setLevel(0);
    }
}
