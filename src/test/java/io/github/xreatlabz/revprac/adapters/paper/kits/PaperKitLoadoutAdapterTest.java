package io.github.xreatlabz.revprac.adapters.paper.kits;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

final class PaperKitLoadoutAdapterTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void captureMapsStorageArmorExtraSelectedSlotAndPotionEffectsIntoKitDefinition() {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = server.addPlayer("capture-player");

        ItemStack[] expectedStorage = new ItemStack[player.getInventory().getStorageContents().length];
        expectedStorage[0] = stack(Material.DIAMOND_SWORD, 1);
        expectedStorage[7] = stack(Material.GOLDEN_APPLE, 4);
        expectedStorage[20] = stack(Material.COBWEB, 8);
        player.getInventory().setStorageContents(expectedStorage);

        ItemStack[] expectedArmor = new ItemStack[player.getInventory().getArmorContents().length];
        expectedArmor[0] = stack(Material.NETHERITE_BOOTS, 1);
        expectedArmor[1] = stack(Material.NETHERITE_LEGGINGS, 1);
        expectedArmor[2] = stack(Material.NETHERITE_CHESTPLATE, 1);
        expectedArmor[3] = stack(Material.NETHERITE_HELMET, 1);
        player.getInventory().setArmorContents(expectedArmor);

        ItemStack[] expectedExtra = new ItemStack[player.getInventory().getExtraContents().length];
        expectedExtra[expectedExtra.length - 1] = stack(Material.SHIELD, 1);
        player.getInventory().setExtraContents(expectedExtra);
        player.getInventory().setHeldItemSlot(7);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 2, false, true, false));

        PaperKitLoadoutAdapter adapter = new PaperKitLoadoutAdapter();

        KitDefinition definition = adapter.capture(
                player, new KitId("nodebuff"), "NoDebuff", new KitRules(false, false, true, false), true);

        assertEquals("nodebuff", definition.id().value());
        assertEquals("NoDebuff", definition.displayName());
        assertEquals(7, definition.inventory().selectedSlot());
        assertEquals(expectedStorage.length, definition.inventory().storage().size());
        assertTrue(definition.inventory().storage().get(0) != null);
        assertNull(definition.inventory().storage().get(1));
        assertTrue(definition.inventory().storage().get(7) != null);
        assertTrue(definition.inventory().storage().get(20) != null);
        assertEquals(expectedArmor.length, definition.inventory().armor().size());
        assertTrue(definition.inventory().armor().stream().allMatch(value -> value != null));
        assertEquals(expectedExtra.length, definition.inventory().extra().size());
        assertTrue(definition.inventory().extra().get(expectedExtra.length - 1) != null);

        List<String> effectKeys = definition.potionEffects().stream()
                .map(effect -> effect.effectKey())
                .sorted()
                .toList();
        assertEquals(List.of("minecraft:regeneration", "minecraft:speed"), effectKeys);
    }

    @Test
    void applyRestoresCapturedKitToPlayer() {
        ServerMock server = MockBukkit.mock();
        PlayerMock source = server.addPlayer("source-player");
        PlayerMock target = server.addPlayer("target-player");

        ItemStack[] expectedStorage = new ItemStack[source.getInventory().getStorageContents().length];
        expectedStorage[0] = stack(Material.IRON_SWORD, 1);
        expectedStorage[5] = stack(Material.ENDER_PEARL, 16);
        source.getInventory().setStorageContents(expectedStorage);

        ItemStack[] expectedArmor = new ItemStack[source.getInventory().getArmorContents().length];
        expectedArmor[1] = stack(Material.DIAMOND_LEGGINGS, 1);
        expectedArmor[3] = stack(Material.DIAMOND_HELMET, 1);
        source.getInventory().setArmorContents(expectedArmor);

        ItemStack[] expectedExtra = new ItemStack[source.getInventory().getExtraContents().length];
        expectedExtra[expectedExtra.length - 1] = stack(Material.TOTEM_OF_UNDYING, 1);
        source.getInventory().setExtraContents(expectedExtra);
        source.getInventory().setHeldItemSlot(5);
        source.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 400, 1, false, true, true));

        PaperKitLoadoutAdapter adapter = new PaperKitLoadoutAdapter();
        KitDefinition definition = adapter.capture(
                source, new KitId("boxing"), "Boxing", new KitRules(false, false, false, false), true);

        target.getInventory().clear();
        target.getInventory().setArmorContents(new ItemStack[target.getInventory().getArmorContents().length]);
        target.getInventory().setExtraContents(new ItemStack[target.getInventory().getExtraContents().length]);
        target.getInventory().setHeldItemSlot(0);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 0, false, false, false));

        adapter.apply(target, definition);

        assertArrayEquals(expectedStorage, target.getInventory().getStorageContents());
        assertArrayEquals(expectedArmor, target.getInventory().getArmorContents());
        assertArrayEquals(expectedExtra, target.getInventory().getExtraContents());
        assertEquals(5, target.getInventory().getHeldItemSlot());

        List<PotionEffect> effects = target.getActivePotionEffects().stream()
                .sorted(Comparator.comparing(effect -> effect.getType().getKey().asString()))
                .toList();
        assertEquals(1, effects.size());
        assertEquals(PotionEffectType.JUMP_BOOST, effects.getFirst().getType());
    }

    @Test
    void nullItemSlotsSurviveCaptureSaveLoadAndApply() throws Exception {
        ServerMock server = MockBukkit.mock();
        PlayerMock source = server.addPlayer("source-player");
        PlayerMock target = server.addPlayer("target-player");

        ItemStack[] expectedStorage = new ItemStack[source.getInventory().getStorageContents().length];
        expectedStorage[0] = stack(Material.BOW, 1);
        expectedStorage[1] = null;
        expectedStorage[8] = stack(Material.ARROW, 32);
        source.getInventory().setStorageContents(expectedStorage);

        ItemStack[] expectedArmor = new ItemStack[source.getInventory().getArmorContents().length];
        expectedArmor[0] = null;
        expectedArmor[1] = stack(Material.CHAINMAIL_LEGGINGS, 1);
        source.getInventory().setArmorContents(expectedArmor);

        ItemStack[] expectedExtra = new ItemStack[source.getInventory().getExtraContents().length];
        expectedExtra[expectedExtra.length - 1] = null;
        source.getInventory().setExtraContents(expectedExtra);
        source.getInventory().setHeldItemSlot(8);

        PaperKitLoadoutAdapter adapter = new PaperKitLoadoutAdapter();
        KitDefinition captured = adapter.capture(
                source, new KitId("archer"), "Archer", new KitRules(false, false, true, false), true);
        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);
        files.save(List.of(captured));

        KitDefinition reloaded = files.load().getFirst();

        target.getInventory().setStorageContents(new ItemStack[target.getInventory().getStorageContents().length]);
        target.getInventory().setArmorContents(new ItemStack[target.getInventory().getArmorContents().length]);
        target.getInventory().setExtraContents(new ItemStack[target.getInventory().getExtraContents().length]);

        adapter.apply(target, reloaded);

        assertArrayEquals(expectedStorage, target.getInventory().getStorageContents());
        assertArrayEquals(expectedArmor, target.getInventory().getArmorContents());
        assertArrayEquals(expectedExtra, target.getInventory().getExtraContents());
        assertNull(target.getInventory().getStorageContents()[1], "Null storage slots must survive round-trip");
        assertNull(target.getInventory().getArmorContents()[0], "Null armor slots must survive round-trip");
    }

    @Test
    void malformedBase64ItemPayloadsFailClearly() {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = server.addPlayer("apply-player");

        PaperKitLoadoutAdapter adapter = new PaperKitLoadoutAdapter();
        KitDefinition malformed = new KitDefinition(
                new KitId("broken"),
                "Broken",
                new io.github.xreatlabz.revprac.domain.kits.KitInventory(
                        List.of("%%%not-base64%%%"), List.of(), List.of(), 0),
                List.of(),
                new KitRules(false, false, true, false),
                true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> adapter.apply(player, malformed));
        assertTrue(exception.getMessage().contains("storage[0]"));
    }

    private static ItemStack stack(Material material, int amount) {
        return new ItemStack(material, amount);
    }
}
