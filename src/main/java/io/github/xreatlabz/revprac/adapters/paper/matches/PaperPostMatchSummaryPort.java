package io.github.xreatlabz.revprac.adapters.paper.matches;

import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.ports.matches.PostMatchSummaryPort;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Server;
import org.bukkit.entity.Player;

public final class PaperPostMatchSummaryPort implements PostMatchSummaryPort {

    private final Server server;

    public PaperPostMatchSummaryPort(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public Optional<String> playerName(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Player player = server.getPlayer(playerId.value());
        if (player == null || !player.isOnline()) {
            return Optional.empty();
        }
        return Optional.ofNullable(player.getName());
    }

    @Override
    public void send(PlayerId playerId, String message) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(message, "message");
        Player player = server.getPlayer(playerId.value());
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendMessage(message);
    }
}
