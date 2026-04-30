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
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PaperKitRegistryFilesTest {

    @TempDir
    Path tempDir;

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
                        - c3RvcmFnZS0w
                        - null
                        - c3RvcmFnZS0y
                      armor:
                        - YXJtb3ItMA==
                        - YXJtb3ItMQ==
                        - YXJtb3ItMg==
                        - YXJtb3ItMw==
                      extra:
                        - ZXh0cmEtMA==
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
                """);

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        List<KitDefinition> loaded = files.load();

        assertEquals(
                List.of(new KitDefinition(
                        new KitId("nodebuff"),
                        "NoDebuff",
                        new KitInventory(
                                Arrays.asList("c3RvcmFnZS0w", null, "c3RvcmFnZS0y"),
                                List.of("YXJtb3ItMA==", "YXJtb3ItMQ==", "YXJtb3ItMg==", "YXJtb3ItMw=="),
                                List.of("ZXh0cmEtMA=="),
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
                      storage: [a]
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
                      storage: [b]
                      armor: []
                      extra: []
                    potion-effects: []
                    rules:
                      allow-building: false
                      allow-hunger: true
                      allow-natural-regeneration: false
                      ranked: true
                """);

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("Duplicate kit id: nodebuff"));
    }

    @Test
    void missingRequiredFieldsIncludeYamlPathInExceptionMessage() throws Exception {
        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: NoDebuff
                    enabled: true
                    inventory:
                      storage: [a]
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
        assertTrue(exception.getMessage().contains("kits[0].inventory.selected-slot"));
    }

    @Test
    void invalidYamlContentDoesNotPublishPartialDefinitions() throws Exception {
        Files.writeString(
                tempDir.resolve("kits.yml"),
                """
                kits:
                  - id: nodebuff
                    display-name: NoDebuff
                    enabled: true
                    inventory:
                      selected-slot: 0
                      storage: [a, null, b]
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
                """);

        PaperKitRegistryFiles files = new PaperKitRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("kits[1].display-name"));
    }

    private static KitDefinition kit(String id, String displayName) {
        return new KitDefinition(
                new KitId(id),
                displayName,
                new KitInventory(
                        Arrays.asList("storage-" + id, null, "rod-" + id),
                        List.of("helmet-" + id, "chestplate-" + id, "leggings-" + id, "boots-" + id),
                        List.of("totem-" + id),
                        2),
                List.of(new KitPotionEffect("minecraft:speed", 600, 1, false, true, true)),
                new KitRules(false, false, true, false),
                true);
    }
}
