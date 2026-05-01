package io.github.xreatlabz.revprac.adapters.paper.arenas;

import io.github.xreatlabz.revprac.domain.arenas.ArenaCuboid;
import io.github.xreatlabz.revprac.domain.arenas.ArenaDefinition;
import io.github.xreatlabz.revprac.domain.arenas.ArenaId;
import io.github.xreatlabz.revprac.domain.arenas.ArenaSpawnPoint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PaperArenaRegistryFiles {

    private static final String REGISTRY_FILE_NAME = "arenas.yml";

    private final Path dataDirectory;

    public PaperArenaRegistryFiles(Path dataDirectory) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
    }

    public List<ArenaDefinition> load() {
        Path registryFile = registryFile();
        if (Files.notExists(registryFile)) {
            return List.of();
        }

        YamlConfiguration configuration = loadConfiguration(registryFile);
        if (!configuration.contains("arenas")) {
            return List.of();
        }

        List<?> rawArenas = requireList(configuration.get("arenas"), "arenas");
        List<ArenaDefinition> definitions = new ArrayList<>(rawArenas.size());
        Set<ArenaId> ids = new HashSet<>();
        for (int index = 0; index < rawArenas.size(); index++) {
            Map<?, ?> arenaMap = requireMap(rawArenas.get(index), "arenas[" + index + "]");
            ArenaDefinition definition = toArenaDefinition(arenaMap, "arenas[" + index + "]");
            if (!ids.add(definition.id())) {
                throw new IllegalArgumentException("Duplicate arena id: " + definition.id().value());
            }
            definitions.add(definition);
        }
        return List.copyOf(definitions);
    }

    public void save(List<ArenaDefinition> definitions) throws IOException {
        Objects.requireNonNull(definitions, "definitions");
        Files.createDirectories(dataDirectory);

        YamlConfiguration configuration = new YamlConfiguration();
        List<Map<String, Object>> serialized = new ArrayList<>(definitions.size());
        for (ArenaDefinition definition : definitions) {
            serialized.add(serializeArena(Objects.requireNonNull(definition, "definitions entry")));
        }
        configuration.set("arenas", serialized);
        configuration.save(registryFile().toFile());
    }

    private Path registryFile() {
        return dataDirectory.resolve(REGISTRY_FILE_NAME);
    }

    private static YamlConfiguration loadConfiguration(Path registryFile) {
        try {
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.load(registryFile.toFile());
            return configuration;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + registryFile, exception);
        } catch (InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Invalid YAML in " + registryFile, exception);
        }
    }

    private static ArenaDefinition toArenaDefinition(Map<?, ?> values, String path) {
        return new ArenaDefinition(
                new ArenaId(requireString(values, "id", path + ".id")),
                requireString(values, "display-name", path + ".display-name"),
                toCuboid(requireMap(values.get("bounds"), path + ".bounds"), path + ".bounds"),
                toSpawnPoint(requireMap(values.get("spawn-one"), path + ".spawn-one"), path + ".spawn-one"),
                toSpawnPoint(requireMap(values.get("spawn-two"), path + ".spawn-two"), path + ".spawn-two"),
                requireBoolean(values, "enabled", path + ".enabled"));
    }

    private static ArenaCuboid toCuboid(Map<?, ?> values, String path) {
        return new ArenaCuboid(
                requireString(values, "world", path + ".world"),
                requireInt(values, "min-x", path + ".min-x"),
                requireInt(values, "min-y", path + ".min-y"),
                requireInt(values, "min-z", path + ".min-z"),
                requireInt(values, "max-x", path + ".max-x"),
                requireInt(values, "max-y", path + ".max-y"),
                requireInt(values, "max-z", path + ".max-z"));
    }

    private static ArenaSpawnPoint toSpawnPoint(Map<?, ?> values, String path) {
        return new ArenaSpawnPoint(
                requireString(values, "world", path + ".world"),
                requireDouble(values, "x", path + ".x"),
                requireDouble(values, "y", path + ".y"),
                requireDouble(values, "z", path + ".z"),
                requireFloat(values, "yaw", path + ".yaw"),
                requireFloat(values, "pitch", path + ".pitch"));
    }

    private static Map<String, Object> serializeArena(ArenaDefinition definition) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("id", definition.id().value());
        serialized.put("display-name", definition.displayName());
        serialized.put("enabled", definition.enabled());
        serialized.put("bounds", serializeBounds(definition.bounds()));
        serialized.put("spawn-one", serializeSpawn(definition.spawnOne()));
        serialized.put("spawn-two", serializeSpawn(definition.spawnTwo()));
        return serialized;
    }

    private static Map<String, Object> serializeBounds(ArenaCuboid bounds) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("world", bounds.worldKey());
        serialized.put("min-x", bounds.minX());
        serialized.put("min-y", bounds.minY());
        serialized.put("min-z", bounds.minZ());
        serialized.put("max-x", bounds.maxX());
        serialized.put("max-y", bounds.maxY());
        serialized.put("max-z", bounds.maxZ());
        return serialized;
    }

    private static Map<String, Object> serializeSpawn(ArenaSpawnPoint spawnPoint) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("world", spawnPoint.worldKey());
        serialized.put("x", spawnPoint.x());
        serialized.put("y", spawnPoint.y());
        serialized.put("z", spawnPoint.z());
        serialized.put("yaw", spawnPoint.yaw());
        serialized.put("pitch", spawnPoint.pitch());
        return serialized;
    }

    private static List<?> requireList(Object value, String path) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        throw new IllegalArgumentException(path + " must be a YAML list");
    }

    private static Map<?, ?> requireMap(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException(path + " must be a YAML object");
    }

    private static String requireString(Map<?, ?> values, String key, String path) {
        Object value = values.get(key);
        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException(path + " is required");
        }
        return stringValue;
    }

    private static boolean requireBoolean(Map<?, ?> values, String key, String path) {
        Object value = values.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new IllegalArgumentException(path + " is required");
    }

    private static int requireInt(Map<?, ?> values, String key, String path) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            double numericValue = number.doubleValue();
            if (Double.isFinite(numericValue)
                    && numericValue >= Integer.MIN_VALUE
                    && numericValue <= Integer.MAX_VALUE
                    && Math.rint(numericValue) == numericValue) {
                return (int) numericValue;
            }
            throw new IllegalArgumentException(path + " must be an integer");
        }
        throw new IllegalArgumentException(path + " is required");
    }

    private static double requireDouble(Map<?, ?> values, String key, String path) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException(path + " is required");
    }

    private static float requireFloat(Map<?, ?> values, String key, String path) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        throw new IllegalArgumentException(path + " is required");
    }
}
