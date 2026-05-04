package io.github.xreatlabz.revprac.adapters.paper.commands;

import io.github.xreatlabz.revprac.application.players.PlayerKitSummaryView;
import io.github.xreatlabz.revprac.application.players.PlayerMatchHistoryLineItem;
import io.github.xreatlabz.revprac.application.players.PlayerMatchHistoryPage;
import io.github.xreatlabz.revprac.application.players.PlayerRecordQueryService;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RevPracStatsCommand implements CommandExecutor {

    static final String STATS_PERMISSION = "revprac.stats";
    static final String USAGE = "Usage: /stats summary <kit>|history [page]";
    private static final int PAGE_SIZE = Math.min(5, PlayerRecordQueryService.MAX_PAGE_SIZE);

    private final PlayerRecordQueryService playerRecordQueryService;

    public RevPracStatsCommand(PlayerRecordQueryService playerRecordQueryService) {
        this.playerRecordQueryService = Objects.requireNonNull(playerRecordQueryService, "playerRecordQueryService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(args, "args");

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /stats.");
            return true;
        }
        if (!player.hasPermission(STATS_PERMISSION)) {
            player.sendMessage("You do not have permission to use this command.");
            return true;
        }

        try {
            if (args.length == 0) {
                player.sendMessage(USAGE);
                return true;
            }

            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "summary" -> handleSummary(player, args);
                case "history" -> handleHistory(player, args);
                default -> sendUsage(player);
            };
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(exception.getMessage());
            return true;
        }
    }

    private boolean handleSummary(Player player, String[] args) {
        if (args.length != 2) {
            return sendUsage(player);
        }

        PlayerKitSummaryView summary =
                playerRecordQueryService.summary(new PlayerId(player.getUniqueId()), parseKitId(args[1]));
        StringBuilder message = new StringBuilder()
                .append(summary.displayName())
                .append(" (")
                .append(summary.kitId().value())
                .append("): matches=")
                .append(summary.matchesPlayed())
                .append(" wins=")
                .append(summary.wins())
                .append(" losses=")
                .append(summary.losses());
        summary.rating().ifPresent(rating -> message.append(" rating=").append(rating.rating()));
        player.sendMessage(message.toString());
        return true;
    }

    private boolean handleHistory(Player player, String[] args) {
        if (args.length > 2) {
            return sendUsage(player);
        }

        int page = args.length == 2 ? parsePositivePage(args[1]) : 1;
        PlayerMatchHistoryPage history =
                playerRecordQueryService.recentHistory(new PlayerId(player.getUniqueId()), page, PAGE_SIZE);
        if (history.items().isEmpty()) {
            player.sendMessage("No match history found.");
            return true;
        }

        for (PlayerMatchHistoryLineItem item : history.items()) {
            player.sendMessage(formatHistoryLine(item));
        }
        return true;
    }

    private boolean sendUsage(Player player) {
        player.sendMessage(USAGE);
        return true;
    }

    private static KitId parseKitId(String value) {
        try {
            return new KitId(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown kit: " + value);
        }
    }

    private static int parsePositivePage(String value) {
        final int page;
        try {
            page = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(PlayerRecordQueryService.HISTORY_PAGE_RANGE_MESSAGE);
        }
        if (page < 1 || page > PlayerRecordQueryService.MAX_HISTORY_PAGE) {
            throw new IllegalArgumentException(PlayerRecordQueryService.HISTORY_PAGE_RANGE_MESSAGE);
        }
        return page;
    }

    private static String formatHistoryLine(PlayerMatchHistoryLineItem item) {
        String result = item.won()
                .map(won -> won ? "win" : "loss")
                .orElse("draw");
        return item.completedAt()
                + " kit="
                + item.kitId().value()
                + " opponent="
                + item.opponentName()
                + " result="
                + result
                + " origin="
                + item.origin().name().toLowerCase(Locale.ROOT)
                + " end="
                + item.endReason().name().toLowerCase(Locale.ROOT);
    }
}
