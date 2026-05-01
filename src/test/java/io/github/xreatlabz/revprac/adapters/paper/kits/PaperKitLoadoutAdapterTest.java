package io.github.xreatlabz.revprac.adapters.paper.kits;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitInventory;
import io.github.xreatlabz.revprac.domain.kits.KitPotionEffect;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    private static final int PLAYER_STORAGE_SIZE = 36;
    private static final int PLAYER_ARMOR_SIZE = 4;
    private static final int PLAYER_EXTRA_SIZE = 1;

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
                .map(KitPotionEffect::effectKey)
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
                new KitInventory(
                        section(PLAYER_STORAGE_SIZE, Map.of(0, "%%%not-base64%%%")),
                        emptySection(PLAYER_ARMOR_SIZE),
                        emptySection(PLAYER_EXTRA_SIZE),
                        0),
                List.of(),
                new KitRules(false, false, true, false),
                true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> adapter.apply(player, malformed));
        assertTrue(exception.getMessage().contains("storage[0]"));
    }

    @Test
    void oversizedSectionWithInvalidPayloadFailsOnSizeBeforeDecoding() {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = server.addPlayer("apply-player");
        ItemStack[] baselineStorage = new ItemStack[player.getInventory().getStorageContents().length];
        baselineStorage[0] = stack(Material.STONE_SWORD, 1);
        player.getInventory().setStorageContents(baselineStorage);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, false, true, true));

        List<String> oversizedStorage = new ArrayList<>(emptySection(PLAYER_STORAGE_SIZE));
        oversizedStorage.add("%%%not-base64%%%");
        KitDefinition invalid = new KitDefinition(
                new KitId("oversized-storage"),
                "OversizedStorage",
                new KitInventory(
                        Collections.unmodifiableList(new ArrayList<>(oversizedStorage)),
                        emptySection(PLAYER_ARMOR_SIZE),
                        emptySection(PLAYER_EXTRA_SIZE),
                        0),
                List.of(new KitPotionEffect("minecraft:regeneration", 100, 0, false, true, true)),
                new KitRules(false, false, true, false),
                true);

        PaperKitLoadoutAdapter adapter = new PaperKitLoadoutAdapter();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> adapter.apply(player, invalid));
        assertTrue(exception.getMessage().contains("storage must contain exactly"));
        assertArrayEquals(baselineStorage, player.getInventory().getStorageContents());
        assertPotionEffectsEqual(List.of(new PotionEffect(PotionEffectType.SPEED, 100, 0, false, true, true)), player);
    }

    @Test
    void unknownPotionEffectKeyLeavesExistingInventoryAndEffectsUnchanged() {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = server.addPlayer("apply-player");
        ItemStack[] baselineStorage = new ItemStack[player.getInventory().getStorageContents().length];
        baselineStorage[0] = stack(Material.STONE_SWORD, 1);
        baselineStorage[9] = stack(Material.COOKED_BEEF, 16);
        player.getInventory().setStorageContents(baselineStorage);

        ItemStack[] baselineArmor = new ItemStack[player.getInventory().getArmorContents().length];
        baselineArmor[3] = stack(Material.IRON_HELMET, 1);
        player.getInventory().setArmorContents(baselineArmor);

        ItemStack[] baselineExtra = new ItemStack[player.getInventory().getExtraContents().length];
        baselineExtra[baselineExtra.length - 1] = stack(Material.SHIELD, 1);
        player.getInventory().setExtraContents(baselineExtra);
        player.getInventory().setHeldItemSlot(0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 0, false, true, true));

        KitDefinition invalid = new KitDefinition(
                new KitId("broken"),
                "Broken",
                new KitInventory(
                        section(PLAYER_STORAGE_SIZE, Map.of(0, encoded(stack(Material.DIAMOND_SWORD, 1)))),
                        section(PLAYER_ARMOR_SIZE, Map.of(0, encoded(stack(Material.DIAMOND_BOOTS, 1)))),
                        section(PLAYER_EXTRA_SIZE, Map.of(0, encoded(stack(Material.TOTEM_OF_UNDYING, 1)))),
                        1),
                List.of(new KitPotionEffect("minecraft:not_real", 200, 1, false, true, true)),
                new KitRules(false, false, true, false),
                true);

        PaperKitLoadoutAdapter adapter = new PaperKitLoadoutAdapter();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> adapter.apply(player, invalid));
        assertTrue(exception.getMessage().contains("minecraft:not_real"));
        assertArrayEquals(baselineStorage, player.getInventory().getStorageContents());
        assertArrayEquals(baselineArmor, player.getInventory().getArmorContents());
        assertArrayEquals(baselineExtra, player.getInventory().getExtraContents());
        assertEquals(0, player.getInventory().getHeldItemSlot());
        assertPotionEffectsEqual(List.of(new PotionEffect(PotionEffectType.SPEED, 300, 0, false, true, true)), player);
    }

    @Test
    void corruptButBase64ValidLaterItemBytesLeaveExistingInventoryAndEffectsUnchanged() {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = server.addPlayer("apply-player");
        ItemStack[] baselineStorage = new ItemStack[player.getInventory().getStorageContents().length];
        baselineStorage[2] = stack(Material.WOODEN_AXE, 1);
        player.getInventory().setStorageContents(baselineStorage);

        ItemStack[] baselineArmor = new ItemStack[player.getInventory().getArmorContents().length];
        baselineArmor[0] = stack(Material.LEATHER_BOOTS, 1);
        player.getInventory().setArmorContents(baselineArmor);

        ItemStack[] baselineExtra = new ItemStack[player.getInventory().getExtraContents().length];
        baselineExtra[baselineExtra.length - 1] = stack(Material.TOTEM_OF_UNDYING, 1);
        player.getInventory().setExtraContents(baselineExtra);
        player.getInventory().setHeldItemSlot(2);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1, false, true, false));

        KitDefinition invalid = new KitDefinition(
                new KitId("broken-bytes"),
                "BrokenBytes",
                new KitInventory(
                        section(PLAYER_STORAGE_SIZE, Map.of(0, encoded(stack(Material.BOW, 1)))),
                        section(PLAYER_ARMOR_SIZE, Map.of(0, encoded(stack(Material.CHAINMAIL_BOOTS, 1)))),
                        section(PLAYER_EXTRA_SIZE, Map.of(0, Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4}))),
                        4),
                List.of(new KitPotionEffect("minecraft:speed", 100, 0, false, true, true)),
                new KitRules(false, false, true, false),
                true);

        PaperKitLoadoutAdapter adapter = new PaperKitLoadoutAdapter();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> adapter.apply(player, invalid));
        assertTrue(exception.getMessage().contains("extra[0]"));
        assertArrayEquals(baselineStorage, player.getInventory().getStorageContents());
        assertArrayEquals(baselineArmor, player.getInventory().getArmorContents());
        assertArrayEquals(baselineExtra, player.getInventory().getExtraContents());
        assertEquals(2, player.getInventory().getHeldItemSlot());
        assertPotionEffectsEqual(List.of(new PotionEffect(PotionEffectType.REGENERATION, 120, 1, false, true, false)), player);
    }

    @Test
    void oversizedLaterSectionLeavesExistingInventoryAndEffectsUnchanged() {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = server.addPlayer("apply-player");
        ItemStack[] baselineStorage = new ItemStack[player.getInventory().getStorageContents().length];
        baselineStorage[1] = stack(Material.IRON_AXE, 1);
        player.getInventory().setStorageContents(baselineStorage);

        ItemStack[] baselineArmor = new ItemStack[player.getInventory().getArmorContents().length];
        baselineArmor[2] = stack(Material.IRON_CHESTPLATE, 1);
        player.getInventory().setArmorContents(baselineArmor);

        ItemStack[] baselineExtra = new ItemStack[player.getInventory().getExtraContents().length];
        baselineExtra[0] = stack(Material.SHIELD, 1);
        player.getInventory().setExtraContents(baselineExtra);
        player.getInventory().setHeldItemSlot(1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0, false, true, true));

        List<String> oversizedExtra = new ArrayList<>(section(PLAYER_EXTRA_SIZE, Map.of(0, encoded(stack(Material.TOTEM_OF_UNDYING, 1)))));
        oversizedExtra.add(encoded(stack(Material.SHIELD, 1)));

        KitDefinition invalid = new KitDefinition(
                new KitId("oversized-extra"),
                "OversizedExtra",
                new KitInventory(
                        section(PLAYER_STORAGE_SIZE, Map.of(0, encoded(stack(Material.DIAMOND_SWORD, 1)))),
                        section(PLAYER_ARMOR_SIZE, Map.of(0, encoded(stack(Material.DIAMOND_BOOTS, 1)))),
                        List.copyOf(oversizedExtra),
                        3),
                List.of(new KitPotionEffect("minecraft:speed", 100, 1, false, true, true)),
                new KitRules(false, false, true, false),
                true);

        PaperKitLoadoutAdapter adapter = new PaperKitLoadoutAdapter();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> adapter.apply(player, invalid));
        assertTrue(exception.getMessage().contains("extra"));
        assertArrayEquals(baselineStorage, player.getInventory().getStorageContents());
        assertArrayEquals(baselineArmor, player.getInventory().getArmorContents());
        assertArrayEquals(baselineExtra, player.getInventory().getExtraContents());
        assertEquals(1, player.getInventory().getHeldItemSlot());
        assertPotionEffectsEqual(List.of(new PotionEffect(PotionEffectType.STRENGTH, 200, 0, false, true, true)), player);
    }

    private static List<String> emptySection(int size) {
        return section(size, Map.of());
    }

    private static List<String> section(int size, Map<Integer, String> valuesByIndex) {
        List<String> values = new ArrayList<>(Collections.nCopies(size, null));
        for (Map.Entry<Integer, String> entry : valuesByIndex.entrySet()) {
            values.set(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static ItemStack stack(Material material, int amount) {
        return new ItemStack(material, amount);
    }

    private static String encoded(ItemStack itemStack) {
        return Base64.getEncoder().encodeToString(itemStack.serializeAsBytes());
    }

    private static void assertPotionEffectsEqual(List<PotionEffect> expected, PlayerMock player) {
        List<PotionEffect> actual = player.getActivePotionEffects().stream()
                .sorted(Comparator.comparing(effect -> effect.getType().getKey().asString()))
                .toList();
        List<PotionEffect> sortedExpected = expected.stream()
                .sorted(Comparator.comparing(effect -> effect.getType().getKey().asString()))
                .toList();
        assertEquals(sortedExpected.size(), actual.size());
        for (int index = 0; index < sortedExpected.size(); index++) {
            PotionEffect expectedEffect = sortedExpected.get(index);
            PotionEffect actualEffect = actual.get(index);
            assertEquals(expectedEffect.getType(), actualEffect.getType());
            assertEquals(expectedEffect.getDuration(), actualEffect.getDuration());
            assertEquals(expectedEffect.getAmplifier(), actualEffect.getAmplifier());
            assertEquals(expectedEffect.isAmbient(), actualEffect.isAmbient());
            assertEquals(expectedEffect.hasParticles(), actualEffect.hasParticles());
            assertEquals(expectedEffect.hasIcon(), actualEffect.hasIcon());
        }
    }
}
