package io.github.xreatlabz.revprac.adapters.paper.commands;

import io.github.xreatlabz.revprac.application.queues.QueueService;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.queues.QueueMode;
import io.github.xreatlabz.revprac.domain.queues.QueueTicket;
import java.util.Locale;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RevPracQueueCommand implements CommandExecutor {

    static final String QUEUE_PERMISSION = "revprac.queue";
    static final String USAGE = "Usage: /queue join <ranked|unranked> <kit>|leave|status";

    private final QueueService queueService;
    private final LongSupplier currentTickSupplier;

    public RevPracQueueCommand(QueueService queueService, LongSupplier currentTickSupplier) {
        this.queueService = Objects.requireNonNull(queueService, "queueService");
        this.currentTickSupplier = Objects.requireNonNull(currentTickSupplier, "currentTickSupplier");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(args, "args");

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /queue.");
            return true;
        }
        if (!player.hasPermission(QUEUE_PERMISSION)) {
            player.sendMessage("You do not have permission to use this command.");
            return true;
        }

        try {
            if (args.length == 0) {
                player.sendMessage(USAGE);
                return true;
            }

            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "join" -> handleJoin(player, args);
                case "leave" -> handleLeave(player, args);
                case "status" -> handleStatus(player, args);
                default -> sendUsage(player);
            };
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(exception.getMessage());
            return true;
        }
    }

    private boolean handleJoin(Player player, String[] args) {
        if (args.length != 3) {
            return sendUsage(player);
        }
        QueueMode mode = parseMode(args[1]);
        if (mode == null) {
            return sendUsage(player);
        }

        queueService.join(
                new PlayerId(player.getUniqueId()),
                mode,
                new KitId(args[2]),
                currentTickSupplier.getAsLong());
        player.sendMessage("Joined " + mode.name().toLowerCase(Locale.ROOT) + " queue for kit " + args[2] + ".");
        return true;
    }

    private boolean handleLeave(Player player, String[] args) {
        if (args.length != 1) {
            return sendUsage(player);
        }
        queueService.leave(new PlayerId(player.getUniqueId()));
        player.sendMessage("Left queue.");
        return true;
    }

    private boolean handleStatus(Player player, String[] args) {
        if (args.length != 1) {
            return sendUsage(player);
        }

        QueueTicket ticket = queueService.ticket(new PlayerId(player.getUniqueId())).orElse(null);
        if (ticket == null) {
            player.sendMessage("You are not queued.");
            return true;
        }

        player.sendMessage("Queued for "
                + ticket.key().mode().name().toLowerCase(Locale.ROOT)
                + " "
                + ticket.key().kitId().value()
                + ".");
        return true;
    }

    private boolean sendUsage(Player player) {
        player.sendMessage(USAGE);
        return true;
    }

    private QueueMode parseMode(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "ranked" -> QueueMode.RANKED;
            case "unranked" -> QueueMode.UNRANKED;
            default -> null;
        };
    }
}
