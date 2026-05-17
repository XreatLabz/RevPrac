package io.github.xreatlabz.revprac.adapters.paper.commands;

import io.github.xreatlabz.revprac.application.players.PlayerDirectoryEntry;
import io.github.xreatlabz.revprac.application.players.PlayerDirectoryService;
import io.github.xreatlabz.revprac.application.players.PlayerKitSummaryView;
import io.github.xreatlabz.revprac.application.players.PlayerMatchHistoryLineItem;
import io.github.xreatlabz.revprac.application.players.PlayerMatchHistoryPage;
import io.github.xreatlabz.revprac.application.players.PlayerRecordBundle;
import io.github.xreatlabz.revprac.application.players.PlayerRecordQueryService;
import io.github.xreatlabz.revprac.application.players.PlayerRecordTransferService;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class RevPracRecordsCommand implements CommandExecutor {

    public static final String RECORDS_PERMISSION = "revprac.records";
    public static final String RECORDS_LOOKUP_PERMISSION = "revprac.records.lookup";
    public static final String RECORDS_TRANSFER_PERMISSION = "revprac.records.transfer";
    static final String USAGE =
            "Usage: /records summary <player> <kit>|history <player> [page]|export <player>|import <file>";
    private static final int PAGE_SIZE = Math.min(5, PlayerRecordQueryService.MAX_PAGE_SIZE);

    private final PlayerDirectoryService playerDirectoryService;
    private final PlayerRecordQueryService playerRecordQueryService;
    private final PlayerRecordTransferService playerRecordTransferService;

    public RevPracRecordsCommand(
            PlayerDirectoryService playerDirectoryService,
            PlayerRecordQueryService playerRecordQueryService,
            PlayerRecordTransferService playerRecordTransferService) {
        this.playerDirectoryService = Objects.requireNonNull(playerDirectoryService, "playerDirectoryService");
        this.playerRecordQueryService = Objects.requireNonNull(playerRecordQueryService, "playerRecordQueryService");
        this.playerRecordTransferService = Objects.requireNonNull(playerRecordTransferService, "playerRecordTransferService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(args, "args");

        if (!sender.hasPermission(RECORDS_PERMISSION)) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        try {
            if (args.length == 0) {
                sender.sendMessage(USAGE);
                return true;
            }

            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "summary" -> handleSummary(sender, args);
                case "history" -> handleHistory(sender, args);
                case "export" -> handleExport(sender, args);
                case "import" -> handleImport(sender, args);
                default -> sendUsage(sender);
            };
        } catch (IllegalArgumentException | IllegalStateException exception) {
            sender.sendMessage(exception.getMessage());
            return true;
        }
    }

    private boolean handleSummary(CommandSender sender, String[] args) {
        requirePermission(sender, RECORDS_LOOKUP_PERMISSION);
        if (args.length != 3) {
            return sendUsage(sender);
        }

        PlayerDirectoryEntry entry = playerDirectoryService.resolve(args[1]);
        PlayerKitSummaryView summary =
                playerRecordQueryService.summary(entry.playerId(), parseKitId(args[2]));
        StringBuilder message = new StringBuilder()
                .append(entry.displayLabel())
                .append(": ")
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
        sender.sendMessage(message.toString());
        return true;
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        requirePermission(sender, RECORDS_LOOKUP_PERMISSION);
        if (args.length < 2 || args.length > 3) {
            return sendUsage(sender);
        }

        PlayerDirectoryEntry entry = playerDirectoryService.resolve(args[1]);
        int page = args.length == 3 ? parsePositivePage(args[2]) : 1;
        PlayerMatchHistoryPage history = playerRecordQueryService.recentHistory(entry.playerId(), page, PAGE_SIZE);
        if (history.items().isEmpty()) {
            sender.sendMessage("No match history found.");
            return true;
        }
        for (PlayerMatchHistoryLineItem item : history.items()) {
            sender.sendMessage(formatHistoryLine(item));
        }
        return true;
    }

    private boolean handleExport(CommandSender sender, String[] args) {
        requirePermission(sender, RECORDS_TRANSFER_PERMISSION);
        if (args.length != 2) {
            return sendUsage(sender);
        }

        PlayerDirectoryEntry entry = playerDirectoryService.resolve(args[1]);
        String relativePath = playerRecordTransferService.export(entry.playerId());
        sender.sendMessage("Exported player records for " + entry.displayLabel() + " to " + relativePath);
        return true;
    }

    private boolean handleImport(CommandSender sender, String[] args) {
        requirePermission(sender, RECORDS_TRANSFER_PERMISSION);
        if (args.length != 2) {
            return sendUsage(sender);
        }

        PlayerRecordBundle bundle = playerRecordTransferService.importFromFile(args[1]);
        PlayerDirectoryEntry entry = new PlayerDirectoryEntry(
                bundle.profile().playerId(),
                bundle.profile().lastKnownName().orElse(bundle.profile().playerId().value().toString()));
        sender.sendMessage("Imported player records for " + entry.displayLabel() + " from imports/player-records/" + args[1]);
        return true;
    }

    private static void requirePermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            throw new IllegalArgumentException("You do not have permission to use this command.");
        }
    }

    private static boolean sendUsage(CommandSender sender) {
        sender.sendMessage(USAGE);
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
