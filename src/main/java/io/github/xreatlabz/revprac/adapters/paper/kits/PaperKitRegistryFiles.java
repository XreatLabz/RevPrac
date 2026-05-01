package io.github.xreatlabz.revprac.adapters.paper.kits;

import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitInventory;
import io.github.xreatlabz.revprac.domain.kits.KitPotionEffect;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

public final class PaperKitRegistryFiles {

    private static final String REGISTRY_FILE_NAME = "kits.yml";
    // Paper and MockBukkit expose player inventory sections as 36 storage slots, 4 armor slots, and 1 offhand slot.
    private static final int PLAYER_STORAGE_SIZE = 36;
    private static final int PLAYER_ARMOR_SIZE = 4;
    private static final int PLAYER_EXTRA_SIZE = 1;

    private final Path dataDirectory;

    public PaperKitRegistryFiles(Path dataDirectory) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
    }

    public List<KitDefinition> load() {
        Path registryFile = registryFile();
        if (Files.notExists(registryFile)) {
            return List.of();
        }

        YamlConfiguration configuration = loadConfiguration(registryFile);
        if (!configuration.contains("kits")) {
            return List.of();
        }

        List<?> rawKits = requireList(configuration.get("kits"), "kits");
        List<KitDefinition> definitions = new ArrayList<>(rawKits.size());
        Set<KitId> ids = new HashSet<>();
        for (int index = 0; index < rawKits.size(); index++) {
            Map<?, ?> kitMap = requireMap(rawKits.get(index), "kits[" + index + "]");
            KitDefinition definition = toKitDefinition(kitMap, "kits[" + index + "]");
            if (!ids.add(definition.id())) {
                throw new IllegalArgumentException("Duplicate kit id: " + definition.id().value());
            }
            definitions.add(definition);
        }
        return List.copyOf(definitions);
    }

    public void save(List<KitDefinition> definitions) throws IOException {
        Objects.requireNonNull(definitions, "definitions");
        Files.createDirectories(dataDirectory);

        YamlConfiguration configuration = new YamlConfiguration();
        List<Map<String, Object>> serialized = new ArrayList<>(definitions.size());
        for (KitDefinition definition : definitions) {
            serialized.add(serializeKit(Objects.requireNonNull(definition, "definitions entry")));
        }
        configuration.set("kits", serialized);
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

    private static KitDefinition toKitDefinition(Map<?, ?> values, String path) {
        return new KitDefinition(
                new KitId(requireString(values, "id", path + ".id")),
                requireString(values, "display-name", path + ".display-name"),
                toInventory(requireMap(values.get("inventory"), path + ".inventory"), path + ".inventory"),
                toPotionEffects(requireList(values.get("potion-effects"), path + ".potion-effects"), path + ".potion-effects"),
                toRules(requireMap(values.get("rules"), path + ".rules"), path + ".rules"),
                requireBoolean(values, "enabled", path + ".enabled"));
    }

    private static KitInventory toInventory(Map<?, ?> values, String path) {
        List<String> storage = toNullableStringList(requireList(values.get("storage"), path + ".storage"), path + ".storage");
        List<String> armor = toNullableStringList(requireList(values.get("armor"), path + ".armor"), path + ".armor");
        List<String> extra = toNullableStringList(requireList(values.get("extra"), path + ".extra"), path + ".extra");
        requireSectionLength(storage, PLAYER_STORAGE_SIZE, path + ".storage");
        requireSectionLength(armor, PLAYER_ARMOR_SIZE, path + ".armor");
        requireSectionLength(extra, PLAYER_EXTRA_SIZE, path + ".extra");
        return new KitInventory(storage, armor, extra, requireInt(values, "selected-slot", path + ".selected-slot"));
    }

    private static List<KitPotionEffect> toPotionEffects(List<?> values, String path) {
        List<KitPotionEffect> effects = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Map<?, ?> effectMap = requireMap(values.get(index), path + "[" + index + "]");
            String effectKey = requireString(effectMap, "effect", path + "[" + index + "].effect");
            requireEffectKey(effectKey, path + "[" + index + "].effect");
            effects.add(new KitPotionEffect(
                    effectKey,
                    requireInt(effectMap, "duration-ticks", path + "[" + index + "].duration-ticks"),
                    requireInt(effectMap, "amplifier", path + "[" + index + "].amplifier"),
                    requireBoolean(effectMap, "ambient", path + "[" + index + "].ambient"),
                    requireBoolean(effectMap, "particles", path + "[" + index + "].particles"),
                    requireBoolean(effectMap, "icon", path + "[" + index + "].icon")));
        }
        return List.copyOf(effects);
    }

    private static KitRules toRules(Map<?, ?> values, String path) {
        return new KitRules(
                requireBoolean(values, "allow-building", path + ".allow-building"),
                requireBoolean(values, "allow-hunger", path + ".allow-hunger"),
                requireBoolean(values, "allow-natural-regeneration", path + ".allow-natural-regeneration"),
                requireBoolean(values, "ranked", path + ".ranked"));
    }

    private static Map<String, Object> serializeKit(KitDefinition definition) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("id", definition.id().value());
        serialized.put("display-name", definition.displayName());
        serialized.put("enabled", definition.enabled());
        serialized.put("inventory", serializeInventory(definition.inventory()));
        serialized.put("potion-effects", serializePotionEffects(definition.potionEffects()));
        serialized.put("rules", serializeRules(definition.rules()));
        return serialized;
    }

    private static Map<String, Object> serializeInventory(KitInventory inventory) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("selected-slot", inventory.selectedSlot());
        serialized.put("storage", new ArrayList<>(inventory.storage()));
        serialized.put("armor", new ArrayList<>(inventory.armor()));
        serialized.put("extra", new ArrayList<>(inventory.extra()));
        return serialized;
    }

    private static List<Map<String, Object>> serializePotionEffects(List<KitPotionEffect> potionEffects) {
        List<Map<String, Object>> serialized = new ArrayList<>(potionEffects.size());
        for (KitPotionEffect potionEffect : potionEffects) {
            Map<String, Object> effect = new LinkedHashMap<>();
            effect.put("effect", potionEffect.effectKey());
            effect.put("duration-ticks", potionEffect.durationTicks());
            effect.put("amplifier", potionEffect.amplifier());
            effect.put("ambient", potionEffect.ambient());
            effect.put("particles", potionEffect.particles());
            effect.put("icon", potionEffect.icon());
            serialized.add(effect);
        }
        return serialized;
    }

    private static Map<String, Object> serializeRules(KitRules rules) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("allow-building", rules.allowBuilding());
        serialized.put("allow-hunger", rules.allowHunger());
        serialized.put("allow-natural-regeneration", rules.allowNaturalRegeneration());
        serialized.put("ranked", rules.ranked());
        return serialized;
    }

    private static List<String> toNullableStringList(List<?> values, String path) {
        List<String> decoded = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (value == null) {
                decoded.add(null);
            } else if (value instanceof String stringValue) {
                validateItemPayload(stringValue, path + "[" + index + "]");
                decoded.add(stringValue);
            } else {
                throw new IllegalArgumentException(path + "[" + index + "] must be a string or null");
            }
        }
        return decoded;
    }

    private static void validateItemPayload(String encodedItem, String path) {
        try {
            ItemStack.deserializeBytes(Base64.getDecoder().decode(encodedItem));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid item payload at " + path, exception);
        }
    }

    private static void requireEffectKey(String effectKey, String path) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(effectKey);
        if (namespacedKey == null) {
            throw new IllegalArgumentException("Invalid potion effect key at " + path + ": " + effectKey);
        }

        PotionEffectType effectType = Registry.EFFECT.get(namespacedKey);
        if (effectType == null) {
            throw new IllegalArgumentException("Unknown potion effect type at " + path + ": " + effectKey);
        }
    }

    private static void requireSectionLength(List<String> values, int expectedSize, String path) {
        if (values.size() != expectedSize) {
            throw new IllegalArgumentException(path + " must contain exactly " + expectedSize + " entries");
        }
    }

    private static List<?> requireList(Object value, String path) {
        if (value == null) {
            throw new IllegalArgumentException(path + " is required");
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
}
