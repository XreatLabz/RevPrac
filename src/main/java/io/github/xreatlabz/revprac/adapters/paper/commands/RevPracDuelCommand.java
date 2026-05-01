package io.github.xreatlabz.revprac.adapters.paper.commands;

import io.github.xreatlabz.revprac.application.matches.DuelRequestService;
import io.github.xreatlabz.revprac.application.matches.MatchLifecycleService;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.Objects;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RevPracDuelCommand implements CommandExecutor {

    static final String DUEL_PERMISSION = "revprac.duel";
    private static final String REQUEST_USAGE =
            "Usage: /duel <player> <arena> <kit> or /duel request <player> <arena> <kit>";

    private final Server server;
    private final DuelRequestService duelRequestService;
    private final MatchLifecycleService matchLifecycleService;

    public RevPracDuelCommand(
            Server server, DuelRequestService duelRequestService, MatchLifecycleService matchLifecycleService) {
        this.server = Objects.requireNonNull(server, "server");
        this.duelRequestService = Objects.requireNonNull(duelRequestService, "duelRequestService");
        this.matchLifecycleService = Objects.requireNonNull(matchLifecycleService, "matchLifecycleService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(args, "args");

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /duel.");
            return true;
        }
        if (!player.hasPermission(DUEL_PERMISSION)) {
            player.sendMessage("You do not have permission to use this command.");
            return true;
        }

        try {
            if (args.length == 0) {
                player.sendMessage(REQUEST_USAGE);
                return true;
            }

            return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "request" -> handleExplicitRequest(player, args);
                case "accept" -> handleAccept(player, args);
                case "deny", "decline" -> handleDeny(player, args);
                case "cancel" -> handleCancel(player, args);
                case "spectate" -> handleSpectate(player, args);
                case "forfeit" -> handleForfeit(player, args);
                default -> handleRequest(player, args);
            };
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(exception.getMessage());
            return true;
        }
    }

    private boolean handleRequest(Player sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(REQUEST_USAGE);
            return true;
        }

        Player target = requireOnlinePlayer(args[0]);
        duelRequestService.request(
                new PlayerId(sender.getUniqueId()),
                new PlayerId(target.getUniqueId()),
                new ArenaId(args[1]),
                new KitId(args[2]));
        sender.sendMessage("Sent duel request to " + target.getName() + ".");
        return true;
    }

    private boolean handleExplicitRequest(Player sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage("Usage: /duel request <player> <arena> <kit>");
            return true;
        }

        return handleRequest(sender, new String[] {args[1], args[2], args[3]});
    }

    private boolean handleAccept(Player sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /duel accept <player>");
            return true;
        }

        Player requester = requireOnlinePlayer(args[1]);
        duelRequestService.accept(new PlayerId(requester.getUniqueId()), new PlayerId(sender.getUniqueId()));
        sender.sendMessage("Accepted duel from " + requester.getName() + ".");
        return true;
    }

    private boolean handleDeny(Player sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /duel deny <player>");
            return true;
        }

        Player requester = requireOnlinePlayer(args[1]);
        duelRequestService.decline(new PlayerId(requester.getUniqueId()), new PlayerId(sender.getUniqueId()));
        sender.sendMessage("Declined duel from " + requester.getName() + ".");
        return true;
    }

    private boolean handleCancel(Player sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /duel cancel <player>");
            return true;
        }

        Player target = requireOnlinePlayer(args[1]);
        duelRequestService.cancel(new PlayerId(sender.getUniqueId()), new PlayerId(target.getUniqueId()));
        sender.sendMessage("Cancelled duel with " + target.getName() + ".");
        return true;
    }

    private boolean handleSpectate(Player sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /duel spectate <player>");
            return true;
        }

        Player target = requireOnlinePlayer(args[1]);
        matchLifecycleService.spectate(new PlayerId(sender.getUniqueId()), new PlayerId(target.getUniqueId()));
        sender.sendMessage("Spectating " + target.getName() + ".");
        return true;
    }

    private boolean handleForfeit(Player sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /duel forfeit");
            return true;
        }

        matchLifecycleService.forfeit(new PlayerId(sender.getUniqueId()));
        sender.sendMessage("Forfeited duel.");
        return true;
    }

    private Player requireOnlinePlayer(String name) {
        Player player = server.getPlayerExact(name);
        if (player == null || !player.isOnline()) {
            throw new IllegalArgumentException("Player not found: " + name + ".");
        }
        return player;
    }
}
