package io.github.xreatlabz.revprac.adapters.paper.arenas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.xreatlabz.revprac.domain.arenas.ArenaCuboid;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PaperArenaRegistryFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void missingRegistryFileLoadsAsEmptyListAndCanBeSaved() throws Exception {
        PaperArenaRegistryFiles files = new PaperArenaRegistryFiles(tempDir);

        assertEquals(List.of(), files.load());

        List<ArenaDefinition> definitions = List.of(arena("bridge", "Bridge"), arena("cave", "Cave"));
        files.save(definitions);

        Path registryFile = tempDir.resolve("arenas.yml");
        assertTrue(Files.exists(registryFile), "Save should create arenas.yml");
        assertEquals(List.of("bridge", "cave"), files.load().stream().map(definition -> definition.id().value()).toList());
    }

    @Test
    void validArenaYamlLoadsIntoArenaDefinitions() throws Exception {
        Files.writeString(
                tempDir.resolve("arenas.yml"),
                """
                arenas:
                  - id: bridge
                    display-name: Bridge
                    enabled: true
                    bounds:
                      world: minecraft:arena
                      min-x: -10
                      min-y: 64
                      min-z: -10
                      max-x: 10
                      max-y: 80
                      max-z: 10
                    spawn-one:
                      world: minecraft:arena
                      x: -5.5
                      y: 65.0
                      z: 0.5
                      yaw: 90.0
                      pitch: 0.0
                    spawn-two:
                      world: minecraft:arena
                      x: 5.5
                      y: 65.0
                      z: 0.5
                      yaw: -90.0
                      pitch: 0.0
                """);

        PaperArenaRegistryFiles files = new PaperArenaRegistryFiles(tempDir);

        List<ArenaDefinition> loaded = files.load();

        assertEquals(
                List.of(new ArenaDefinition(
                        new ArenaId("bridge"),
                        "Bridge",
                        new ArenaCuboid("minecraft:arena", -10, 64, -10, 10, 80, 10),
                        new ArenaSpawnPoint("minecraft:arena", -5.5d, 65.0d, 0.5d, 90.0f, 0.0f),
                        new ArenaSpawnPoint("minecraft:arena", 5.5d, 65.0d, 0.5d, -90.0f, 0.0f),
                        true)),
                loaded);
    }

    @Test
    void saveThenLoadRoundTripsDefinitionsWithoutReorderingIds() throws Exception {
        PaperArenaRegistryFiles files = new PaperArenaRegistryFiles(tempDir);
        List<ArenaDefinition> definitions = List.of(arena("zeta", "Zeta"), arena("alpha", "Alpha"), arena("mid", "Mid"));

        files.save(definitions);

        List<ArenaDefinition> reloaded = files.load();
        assertEquals(definitions, reloaded);
        assertEquals(List.of("zeta", "alpha", "mid"), reloaded.stream().map(definition -> definition.id().value()).toList());
    }

    @Test
    void duplicateIdsFailClosed() throws Exception {
        Files.writeString(
                tempDir.resolve("arenas.yml"),
                """
                arenas:
                  - id: bridge
                    display-name: Bridge A
                    enabled: true
                    bounds:
                      world: minecraft:arena
                      min-x: 0
                      min-y: 64
                      min-z: 0
                      max-x: 10
                      max-y: 80
                      max-z: 10
                    spawn-one:
                      world: minecraft:arena
                      x: 1.0
                      y: 65.0
                      z: 1.0
                      yaw: 0.0
                      pitch: 0.0
                    spawn-two:
                      world: minecraft:arena
                      x: 9.0
                      y: 65.0
                      z: 9.0
                      yaw: 180.0
                      pitch: 0.0
                  - id: bridge
                    display-name: Bridge B
                    enabled: true
                    bounds:
                      world: minecraft:arena
                      min-x: 20
                      min-y: 64
                      min-z: 20
                      max-x: 30
                      max-y: 80
                      max-z: 30
                    spawn-one:
                      world: minecraft:arena
                      x: 21.0
                      y: 65.0
                      z: 21.0
                      yaw: 0.0
                      pitch: 0.0
                    spawn-two:
                      world: minecraft:arena
                      x: 29.0
                      y: 65.0
                      z: 29.0
                      yaw: 180.0
                      pitch: 0.0
                """);

        PaperArenaRegistryFiles files = new PaperArenaRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("Duplicate arena id: bridge"));
    }

    @Test
    void missingRequiredFieldsIncludeYamlPathInExceptionMessage() throws Exception {
        Files.writeString(
                tempDir.resolve("arenas.yml"),
                """
                arenas:
                  - id: bridge
                    enabled: true
                    bounds:
                      world: minecraft:arena
                      min-x: 0
                      min-y: 64
                      min-z: 0
                      max-x: 10
                      max-y: 80
                      max-z: 10
                    spawn-one:
                      world: minecraft:arena
                      x: 1.0
                      y: 65.0
                      z: 1.0
                      yaw: 0.0
                      pitch: 0.0
                    spawn-two:
                      world: minecraft:arena
                      x: 9.0
                      y: 65.0
                      z: 9.0
                      yaw: 180.0
                      pitch: 0.0
                """);

        PaperArenaRegistryFiles files = new PaperArenaRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("arenas[0].display-name"));
    }

    @Test
    void invalidYamlContentDoesNotPublishPartialDefinitions() throws Exception {
        Files.writeString(
                tempDir.resolve("arenas.yml"),
                """
                arenas:
                  - id: first
                    display-name: First
                    enabled: true
                    bounds:
                      world: minecraft:arena
                      min-x: 0
                      min-y: 64
                      min-z: 0
                      max-x: 10
                      max-y: 80
                      max-z: 10
                    spawn-one:
                      world: minecraft:arena
                      x: 1.0
                      y: 65.0
                      z: 1.0
                      yaw: 0.0
                      pitch: 0.0
                    spawn-two:
                      world: minecraft:arena
                      x: 9.0
                      y: 65.0
                      z: 9.0
                      yaw: 180.0
                      pitch: 0.0
                  - id: second
                    display-name:
                    enabled: true
                """);

        PaperArenaRegistryFiles files = new PaperArenaRegistryFiles(tempDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, files::load);
        assertTrue(exception.getMessage().contains("arenas[1].display-name"));
    }

    private static ArenaDefinition arena(String id, String displayName) {
        return new ArenaDefinition(
                new ArenaId(id),
                displayName,
                new ArenaCuboid("minecraft:arena", 0, 64, 0, 20, 80, 20),
                new ArenaSpawnPoint("minecraft:arena", 2.5d, 65.0d, 2.5d, 0.0f, 0.0f),
                new ArenaSpawnPoint("minecraft:arena", 18.5d, 65.0d, 18.5d, 180.0f, 0.0f),
                true);
    }
}
