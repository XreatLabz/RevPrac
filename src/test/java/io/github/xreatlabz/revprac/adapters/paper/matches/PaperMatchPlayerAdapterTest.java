package io.github.xreatlabz.revprac.adapters.paper.matches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.adapters.paper.kits.PaperKitLoadoutAdapter;
import io.github.xreatlabz.revprac.domain.arenas.ArenaCuboid;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaReservationId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint;
import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import io.github.xreatlabz.revprac.domain.matches.Match;
import io.github.xreatlabz.revprac.domain.matches.MatchId;
import io.github.xreatlabz.revprac.domain.matches.MatchParticipants;
import io.github.xreatlabz.revprac.domain.matches.MatchRuleset;
import io.github.xreatlabz.revprac.domain.matches.MatchSide;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

final class PaperMatchPlayerAdapterTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void prepareCombatantTeleportsAppliesKitAndMarksTheCountdownFreezeState() {
        ServerMock server = MockBukkit.mock();
        WorldMock world = addKeyedWorld(server, "match-world");
        PlayerMock kitSource = server.addPlayer("kit-source");
        PlayerMock combatant = server.addPlayer("combatant");
        combatant.getInventory().setItem(0, new ItemStack(Material.DIRT, 3));
        combatant.setGameMode(GameMode.CREATIVE);
        combatant.setAllowFlight(true);
        combatant.setFlying(true);

        kitSource.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD, 1));
        kitSource.getInventory().setItem(4, new ItemStack(Material.GOLDEN_APPLE, 8));
        kitSource.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET, 1));
        kitSource.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD, 1));
        kitSource.getInventory().setHeldItemSlot(4);
        KitDefinition kitDefinition = new PaperKitLoadoutAdapter().capture(
                kitSource,
                new KitId("nodebuff"),
                "Nodebuff",
                new KitRules(false, false, true, false),
                true);

        PaperMatchPlayerAdapter adapter = new PaperMatchPlayerAdapter(server, new PaperKitLoadoutAdapter());

        adapter.prepareCombatant(
                new PlayerId(combatant.getUniqueId()),
                countdownMatch(combatant.getUniqueId(), kitSource.getUniqueId()),
                MatchSide.TWO,
                arenaDefinition(),
                kitDefinition);

        assertEquals(new Location(world, 18.0d, 70.0d, 18.0d, 180.0f, 0.0f), combatant.getLocation());
        assertEquals(GameMode.SURVIVAL, combatant.getGameMode());
        assertFalse(combatant.getAllowFlight());
        assertFalse(combatant.isFlying());
        assertEquals(new ItemStack(Material.DIAMOND_SWORD, 1), combatant.getInventory().getItem(0));
        assertEquals(new ItemStack(Material.GOLDEN_APPLE, 8), combatant.getInventory().getItem(4));
        assertEquals(new ItemStack(Material.DIAMOND_HELMET, 1), combatant.getInventory().getHelmet());
        assertEquals(new ItemStack(Material.SHIELD, 1), combatant.getInventory().getItemInOffHand());
        assertTrue(adapter.isCountdownFrozen(new PlayerId(combatant.getUniqueId())));
        assertFalse(adapter.isSpectator(new PlayerId(combatant.getUniqueId())));
    }

    @Test
    void prepareSpectatorTeleportsToConfiguredArenaSpawnAndSetsSpectatorState() {
        ServerMock server = MockBukkit.mock();
        WorldMock world = addKeyedWorld(server, "match-world");
        PlayerMock spectator = server.addPlayer("spectator");
        spectator.setGameMode(GameMode.SURVIVAL);

        PaperMatchPlayerAdapter adapter = new PaperMatchPlayerAdapter(server, new PaperKitLoadoutAdapter());

        adapter.prepareSpectator(
                new PlayerId(spectator.getUniqueId()),
                activeMatch(UUID.nameUUIDFromBytes("one".getBytes()), UUID.nameUUIDFromBytes("two".getBytes())),
                arenaDefinition());

        assertEquals(GameMode.SPECTATOR, spectator.getGameMode());
        assertTrue(spectator.getAllowFlight());
        assertTrue(spectator.isFlying());
        assertEquals(new Location(world, 2.0d, 70.0d, 2.0d, 0.0f, 0.0f), spectator.getLocation());
        assertTrue(adapter.isSpectator(new PlayerId(spectator.getUniqueId())));
        assertFalse(adapter.isCountdownFrozen(new PlayerId(spectator.getUniqueId())));
    }

    @Test
    void clearMatchStateIsIdempotentAndToleratesOfflinePlayers() {
        ServerMock server = MockBukkit.mock();
        addKeyedWorld(server, "match-world");
        PlayerMock combatant = server.addPlayer("combatant");
        PlayerMock spectator = server.addPlayer("spectator");
        PaperMatchPlayerAdapter adapter = new PaperMatchPlayerAdapter(server, new PaperKitLoadoutAdapter());

        adapter.prepareCombatant(
                new PlayerId(combatant.getUniqueId()),
                countdownMatch(combatant.getUniqueId(), spectator.getUniqueId()),
                MatchSide.ONE,
                arenaDefinition(),
                captureSimpleKit(server));
        adapter.prepareSpectator(
                new PlayerId(spectator.getUniqueId()),
                activeMatch(combatant.getUniqueId(), spectator.getUniqueId()),
                arenaDefinition());

        adapter.clearMatchState(new PlayerId(combatant.getUniqueId()));
        adapter.clearMatchState(new PlayerId(spectator.getUniqueId()));
        spectator.disconnect();
        adapter.clearMatchState(new PlayerId(spectator.getUniqueId()));

        assertFalse(adapter.isCountdownFrozen(new PlayerId(combatant.getUniqueId())));
        assertFalse(adapter.isSpectator(new PlayerId(spectator.getUniqueId())));
        assertEquals(GameMode.SURVIVAL, combatant.getGameMode());
        assertEquals(GameMode.SURVIVAL, server.getPlayer(combatant.getUniqueId()).getGameMode());
    }

    @Test
    void synchronizeCountdownStateThawsPlayersOnceMatchesBecomeActiveOrDisappear() {
        ServerMock server = MockBukkit.mock();
        addKeyedWorld(server, "match-world");
        PlayerMock combatant = server.addPlayer("combatant");
        PlayerMock opponent = server.addPlayer("opponent");
        PaperMatchPlayerAdapter adapter = new PaperMatchPlayerAdapter(server, new PaperKitLoadoutAdapter());
        PlayerId combatantId = new PlayerId(combatant.getUniqueId());

        adapter.prepareCombatant(
                combatantId,
                countdownMatch(combatant.getUniqueId(), opponent.getUniqueId()),
                MatchSide.ONE,
                arenaDefinition(),
                captureSimpleKit(server));
        assertTrue(adapter.isCountdownFrozen(combatantId));

        adapter.synchronizeCountdownState(java.util.List.of(activeMatch(combatant.getUniqueId(), opponent.getUniqueId())));
        assertFalse(adapter.isCountdownFrozen(combatantId));

        adapter.prepareCombatant(
                combatantId,
                countdownMatch(combatant.getUniqueId(), opponent.getUniqueId()),
                MatchSide.ONE,
                arenaDefinition(),
                captureSimpleKit(server));
        assertTrue(adapter.isCountdownFrozen(combatantId));

        adapter.synchronizeCountdownState(java.util.List.of());
        assertFalse(adapter.isCountdownFrozen(combatantId));
        assertNotEquals(GameMode.SPECTATOR, combatant.getGameMode());
    }

    private static Match countdownMatch(UUID playerOneId, UUID playerTwoId) {
        return Match.create(
                new MatchId(UUID.nameUUIDFromBytes("countdown-match".getBytes())),
                new MatchParticipants(new PlayerId(playerOneId), new PlayerId(playerTwoId)),
                new ArenaId("arena-one"),
                new KitId("nodebuff"),
                new ArenaReservationId(UUID.nameUUIDFromBytes("reservation".getBytes())),
                new MatchRuleset(3, 200, true));
    }

    private static Match activeMatch(UUID playerOneId, UUID playerTwoId) {
        return countdownMatch(playerOneId, playerTwoId).tickCountdown().tickCountdown().tickCountdown();
    }

    private static ArenaDefinition arenaDefinition() {
        return new ArenaDefinition(
                new ArenaId("arena-one"),
                "Arena One",
                new ArenaCuboid("minecraft:match-world", 0, 60, 0, 20, 90, 20),
                new ArenaSpawnPoint("minecraft:match-world", 2.0d, 70.0d, 2.0d, 0.0f, 0.0f),
                new ArenaSpawnPoint("minecraft:match-world", 18.0d, 70.0d, 18.0d, 180.0f, 0.0f),
                true);
    }

    private static KitDefinition captureSimpleKit(ServerMock server) {
        PlayerMock kitSource = server.addPlayer("kit-template");
        kitSource.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD, 1));
        return new PaperKitLoadoutAdapter().capture(
                kitSource,
                new KitId("nodebuff"),
                "Nodebuff",
                new KitRules(false, false, true, false),
                true);
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
