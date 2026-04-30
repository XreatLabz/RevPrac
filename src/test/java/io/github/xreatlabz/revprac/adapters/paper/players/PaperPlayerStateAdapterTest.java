package io.github.xreatlabz.revprac.adapters.paper.players;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import java.util.Comparator;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class PaperPlayerStateAdapterTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void captureMapsTheCurrentOnlinePlayerStateIntoDomainSnapshots() {
        ServerMock server = MockBukkit.mock();
        World world = server.addSimpleWorld("capture-world");
        PlayerMock player = server.addPlayer("capture-player");
        player.teleport(new Location(world, 12.5d, 70.0d, -4.25d, 135.0f, 12.0f));

        ItemStack[] expectedStorage = new ItemStack[player.getInventory().getStorageContents().length];
        expectedStorage[0] = namedStack(Material.DIAMOND_SWORD, 1);
        expectedStorage[7] = namedStack(Material.GOLDEN_APPLE, 3);
        expectedStorage[20] = namedStack(Material.COBBLESTONE, 64);
        player.getInventory().setStorageContents(expectedStorage);

        ItemStack[] expectedArmor = new ItemStack[player.getInventory().getArmorContents().length];
        expectedArmor[0] = namedStack(Material.NETHERITE_BOOTS, 1);
        expectedArmor[2] = namedStack(Material.NETHERITE_CHESTPLATE, 1);
        player.getInventory().setArmorContents(expectedArmor);

        ItemStack[] expectedExtra = new ItemStack[player.getInventory().getExtraContents().length];
        expectedExtra[expectedExtra.length - 1] = namedStack(Material.SHIELD, 1);
        player.getInventory().setExtraContents(expectedExtra);
        player.getInventory().setItemInOffHand(expectedExtra[expectedExtra.length - 1]);

        ItemStack[] expectedEnderChest = new ItemStack[player.getEnderChest().getSize()];
        expectedEnderChest[1] = namedStack(Material.ENDER_PEARL, 8);
        expectedEnderChest[13] = namedStack(Material.COOKED_BEEF, 16);
        player.getEnderChest().setContents(expectedEnderChest);
        player.setItemOnCursor(namedStack(Material.EMERALD, 5));

        player.getInventory().setHeldItemSlot(7);
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(17.0d);
        player.setFoodLevel(13);
        player.setSaturation(4.5f);
        player.setExp(0.65f);
        player.setLevel(21);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 2, false, true, false));

        PaperPlayerStateAdapter adapter = new PaperPlayerStateAdapter(server);

        PlayerSafetySnapshot snapshot = adapter.capture(new PlayerId(player.getUniqueId()));

        assertEquals(world.getKey().asString(), snapshot.location().worldKey());
        assertEquals(12.5d, snapshot.location().x());
        assertEquals(70.0d, snapshot.location().y());
        assertEquals(-4.25d, snapshot.location().z());
        assertEquals(135.0f, snapshot.location().yaw());
        assertEquals(12.0f, snapshot.location().pitch());

        assertEquals(expectedStorage.length, snapshot.inventory().storage().size());
        assertNotNull(snapshot.inventory().storage().get(0));
        assertNull(snapshot.inventory().storage().get(1));
        assertNotNull(snapshot.inventory().storage().get(7));
        assertNotNull(snapshot.inventory().storage().get(20));
        assertEquals(expectedArmor.length, snapshot.inventory().armor().size());
        assertNotNull(snapshot.inventory().armor().get(0));
        assertNull(snapshot.inventory().armor().get(1));
        assertNotNull(snapshot.inventory().armor().get(2));
        assertEquals(expectedExtra.length, snapshot.inventory().extra().size());
        assertNotNull(snapshot.inventory().extra().get(expectedExtra.length - 1));
        assertEquals(expectedEnderChest.length, snapshot.inventory().enderChest().size());
        assertNull(snapshot.inventory().enderChest().get(0));
        assertNotNull(snapshot.inventory().enderChest().get(1));
        assertNotNull(snapshot.inventory().cursorItem());
        assertEquals(7, snapshot.inventory().selectedSlot());

        assertEquals(GameMode.ADVENTURE.name(), snapshot.status().gameMode());
        assertEquals(17.0d, snapshot.status().health());
        assertEquals(13, snapshot.status().foodLevel());
        assertEquals(4.5f, snapshot.status().saturation());
        assertEquals(0.65f, snapshot.status().expProgress());
        assertEquals(21, snapshot.status().level());
        assertTrue(snapshot.status().allowFlight());
        assertTrue(snapshot.status().flying());

        List<String> effectKeys = snapshot.status().potionEffects().stream()
                .map(effect -> effect.effectKey())
                .sorted()
                .toList();
        assertEquals(List.of("minecraft:regeneration", "minecraft:speed"), effectKeys);
        assertTrue(snapshot.status().potionEffects().stream().anyMatch(effect ->
                effect.effectKey().equals("minecraft:speed")
                        && effect.durationTicks() == 600
                        && effect.amplifier() == 1
                        && effect.ambient()
                        && !effect.particles()
                        && effect.icon()));
        assertTrue(snapshot.status().potionEffects().stream().anyMatch(effect ->
                effect.effectKey().equals("minecraft:regeneration")
                        && effect.durationTicks() == 120
                        && effect.amplifier() == 2
                        && !effect.ambient()
                        && effect.particles()
                        && !effect.icon()));
    }

    @Test
    void restoreReappliesTheCapturedStateIncludingNullSlotsAndOffhandWithoutSlotGuessing() {
        ServerMock server = MockBukkit.mock();
        World baselineWorld = server.addSimpleWorld("baseline-world");
        World mutatedWorld = server.addSimpleWorld("mutated-world");
        PlayerMock player = server.addPlayer("restore-player");
        PaperPlayerStateAdapter adapter = new PaperPlayerStateAdapter(server);

        ItemStack[] expectedStorage = new ItemStack[player.getInventory().getStorageContents().length];
        expectedStorage[0] = namedStack(Material.IRON_SWORD, 1);
        expectedStorage[4] = namedStack(Material.GOLDEN_CARROT, 16);
        expectedStorage[31] = namedStack(Material.OBSIDIAN, 12);
        player.getInventory().setStorageContents(expectedStorage);

        ItemStack[] expectedArmor = new ItemStack[player.getInventory().getArmorContents().length];
        expectedArmor[1] = namedStack(Material.DIAMOND_LEGGINGS, 1);
        expectedArmor[3] = namedStack(Material.DIAMOND_HELMET, 1);
        player.getInventory().setArmorContents(expectedArmor);

        ItemStack expectedOffhand = namedStack(Material.TOTEM_OF_UNDYING, 1);
        player.getInventory().setItemInOffHand(expectedOffhand);
        ItemStack[] expectedExtra = player.getInventory().getExtraContents().clone();

        ItemStack[] expectedEnderChest = new ItemStack[player.getEnderChest().getSize()];
        expectedEnderChest[0] = namedStack(Material.EXPERIENCE_BOTTLE, 16);
        expectedEnderChest[26] = namedStack(Material.ENCHANTED_GOLDEN_APPLE, 1);
        player.getEnderChest().setContents(expectedEnderChest);
        ItemStack expectedCursor = namedStack(Material.EMERALD, 5);
        player.setItemOnCursor(expectedCursor);

        Location expectedLocation = new Location(baselineWorld, -23.5d, 92.0d, 18.0d, -45.0f, 6.0f);
        player.teleport(expectedLocation);
        player.getInventory().setHeldItemSlot(4);
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(12.0d);
        player.setFoodLevel(9);
        player.setSaturation(3.0f);
        player.setExp(0.3f);
        player.setLevel(14);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 400, 1, false, true, true));

        PlayerSafetySnapshot baseline = adapter.capture(new PlayerId(player.getUniqueId()));

        player.getInventory().setStorageContents(new ItemStack[player.getInventory().getStorageContents().length]);
        player.getInventory().setArmorContents(new ItemStack[player.getInventory().getArmorContents().length]);
        player.getInventory().setExtraContents(new ItemStack[player.getInventory().getExtraContents().length]);
        player.getInventory().setItemInOffHand(null);
        player.getEnderChest().clear();
        player.setItemOnCursor(namedStack(Material.DIRT, 1));
        player.teleport(new Location(mutatedWorld, 1.0d, 64.0d, 1.0d, 0.0f, 0.0f));
        player.getInventory().setHeldItemSlot(0);
        player.setGameMode(GameMode.CREATIVE);
        player.setHealth(20.0d);
        player.setFoodLevel(20);
        player.setSaturation(10.0f);
        player.setExp(0.0f);
        player.setLevel(0);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.getActivePotionEffects().stream()
                .map(PotionEffect::getType)
                .forEach(player::removePotionEffect);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 0, false, false, false));

        adapter.restore(new PlayerId(player.getUniqueId()), baseline);

        assertArrayEquals(expectedStorage, player.getInventory().getStorageContents());
        assertArrayEquals(expectedArmor, player.getInventory().getArmorContents());
        assertArrayEquals(expectedExtra, player.getInventory().getExtraContents());
        assertEquals(expectedOffhand, player.getInventory().getItemInOffHand());
        assertArrayEquals(expectedEnderChest, player.getEnderChest().getContents());
        assertEquals(expectedCursor, player.getItemOnCursor());
        assertEquals(4, player.getInventory().getHeldItemSlot());
        assertEquals(GameMode.SURVIVAL, player.getGameMode());
        assertEquals(expectedLocation.getWorld(), player.getLocation().getWorld());
        assertEquals(expectedLocation.getX(), player.getLocation().getX());
        assertEquals(expectedLocation.getY(), player.getLocation().getY());
        assertEquals(expectedLocation.getZ(), player.getLocation().getZ());
        assertEquals(expectedLocation.getYaw(), player.getLocation().getYaw());
        assertEquals(expectedLocation.getPitch(), player.getLocation().getPitch());
        assertEquals(12.0d, player.getHealth());
        assertEquals(9, player.getFoodLevel());
        assertEquals(3.0f, player.getSaturation());
        assertEquals(0.3f, player.getExp());
        assertEquals(14, player.getLevel());
        assertTrue(player.getAllowFlight());
        assertTrue(player.isFlying());

        List<PotionEffect> restoredEffects = player.getActivePotionEffects().stream()
                .sorted(Comparator.comparing(effect -> effect.getType().getKey().asString()))
                .toList();
        assertEquals(1, restoredEffects.size());
        PotionEffect restored = restoredEffects.getFirst();
        assertEquals(PotionEffectType.JUMP_BOOST, restored.getType());
        assertEquals(400, restored.getDuration());
        assertEquals(1, restored.getAmplifier());
        assertFalse(restored.isAmbient());
        assertTrue(restored.hasParticles());
        assertTrue(restored.hasIcon());
        assertNull(player.getInventory().getStorageContents()[1], "Restore must preserve empty storage slots");
        assertNull(player.getInventory().getArmorContents()[0], "Restore must preserve empty armor slots");
    }

    @Test
    void restoreValidatesLocationBeforeMutatingInventoryState() {
        ServerMock server = MockBukkit.mock();
        World world = server.addSimpleWorld("atomic-world");
        PlayerMock player = server.addPlayer("atomic-player");
        PaperPlayerStateAdapter adapter = new PaperPlayerStateAdapter(server);
        PlayerId playerId = new PlayerId(player.getUniqueId());

        ItemStack[] baselineStorage = new ItemStack[player.getInventory().getStorageContents().length];
        baselineStorage[0] = namedStack(Material.DIAMOND, 1);
        player.getInventory().setStorageContents(baselineStorage);
        player.teleport(new Location(world, 2.0d, 70.0d, 2.0d));
        PlayerSafetySnapshot baseline = adapter.capture(playerId);

        ItemStack[] mutatedStorage = new ItemStack[player.getInventory().getStorageContents().length];
        mutatedStorage[0] = namedStack(Material.DIRT, 3);
        player.getInventory().setStorageContents(mutatedStorage);

        PlayerSafetySnapshot missingWorldSnapshot = new PlayerSafetySnapshot(
                new LocationSnapshot("minecraft:missing-world", 10.0d, 65.0d, 10.0d, 0.0f, 0.0f),
                baseline.inventory(),
                baseline.status());

        assertThrows(IllegalStateException.class, () -> adapter.restore(playerId, missingWorldSnapshot));

        assertArrayEquals(mutatedStorage, player.getInventory().getStorageContents(), "Inventory should not mutate when restore location is invalid");
    }

    @Test
    void isOnlineOnlyReturnsTrueWhileTheMappedPlayerIsConnected() {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = server.addPlayer("online-player");
        PaperPlayerStateAdapter adapter = new PaperPlayerStateAdapter(server);
        PlayerId playerId = new PlayerId(player.getUniqueId());

        assertTrue(adapter.isOnline(playerId));

        player.disconnect();

        assertFalse(adapter.isOnline(playerId));
    }

    private static ItemStack namedStack(Material material, int amount) {
        return new ItemStack(material, amount);
    }
}
