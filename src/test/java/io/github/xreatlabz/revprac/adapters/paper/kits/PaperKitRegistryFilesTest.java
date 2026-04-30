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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

final class PaperKitRegistryFilesTest {

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
        List<String> storage = section(PLAYER_STORAGE_SIZE, Map.of(0, sword, 2, apples));
        List<String> armor = section(PLAYER_ARMOR_SIZE, Map.of(0, boots, 1, leggings, 2, chestplate, 3, helmet));
        List<String> extra = section(PLAYER_EXTRA_SIZE, Map.of(0, totem));

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
                %s      armor:
                %s      extra:
                %s    potion-effects:
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
                """.formatted(yamlList(24, storage), yamlList(24, armor), yamlList(24, extra)));

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        List<KitDefinition> loaded = files.load();

        assertEquals(
                List.of(new KitDefinition(
                        new KitId("nodebuff"),
                        "NoDebuff",
                        new KitInventory(storage, armor, extra, 1),
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
        List<String> firstStorage = section(PLAYER_STORAGE_SIZE, Map.of(0, encoded(stack(Material.STONE_SWORD, 1))));
        List<String> secondStorage = section(PLAYER_STORAGE_SIZE, Map.of(0, encoded(stack(Material.BOW, 1))));

        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: Primary
                    enabled: true
                    inventory:
                      selected-slot: 0
                      storage:
                %s      armor:
                %s      extra:
                %s    potion-effects: []
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
                      storage:
                %s      armor:
                %s      extra:
                %s    potion-effects: []
                    rules:
                      allow-building: false
                      allow-hunger: true
                      allow-natural-regeneration: false
                      ranked: true
                """.formatted(
                        yamlList(24, firstStorage),
                        yamlList(24, emptySection(PLAYER_ARMOR_SIZE)),
                        yamlList(24, emptySection(PLAYER_EXTRA_SIZE)),
                        yamlList(24, secondStorage),
                        yamlList(24, emptySection(PLAYER_ARMOR_SIZE)),
                        yamlList(24, emptySection(PLAYER_EXTRA_SIZE))));

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("Duplicate kit id: nodebuff"));
    }

    @Test
    void missingRequiredFieldsIncludeYamlPathInExceptionMessage() throws Exception {
        List<String> storage = section(PLAYER_STORAGE_SIZE, Map.of(0, encoded(stack(Material.DIAMOND_SWORD, 1))));

        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: NoDebuff
                    enabled: true
                    inventory:
                      storage:
                %s      armor:
                %s      extra:
                %s    potion-effects: []
                    rules:
                      allow-building: false
                      allow-hunger: false
                      allow-natural-regeneration: true
                      ranked: false
                """.formatted(
                        yamlList(24, storage),
                        yamlList(24, emptySection(PLAYER_ARMOR_SIZE)),
                        yamlList(24, emptySection(PLAYER_EXTRA_SIZE))));

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("kits[0].inventory.selected-slot"));
    }

    @Test
    void invalidYamlContentDoesNotPublishPartialDefinitions() throws Exception {
        List<String> storage = section(
                PLAYER_STORAGE_SIZE,
                Map.of(0, encoded(stack(Material.DIAMOND_SWORD, 1)), 2, encoded(stack(Material.BOW, 1))));

        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: NoDebuff
                    enabled: true
                    inventory:
                      selected-slot: 0
                      storage:
                %s      armor:
                %s      extra:
                %s    potion-effects: []
                    rules:
                      allow-building: false
                      allow-hunger: false
                      allow-natural-regeneration: true
                      ranked: false
                  - id: soup
                    enabled: true
                """.formatted(
                        yamlList(24, storage),
                        yamlList(24, emptySection(PLAYER_ARMOR_SIZE)),
                        yamlList(24, emptySection(PLAYER_EXTRA_SIZE))));

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("kits[1].display-name"));
    }

    @Test
    void invalidItemPayloadFailsWithYamlPath() throws Exception {
        List<String> storage = section(PLAYER_STORAGE_SIZE, Map.of(0, "AQIDBA=="));

        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: NoDebuff
                    enabled: true
                    inventory:
                      selected-slot: 0
                      storage:
                %s      armor:
                %s      extra:
                %s    potion-effects: []
                    rules:
                      allow-building: false
                      allow-hunger: false
                      allow-natural-regeneration: true
                      ranked: false
                """.formatted(
                        yamlList(24, storage),
                        yamlList(24, emptySection(PLAYER_ARMOR_SIZE)),
                        yamlList(24, emptySection(PLAYER_EXTRA_SIZE))));

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("kits[0].inventory.storage[0]"));
    }

    @Test
    void unknownEffectKeyFailsWithYamlPath() throws Exception {
        List<String> storage = section(PLAYER_STORAGE_SIZE, Map.of(0, encoded(stack(Material.DIAMOND_SWORD, 1))));

        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: NoDebuff
                    enabled: true
                    inventory:
                      selected-slot: 0
                      storage:
                %s      armor:
                %s      extra:
                %s    potion-effects:
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
                """.formatted(
                        yamlList(24, storage),
                        yamlList(24, emptySection(PLAYER_ARMOR_SIZE)),
                        yamlList(24, emptySection(PLAYER_EXTRA_SIZE))));

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("kits[0].potion-effects[0].effect"));
    }

    @Test
    void decimalSelectedSlotFailsWithFieldPath() throws Exception {
        List<String> storage = section(PLAYER_STORAGE_SIZE, Map.of(0, encoded(stack(Material.DIAMOND_SWORD, 1))));

        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: NoDebuff
                    enabled: true
                    inventory:
                      selected-slot: 1.5
                      storage:
                %s      armor:
                %s      extra:
                %s    potion-effects: []
                    rules:
                      allow-building: false
                      allow-hunger: false
                      allow-natural-regeneration: true
                      ranked: false
                """.formatted(
                        yamlList(24, storage),
                        yamlList(24, emptySection(PLAYER_ARMOR_SIZE)),
                        yamlList(24, emptySection(PLAYER_EXTRA_SIZE))));

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("kits[0].inventory.selected-slot"));
    }

    @Test
    void oversizedSectionFailsWithYamlPath() throws Exception {
        List<String> oversizedExtra = new ArrayList<>(emptySection(PLAYER_EXTRA_SIZE));
        oversizedExtra.add(encoded(stack(Material.SHIELD, 1)));
        List<String> storage = section(PLAYER_STORAGE_SIZE, Map.of(0, encoded(stack(Material.DIAMOND_SWORD, 1))));

        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: NoDebuff
                    enabled: true
                    inventory:
                      selected-slot: 0
                      storage:
                %s      armor:
                %s      extra:
                %s    potion-effects: []
                    rules:
                      allow-building: false
                      allow-hunger: false
                      allow-natural-regeneration: true
                      ranked: false
                """.formatted(
                        yamlList(24, storage),
                        yamlList(24, emptySection(PLAYER_ARMOR_SIZE)),
                        yamlList(24, oversizedExtra)));

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("kits[0].inventory.extra"));
    }

    private static KitDefinition kit(String id, String displayName) {
        return new KitDefinition(
                new KitId(id),
                displayName,
                new KitInventory(
                        section(
                                PLAYER_STORAGE_SIZE,
                                Map.of(0, encoded(stack(Material.IRON_SWORD, 1)), 2, encoded(stack(Material.FISHING_ROD, 1)))),
                        section(
                                PLAYER_ARMOR_SIZE,
                                Map.of(
                                        0, encoded(stack(Material.IRON_BOOTS, 1)),
                                        1, encoded(stack(Material.IRON_LEGGINGS, 1)),
                                        2, encoded(stack(Material.IRON_CHESTPLATE, 1)),
                                        3, encoded(stack(Material.IRON_HELMET, 1)))),
                        section(PLAYER_EXTRA_SIZE, Map.of(0, encoded(stack(Material.TOTEM_OF_UNDYING, 1)))),
                        2),
                List.of(new KitPotionEffect("minecraft:speed", 600, 1, false, true, true)),
                new KitRules(false, false, true, false),
                true);
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

    private static String yamlList(int indent, List<String> values) {
        String prefix = " ".repeat(indent);
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            builder.append(prefix).append("- ");
            if (value == null) {
                builder.append("null");
            } else {
                builder.append('"').append(value).append('"');
            }
            builder.append('\n');
        }
        return builder.toString();
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
