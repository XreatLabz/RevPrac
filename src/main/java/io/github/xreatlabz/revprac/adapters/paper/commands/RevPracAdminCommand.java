package io.github.xreatlabz.revprac.adapters.paper.commands;

import io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitLoadoutAdapter;
import io.github.xreatlabz.revprac.bootstrap.BootstrapRuntime;
import io.github.xreatlabz.revprac.domain.arenas.ArenaCuboid;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import io.github.xreatlabz.revprac.application.integrations.IntegrationStatus;
import io.github.xreatlabz.revprac.application.operations.AuditEntry;
import io.github.xreatlabz.revprac.application.operations.OperationalMetricsSnapshot;
import io.github.xreatlabz.revprac.application.operations.RegistryReloadResult;
import io.github.xreatlabz.revprac.application.operations.StaffDiagnostics;
import io.github.xreatlabz.revprac.application.operations.StaffOperationsService;
import io.github.xreatlabz.revprac.domain.seasons.Season;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RevPracAdminCommand implements CommandExecutor {

    static final String ADMIN_PERMISSION = "revprac.admin";

    private final BootstrapRuntime runtime;
    private final PaperKitLoadoutAdapter kitLoadoutAdapter;
    private final ReentrantLock persistenceLock;
    private final PersistenceHooks persistenceHooks;

    public RevPracAdminCommand(BootstrapRuntime runtime, PaperKitLoadoutAdapter kitLoadoutAdapter) {
        this(runtime, kitLoadoutAdapter, PersistenceHooks.NO_OP);
    }

    RevPracAdminCommand(BootstrapRuntime runtime, PaperKitLoadoutAdapter kitLoadoutAdapter, PersistenceHooks persistenceHooks) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.kitLoadoutAdapter = Objects.requireNonNull(kitLoadoutAdapter, "kitLoadoutAdapter");
        this.persistenceHooks = Objects.requireNonNull(persistenceHooks, "persistenceHooks");
        this.persistenceLock = new ReentrantLock();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(args, "args");

        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        try {
            if (args.length >= 2 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("create")) {
                return handleArenaCreate(sender, args);
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("kit") && args[1].equalsIgnoreCase("save")) {
                return handleKitSave(sender, args);
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("status")) {
                return handleStatus(sender, args);
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("metrics")) {
                return handleMetrics(sender, args);
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("integrations")) {
                return handleIntegrations(sender, args);
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("audit")) {
                return handleAudit(sender, args);
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                return handleReload(sender, args);
            }
            if (args.length >= 1 && (args[0].equalsIgnoreCase("season") || args[0].equalsIgnoreCase("seasons"))) {
                return handleSeason(sender, args);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            sender.sendMessage(exception.getMessage());
            return true;
        }

        sender.sendMessage("Usage: /revprac <arena|kit|status|reload|season|metrics|integrations|audit> ...");
        return true;
    }

    private boolean handleArenaCreate(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage("Usage: /revprac arena create <id> <radius>");
            return true;
        }

        Player player = requirePlayer(sender, "/revprac arena create");
        int radius = parseRadius(args[3]);
        Location location = player.getLocation();
        String worldKey = player.getWorld().getKey().asString();
        ArenaSpawnPoint spawn = new ArenaSpawnPoint(
                worldKey,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
        ArenaDefinition arenaDefinition = new ArenaDefinition(
                new ArenaId(args[2]),
                args[2],
                new ArenaCuboid(
                        worldKey,
                        location.getBlockX() - radius,
                        location.getBlockY() - 8,
                        location.getBlockZ() - radius,
                        location.getBlockX() + radius,
                        location.getBlockY() + 8,
                        location.getBlockZ() + radius),
                spawn,
                spawn,
                true);

        persistenceLock.lock();
        try {
            List<ArenaDefinition> stagedArenas = stageArenaDefinitions(arenaDefinition);
            persistenceHooks.afterArenaStage(arenaDefinition, stagedArenas);
            saveArenas(stagedArenas);
            runtime.arenaRegistryService().register(arenaDefinition);
        } finally {
            persistenceLock.unlock();
        }
        sender.sendMessage("Saved arena " + arenaDefinition.id().value() + ".");
        return true;
    }

    private boolean handleKitSave(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("Usage: /revprac kit save <id>");
            return true;
        }

        Player player = requirePlayer(sender, "/revprac kit save");
        KitDefinition kitDefinition = kitLoadoutAdapter.capture(
                player,
                new KitId(args[2]),
                args[2],
                new KitRules(false, false, true, false),
                true);

        persistenceLock.lock();
        try {
            List<KitDefinition> stagedKits = stageKitDefinitions(kitDefinition);
            persistenceHooks.afterKitStage(kitDefinition, stagedKits);
            saveKits(stagedKits);
            runtime.kitRegistryService().register(kitDefinition);
        } finally {
            persistenceLock.unlock();
        }
        sender.sendMessage("Saved kit " + kitDefinition.id().value() + ".");
        return true;
    }

    private boolean handleStatus(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /revprac status");
            return true;
        }

        StaffDiagnostics diagnostics = operations().diagnostics();
        sender.sendMessage("RevPrac status: backend="
                + diagnostics.storageBackend()
                + " season="
                + diagnostics.activeSeasonId()
                + " arenas="
                + diagnostics.arenaCount()
                + " kits="
                + diagnostics.kitCount()
                + " reservations="
                + diagnostics.arenaReservationCount()
                + " queues="
                + diagnostics.activeQueueTicketCount()
                + " matches="
                + diagnostics.activeMatchCount()
                + " pending-duels="
                + diagnostics.pendingDuelRequestCount()
                + " events="
                + diagnostics.metrics().publishedEvents());
        return true;
    }

    private boolean handleMetrics(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /revprac metrics");
            return true;
        }

        OperationalMetricsSnapshot metrics = operations().diagnostics().metrics();
        sender.sendMessage("Metrics: events="
                + metrics.publishedEvents()
                + " duel-requests="
                + metrics.duelRequestsCreated()
                + " completed-matches="
                + metrics.matchesCompleted()
                + " torn-down-matches="
                + metrics.matchesTornDown());
        return true;
    }

    private boolean handleIntegrations(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /revprac integrations");
            return true;
        }

        List<String> statuses = operations().diagnostics().integrations().stream()
                .map(RevPracAdminCommand::formatIntegration)
                .toList();
        sender.sendMessage("Integrations: " + String.join(", ", statuses));
        return true;
    }

    private boolean handleAudit(CommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage("Usage: /revprac audit [limit]");
            return true;
        }

        int limit = args.length == 2 ? parseLimit(args[1]) : 5;
        List<AuditEntry> entries = operations().recentAudit(limit);
        if (entries.isEmpty()) {
            sender.sendMessage("Audit: none");
            return true;
        }
        sender.sendMessage("Audit: " + entries.stream()
                .map(entry -> entry.action() + " by " + entry.actor() + " at " + entry.occurredAt())
                .toList());
        return true;
    }

    private boolean handleReload(CommandSender sender, String[] args) {
        if (args.length != 2 || !args[1].equalsIgnoreCase("registries")) {
            sender.sendMessage("Usage: /revprac reload registries");
            return true;
        }

        RegistryReloadResult result = operations().reloadRegistries(actor(sender));
        sender.sendMessage("Reloaded registries: arenas=" + result.arenaCount() + " kits=" + result.kitCount() + ".");
        return true;
    }

    private boolean handleSeason(CommandSender sender, String[] args) {
        if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
            List<String> seasons = operations().seasons().stream()
                    .map(RevPracAdminCommand::formatSeason)
                    .toList();
            sender.sendMessage("Seasons: " + String.join(", ", seasons));
            return true;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("create")) {
            Season created = operations().createSeason(actor(sender), args[2]);
            sender.sendMessage("Created season " + created.id().value() + ".");
            return true;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("activate")) {
            Season activated = operations().activateSeason(actor(sender), args[2]);
            sender.sendMessage("Activated season " + activated.id().value() + ".");
            return true;
        }
        sender.sendMessage("Usage: /revprac season <list|create <id>|activate <id>>");
        return true;
    }

    private Player requirePlayer(CommandSender sender, String usagePrefix) {
        if (sender instanceof Player player) {
            return player;
        }
        throw new IllegalArgumentException("Only players can use " + usagePrefix + ".");
    }

    private static int parseRadius(String rawRadius) {
        final int radius;
        try {
            radius = Integer.parseInt(rawRadius);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Radius must be an integer from 1 to 256.", exception);
        }

        if (radius < 1 || radius > 256) {
            throw new IllegalArgumentException("Radius must be an integer from 1 to 256.");
        }
        return radius;
    }

    private static int parseLimit(String rawLimit) {
        final int limit;
        try {
            limit = Integer.parseInt(rawLimit);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Limit must be an integer from 1 to 100.", exception);
        }

        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be an integer from 1 to 100.");
        }
        return limit;
    }

    private StaffOperationsService operations() {
        StaffOperationsService operations = runtime.staffOperationsService();
        if (operations == null) {
            throw new IllegalStateException("staff operations are not available");
        }
        return operations;
    }

    private static String actor(CommandSender sender) {
        return sender.getName();
    }

    private static String formatIntegration(IntegrationStatus status) {
        return status.type().name() + "/" + status.pluginName() + "=" + (status.present() ? "present" : "absent");
    }

    private static String formatSeason(Season season) {
        return season.id().value() + (season.active() ? "(active)" : "");
    }

    private List<ArenaDefinition> stageArenaDefinitions(ArenaDefinition arenaDefinition) {
        List<ArenaDefinition> currentArenas = runtime.arenaRegistryService().arenas();
        if (currentArenas.stream().anyMatch(existingArena -> existingArena.id().equals(arenaDefinition.id()))) {
            throw new IllegalArgumentException("Arena already exists: " + arenaDefinition.id().value());
        }

        List<ArenaDefinition> stagedArenas = new ArrayList<>(currentArenas);
        stagedArenas.add(arenaDefinition);
        return List.copyOf(stagedArenas);
    }

    private List<KitDefinition> stageKitDefinitions(KitDefinition kitDefinition) {
        List<KitDefinition> currentKits = runtime.kitRegistryService().kits();
        if (currentKits.stream().anyMatch(existingKit -> existingKit.id().equals(kitDefinition.id()))) {
            throw new IllegalArgumentException("Kit already exists: " + kitDefinition.id().value());
        }

        List<KitDefinition> stagedKits = new ArrayList<>(currentKits);
        stagedKits.add(kitDefinition);
        return List.copyOf(stagedKits);
    }

    private void saveArenas(List<ArenaDefinition> stagedArenas) {
        try {
            runtime.arenaRegistryFiles().save(stagedArenas);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save arenas.yml.", exception);
        }
    }

    private void saveKits(List<KitDefinition> stagedKits) {
        try {
            runtime.kitRegistryFiles().save(stagedKits);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save kits.yml.", exception);
        }
    }

    interface PersistenceHooks {

        PersistenceHooks NO_OP = new PersistenceHooks() {};

        default void afterArenaStage(ArenaDefinition arenaDefinition, List<ArenaDefinition> stagedArenas) {}

        default void afterKitStage(KitDefinition kitDefinition, List<KitDefinition> stagedKits) {}
    }
}
