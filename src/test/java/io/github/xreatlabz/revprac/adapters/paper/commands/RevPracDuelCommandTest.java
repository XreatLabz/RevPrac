package io.github.xreatlabz.revprac.adapters.paper.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.storage.InMemoryArenaRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryDuelRequestRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryKitRegistryRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryMatchRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPendingRestorationRepository;
import io.github.xreatlabz.revprac.adapters.storage.InMemoryPlayerSessionRepository;
import io.github.xreatlabz.revprac.application.arenas.ArenaRegistryService;
import io.github.xreatlabz.revprac.application.kits.KitRegistryService;
import io.github.xreatlabz.revprac.application.matches.DuelRequestService;
import io.github.xreatlabz.revprac.application.matches.MatchLifecycleService;
import io.github.xreatlabz.revprac.application.players.PlayerSessionService;
import io.github.xreatlabz.revprac.domain.arenas.ArenaCuboid;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitInventory;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import io.github.xreatlabz.revprac.domain.matches.DuelRequest;
import io.github.xreatlabz.revprac.domain.matches.DuelRequestState;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerContext;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.ports.arenas.ArenaResetPort;
import io.github.xreatlabz.revprac.ports.matches.MatchPlayerPort;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

final class RevPracDuelCommandTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void commandRequiresPlayerSenderAndDuelPermission() {
        Harness harness = new Harness();

        harness.command.onCommand(harness.server.getConsoleSender(), command(), "duel", new String[] {"forfeit"});
        assertEquals("Only players can use /duel.", harness.server.getConsoleSender().nextMessage());

        harness.command.onCommand(harness.requester, command(), "duel", new String[] {"target", "arena-one", "nodebuff"});
        assertEquals("You do not have permission to use this command.", harness.requester.nextMessage());
    }

    @Test
    void duelRequestValidatesUsageLookupArenaAndKit() {
        Harness harness = new Harness();
        harness.requester.setOp(true);

        harness.command.onCommand(harness.requester, command(), "duel", new String[] {"target", "arena-one"});
        assertEquals(
                "Usage: /duel <player> <arena> <kit> or /duel request <player> <arena> <kit>",
                harness.requester.nextMessage());

        harness.command.onCommand(harness.requester, command(), "duel", new String[] {"missing", "arena-one", "nodebuff"});
        assertEquals("Player not found: missing.", harness.requester.nextMessage());

        harness.command.onCommand(harness.requester, command(), "duel", new String[] {"target", "missing-arena", "nodebuff"});
        assertEquals("unknown arena: missing-arena", harness.requester.nextMessage());

        harness.command.onCommand(harness.requester, command(), "duel", new String[] {"target", "arena-one", "missing-kit"});
        assertEquals("unknown kit: missing-kit", harness.requester.nextMessage());
    }

    @Test
    void duelRequestUsesTheApplicationService() {
        Harness harness = new Harness();
        harness.requester.setOp(true);

        harness.command.onCommand(harness.requester, command(), "duel", new String[] {"target", "arena-one", "nodebuff"});

        assertEquals("Sent duel request to target.", harness.requester.nextMessage());
        DuelRequest created = harness.requestRepository.findAll().stream().findFirst().orElseThrow();
        assertEquals(DuelRequestState.PENDING, created.state());
    }

    @Test
    void knownSubcommandsWithWrongArityShowUsageInsteadOfFallingThroughToRequestHandling() {
        Harness harness = new Harness();
        harness.requester.setOp(true);
        PlayerMock reservedNameTarget = harness.server.addPlayer("accept");
        harness.matchPlayerPort.onlinePlayers.add(new PlayerId(reservedNameTarget.getUniqueId()));

        harness.command.onCommand(
                harness.requester, command(), "duel", new String[] {"accept", "target", "arena-one"});

        assertEquals("Usage: /duel accept <player>", harness.requester.nextMessage());
        assertTrue(harness.requestRepository.findAll().isEmpty());
    }

    @Test
    void duelRequestSupportsReservedPlayerNamesThroughExplicitRequestSubcommand() {
        Harness harness = new Harness();
        harness.requester.setOp(true);
        PlayerMock reservedNameTarget = harness.server.addPlayer("accept");
        harness.matchPlayerPort.onlinePlayers.add(new PlayerId(reservedNameTarget.getUniqueId()));

        harness.command.onCommand(
                harness.requester,
                command(),
                "duel",
                new String[] {"request", "accept", "arena-one", "nodebuff"});

        assertEquals("Sent duel request to accept.", harness.requester.nextMessage());
        DuelRequest created = harness.requestRepository.findAll().stream().findFirst().orElseThrow();
        assertEquals(new PlayerId(reservedNameTarget.getUniqueId()), created.targetId());
    }

    @Test
    void duelAcceptUsesTheApplicationService() {
        Harness harness = new Harness();
        harness.requester.setOp(true);
        harness.target.setOp(true);
        harness.duelRequestService.request(harness.requesterId(), harness.targetId(), new ArenaId("arena-one"), new KitId("nodebuff"));

        harness.command.onCommand(harness.target, command(), "duel", new String[] {"accept", "requester"});

        assertEquals("Accepted duel from requester.", harness.target.nextMessage());
        Match acceptedMatch = harness.matchRepository.findByPlayer(harness.requesterId()).orElseThrow();
        assertEquals("COUNTDOWN", acceptedMatch.state().name());
    }

    @Test
    void duelDenyUsesTheApplicationServiceAndReportsDenyUsage() {
        Harness harness = new Harness();
        harness.requester.setOp(true);
        harness.target.setOp(true);
        harness.duelRequestService.request(harness.requesterId(), harness.targetId(), new ArenaId("arena-one"), new KitId("nodebuff"));

        harness.command.onCommand(harness.target, command(), "duel", new String[] {"deny"});
        assertEquals("Usage: /duel deny <player>", harness.target.nextMessage());

        harness.command.onCommand(harness.target, command(), "duel", new String[] {"deny", "requester"});

        assertEquals("Declined duel from requester.", harness.target.nextMessage());
        assertTrue(harness.requestRepository.findAll().isEmpty());
    }

    @Test
    void duelCancelUsesTheApplicationService() {
        Harness harness = new Harness();
        harness.requester.setOp(true);
        harness.target.setOp(true);
        harness.duelRequestService.request(harness.requesterId(), harness.targetId(), new ArenaId("arena-one"), new KitId("nodebuff"));

        harness.command.onCommand(harness.requester, command(), "duel", new String[] {"cancel", "target"});

        assertEquals("Cancelled duel with target.", harness.requester.nextMessage());
        assertTrue(harness.requestRepository.findAll().isEmpty());
    }

    @Test
    void duelSpectateAndForfeitInvokeLifecycleActions() {
        Harness harness = new Harness();
        harness.requester.setOp(true);
        harness.target.setOp(true);
        harness.spectator.setOp(true);
        harness.duelRequestService.request(
                harness.requesterId(), harness.targetId(), new ArenaId("arena-one"), new KitId("nodebuff"));
        harness.duelRequestService.accept(harness.requesterId(), harness.targetId());
        harness.matchLifecycleService.tick();
        harness.matchLifecycleService.tick();
        harness.matchLifecycleService.tick();
        harness.playerSessionService.join(harness.spectatorId());

        harness.command.onCommand(harness.spectator, command(), "duel", new String[] {"spectate", "requester"});
        assertEquals("Spectating requester.", harness.spectator.nextMessage());
        assertEquals(PlayerContext.SPECTATOR, harness.sessionRepository.find(harness.spectatorId()).orElseThrow().context());

        harness.command.onCommand(harness.target, command(), "duel", new String[] {"forfeit"});
        assertEquals("Forfeited duel.", harness.target.nextMessage());
        assertTrue(harness.matchRepository.findAll().isEmpty());
        assertEquals(PlayerContext.LOBBY, harness.sessionRepository.find(harness.requesterId()).orElseThrow().context());
    }

    private static Command command() {
        return new Command("duel") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return false;
            }
        };
    }

    private static final class Harness {
        private final ServerMock server = MockBukkit.mock();
        @SuppressWarnings("unused")
        private final WorldMock world = addKeyedWorld(server, "match-world");
        private final InMemoryDuelRequestRepository requestRepository = new InMemoryDuelRequestRepository();
        private final InMemoryMatchRepository matchRepository = new InMemoryMatchRepository();
        private final InMemoryPlayerSessionRepository sessionRepository = new InMemoryPlayerSessionRepository();
        private final PlayerSessionService playerSessionService =
                new PlayerSessionService(sessionRepository, new InMemoryPendingRestorationRepository(), new SnapshotStatePort());
        private final ArenaRegistryService arenaRegistryService =
                new ArenaRegistryService(new InMemoryArenaRegistryRepository(), new NoOpArenaResetPort());
        private final KitRegistryService kitRegistryService = new KitRegistryService(new InMemoryKitRegistryRepository());
        private final FakeMatchPlayerPort matchPlayerPort = new FakeMatchPlayerPort();
        private final MatchLifecycleService matchLifecycleService = new MatchLifecycleService(
                matchRepository,
                playerSessionService,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerPort,
                new MatchRuleset(3, 200, true),
                event -> {
                });
        private final DuelRequestService duelRequestService = new DuelRequestService(
                requestRepository,
                matchRepository,
                arenaRegistryService,
                kitRegistryService,
                matchPlayerPort,
                matchLifecycleService,
                Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(30),
                event -> {
                });
        private final PlayerMock requester = server.addPlayer("requester");
        private final PlayerMock target = server.addPlayer("target");
        private final PlayerMock spectator = server.addPlayer("spectator");
        private final RevPracDuelCommand command =
                new RevPracDuelCommand(server, duelRequestService, matchLifecycleService);

        private Harness() {
            arenaRegistryService.register(new ArenaDefinition(
                    new ArenaId("arena-one"),
                    "Arena One",
                    new ArenaCuboid("minecraft:match-world", 0, 60, 0, 20, 90, 20),
                    new ArenaSpawnPoint("minecraft:match-world", 2.0d, 70.0d, 2.0d, 0.0f, 0.0f),
                    new ArenaSpawnPoint("minecraft:match-world", 18.0d, 70.0d, 18.0d, 180.0f, 0.0f),
                    true));
            kitRegistryService.register(new KitDefinition(
                    new KitId("nodebuff"),
                    "Nodebuff",
                    new KitInventory(List.of(), List.of(), List.of(), 0),
                    List.of(),
                    new KitRules(false, false, true, false),
                    true));
            matchPlayerPort.onlinePlayers.addAll(Set.of(requesterId(), targetId(), spectatorId()));
            playerSessionService.join(requesterId());
            playerSessionService.join(targetId());
        }

        private PlayerId requesterId() {
            return new PlayerId(requester.getUniqueId());
        }

        private PlayerId targetId() {
            return new PlayerId(target.getUniqueId());
        }

        private PlayerId spectatorId() {
            return new PlayerId(spectator.getUniqueId());
        }
    }

    private static final class FakeMatchPlayerPort implements MatchPlayerPort {
        private final Set<PlayerId> onlinePlayers = new java.util.HashSet<>();

        @Override
        public boolean isOnline(PlayerId playerId) {
            return onlinePlayers.contains(playerId);
        }

        @Override
        public void prepareCombatant(
                PlayerId playerId,
                Match match,
                io.github.xreatlabz.revprac.domain.matches.MatchSide side,
                ArenaDefinition arenaDefinition,
                KitDefinition kitDefinition) {
        }

        @Override
        public void prepareSpectator(PlayerId playerId, Match match, ArenaDefinition arenaDefinition) {
        }

        @Override
        public void clearMatchState(PlayerId playerId) {
        }
    }

    private static final class SnapshotStatePort implements PlayerStatePort {

        @Override
        public PlayerSafetySnapshot capture(PlayerId playerId) {
            return new PlayerSafetySnapshot(
                    new LocationSnapshot("minecraft:match-world", 10.0d, 70.0d, 10.0d, 0.0f, 0.0f),
                    new InventorySnapshot(List.of(), List.of(), List.of(), List.of(), null, 0),
                    new PlayerStatusSnapshot("SURVIVAL", 20.0d, 20, 5.0f, 0.0f, 0, false, false, List.of()));
        }

        @Override
        public void restore(PlayerId playerId, PlayerSafetySnapshot snapshot) {
        }

        @Override
        public boolean isOnline(PlayerId playerId) {
            return true;
        }
    }

    private static final class NoOpArenaResetPort implements ArenaResetPort {

        @Override
        public void reset(ArenaDefinition arenaDefinition) {
        }
    }

    private static WorldMock addKeyedWorld(ServerMock server, String worldName) {
        WorldMock world = new KeyedWorldMock(worldName);
        server.addWorld(world);
        return world;
    }

    private static final class KeyedWorldMock extends WorldMock {

        private final NamespacedKey key;

        private KeyedWorldMock(String worldName) {
            this.key = NamespacedKey.minecraft(worldName);
            setName(worldName);
        }

        @Override
        public NamespacedKey getKey() {
            return key;
        }
    }
}
