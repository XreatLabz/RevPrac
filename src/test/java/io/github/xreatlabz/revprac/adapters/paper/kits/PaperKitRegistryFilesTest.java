package io.github.xreatlabz.revprac.adapters.paper.kits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitInventory;
import io.github.xreatlabz.revprac.domain.kits.KitPotionEffect;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

final class PaperKitRegistryFilesTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void missingRegistryFileLoadsAsEmptyListAndCanBeSaved() throws Exception {
        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        assertEquals(List.of(), files.load());

        List<KitDefinition> definitions = List.of(kit("nodebuff", "NoDebuff"), kit("sumo", "Sumo"));
        files.save(definitions);

        Path registryFile = tempDir.resolve("kits.yml");
        assertTrue(Files.exists(registryFile), "Save should create kits.yml");
        assertEquals(List.of("nodebuff", "sumo"), files.load().stream().map(definition -> definition.id().value()).toList());
    }

    @Test
    void validKitYamlLoadsIntoKitDefinitions() throws Exception {
        String sword = encoded(stack(Material.DIAMOND_SWORD, 1));
        String apples = encoded(stack(Material.GOLDEN_APPLE, 3));
        String boots = encoded(stack(Material.DIAMOND_BOOTS, 1));
        String leggings = encoded(stack(Material.DIAMOND_LEGGINGS, 1));
        String chestplate = encoded(stack(Material.DIAMOND_CHESTPLATE, 1));
        String helmet = encoded(stack(Material.DIAMOND_HELMET, 1));
        String totem = encoded(stack(Material.TOTEM_OF_UNDYING, 1));

        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: NoDebuff
                    enabled: true
                    inventory:
                      selected-slot: 1
                      storage:
                        - "%s"
                        - null
                        - "%s"
                      armor:
                        - "%s"
                        - "%s"
                        - "%s"
                        - "%s"
                      extra:
                        - "%s"
                    potion-effects:
                      - effect: minecraft:speed
                        duration-ticks: 1200
                        amplifier: 1
                        ambient: false
                        particles: true
                        icon: true
                    rules:
                      allow-building: false
                      allow-hunger: false
                      allow-natural-regeneration: true
                      ranked: false
                """.formatted(sword, apples, boots, leggings, chestplate, helmet, totem));

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        List<KitDefinition> loaded = files.load();

        assertEquals(
                List.of(new KitDefinition(
                        new KitId("nodebuff"),
                        "NoDebuff",
                        new KitInventory(
                                Arrays.asList(sword, null, apples),
                                List.of(boots, leggings, chestplate, helmet),
                                List.of(totem),
                                1),
                        List.of(new KitPotionEffect("minecraft:speed", 1200, 1, false, true, true)),
                        new KitRules(false, false, true, false),
                        true)),
                loaded);
    }

    @Test
    void saveThenLoadRoundTripsDefinitionsWithoutReorderingIds() throws Exception {
        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);
        List<KitDefinition> definitions = List.of(kit("zeta", "Zeta"), kit("alpha", "Alpha"), kit("mid", "Mid"));

        files.save(definitions);

        List<KitDefinition> reloaded = files.load();
        assertEquals(definitions, reloaded);
        assertEquals(List.of("zeta", "alpha", "mid"), reloaded.stream().map(definition -> definition.id().value()).toList());
    }

    @Test
    void duplicateIdsFailClosed() throws Exception {
        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: Primary
                    enabled: true
                    inventory:
                      selected-slot: 0
                      storage: ["%s"]
                      armor: []
                      extra: []
                    potion-effects: []
                    rules:
                      allow-building: false
                      allow-hunger: false
                      allow-natural-regeneration: true
                      ranked: false
                  - id: nodebuff
                    display-name: Secondary
                    enabled: false
                    inventory:
                      selected-slot: 0
                      storage: ["%s"]
                      armor: []
                      extra: []
                    potion-effects: []
                    rules:
                      allow-building: false
                      allow-hunger: true
                      allow-natural-regeneration: false
                      ranked: true
                """.formatted(encoded(stack(Material.STONE_SWORD, 1)), encoded(stack(Material.BOW, 1))));

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("Duplicate kit id: nodebuff"));
    }

    @Test
    void missingRequiredFieldsIncludeYamlPathInExceptionMessage() throws Exception {
        String sword = encoded(stack(Material.DIAMOND_SWORD, 1));
        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: NoDebuff
                    enabled: true
                    inventory:
                      storage: ["%s"]
                      armor: []
                      extra: []
                    potion-effects: []
                    rules:
                      allow-building: false
                      allow-hunger: false
                      allow-natural-regeneration: true
                      ranked: false
                """.formatted(sword));

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("kits[0].inventory.selected-slot"));
    }

    @Test
    void invalidYamlContentDoesNotPublishPartialDefinitions() throws Exception {
        String sword = encoded(stack(Material.DIAMOND_SWORD, 1));
        String bow = encoded(stack(Material.BOW, 1));
        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: NoDebuff
                    enabled: true
                    inventory:
                      selected-slot: 0
                      storage: ["%s", null, "%s"]
                      armor: []
                      extra: []
                    potion-effects: []
                    rules:
                      allow-building: false
                      allow-hunger: false
                      allow-natural-regeneration: true
                      ranked: false
                  - id: soup
                    enabled: true
                """.formatted(sword, bow));

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("kits[1].display-name"));
    }

    @Test
    void invalidItemPayloadFailsWithYamlPath() throws Exception {
        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: NoDebuff
                    enabled: true
                    inventory:
                      selected-slot: 0
                      storage: ["AQIDBA=="]
                      armor: []
                      extra: []
                    potion-effects: []
                    rules:
                      allow-building: false
                      allow-hunger: false
                      allow-natural-regeneration: true
                      ranked: false
                """);

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("kits[0].inventory.storage[0]"));
    }

    @Test
    void unknownEffectKeyFailsWithYamlPath() throws Exception {
        String sword = encoded(stack(Material.DIAMOND_SWORD, 1));
        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: NoDebuff
                    enabled: true
                    inventory:
                      selected-slot: 0
                      storage: ["%s"]
                      armor: []
                      extra: []
                    potion-effects:
                      - effect: minecraft:not_real
                        duration-ticks: 1200
                        amplifier: 1
                        ambient: false
                        particles: true
                        icon: true
                    rules:
                      allow-building: false
                      allow-hunger: false
                      allow-natural-regeneration: true
                      ranked: false
                """.formatted(sword));

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("kits[0].potion-effects[0].effect"));
    }

    @Test
    void decimalSelectedSlotFailsWithFieldPath() throws Exception {
        String sword = encoded(stack(Material.DIAMOND_SWORD, 1));
        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: NoDebuff
                    enabled: true
                    inventory:
                      selected-slot: 1.5
                      storage: ["%s"]
                      armor: []
                      extra: []
                    potion-effects: []
                    rules:
                      allow-building: false
                      allow-hunger: false
                      allow-natural-regeneration: true
                      ranked: false
                """.formatted(sword));

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("kits[0].inventory.selected-slot"));
    }

    private static KitDefinition kit(String id, String displayName) {
        return new KitDefinition(
                new KitId(id),
                displayName,
                new KitInventory(
                        Arrays.asList(
                                encoded(stack(Material.IRON_SWORD, 1)),
                                null,
                                encoded(stack(Material.FISHING_ROD, 1))),
                        List.of(
                                encoded(stack(Material.IRON_BOOTS, 1)),
                                encoded(stack(Material.IRON_LEGGINGS, 1)),
                                encoded(stack(Material.IRON_CHESTPLATE, 1)),
                                encoded(stack(Material.IRON_HELMET, 1))),
                        List.of(encoded(stack(Material.TOTEM_OF_UNDYING, 1))),
                        2),
                List.of(new KitPotionEffect("minecraft:speed", 600, 1, false, true, true)),
                new KitRules(false, false, true, false),
                true);
    }

    private static ItemStack stack(Material material, int amount) {
        if (Bukkit.getServer() == null) {
            MockBukkit.mock();
        }
        return new ItemStack(material, amount);
    }

    private static String encoded(ItemStack itemStack) {
        return Base64.getEncoder().encodeToString(itemStack.serializeAsBytes());
    }
}
