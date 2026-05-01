package io.github.xreatlabz.revprac.domain.kits;

import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static io.github.xreatlabz.revprac.ContractTestSupport.recordComponentValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class KitDefinitionContractTest {

    private static final String DOMAIN_KITS_DIR = "src/main/java/io/github/xreatlabz/revprac/domain/kits";
    private static final String KIT_ID_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitId";
    private static final String KIT_INVENTORY_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitInventory";
    private static final String KIT_POTION_EFFECT_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitPotionEffect";
    private static final String KIT_RULES_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitRules";
    private static final String KIT_DEFINITION_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitDefinition";

    @Test
    void kitContractsUseImmutableRecordsAndNormalizeIds() throws ReflectiveOperationException {
        Class<?> kitIdType = loadClass(KIT_ID_TYPE);
        Class<?> kitInventoryType = loadClass(KIT_INVENTORY_TYPE);
        Class<?> kitPotionEffectType = loadClass(KIT_POTION_EFFECT_TYPE);
        Class<?> kitRulesType = loadClass(KIT_RULES_TYPE);
        Class<?> kitDefinitionType = loadClass(KIT_DEFINITION_TYPE);

        assertTrue(kitIdType.isRecord(), "KitId should be a record");
        assertTrue(kitInventoryType.isRecord(), "KitInventory should be a record");
        assertTrue(kitPotionEffectType.isRecord(), "KitPotionEffect should be a record");
        assertTrue(kitRulesType.isRecord(), "KitRules should be a record");
        assertTrue(kitDefinitionType.isRecord(), "KitDefinition should be a record");

        Object kitId = instantiateRecord(kitIdType, Map.of("value", "  NoDebuff-Ranked  "));
        assertEquals("nodebuff-ranked", recordComponentValue(kitId, "value"));

        Constructor<?> kitIdConstructor = kitIdType.getDeclaredConstructor(String.class);
        kitIdConstructor.setAccessible(true);
        assertIllegalArgument(() -> kitIdConstructor.newInstance(" "), "KitId should reject blank ids");
        assertIllegalArgument(() -> kitIdConstructor.newInstance("bad id"), "KitId should reject spaces");
        assertIllegalArgument(() -> kitIdConstructor.newInstance("bad.id"), "KitId should reject punctuation outside the allowed regex");
        assertIllegalArgument(
                () -> kitIdConstructor.newInstance("_starts-with-underscore"),
                "KitId should require the first character to be alphanumeric");
    }

    @Test
    void kitInventoryPreservesNullSlotsAndDefensivelyCopiesLists() {
        Class<?> kitInventoryType = loadClass(KIT_INVENTORY_TYPE);

        List<String> storage = new ArrayList<>(Arrays.asList("sword-bytes", null, "rod-bytes"));
        List<String> armor = new ArrayList<>(Arrays.asList(null, "chest-bytes", null, "boots-bytes"));
        List<String> extra = new ArrayList<>(Arrays.asList("offhand-bytes", null));

        Object inventory = instantiateRecord(
                kitInventoryType,
                kitInventoryValues(storage, armor, extra, 4));

        storage.set(0, "mutated");
        armor.set(1, "mutated");
        extra.set(0, "mutated");

        List<?> storedStorage = assertInstanceOf(List.class, recordComponentValue(inventory, "storage"));
        List<?> storedArmor = assertInstanceOf(List.class, recordComponentValue(inventory, "armor"));
        List<?> storedExtra = assertInstanceOf(List.class, recordComponentValue(inventory, "extra"));

        assertEquals(Arrays.asList("sword-bytes", null, "rod-bytes"), storedStorage);
        assertEquals(Arrays.asList(null, "chest-bytes", null, "boots-bytes"), storedArmor);
        assertEquals(Arrays.asList("offhand-bytes", null), storedExtra);
        assertThrows(UnsupportedOperationException.class, () -> mutateList(storedStorage));
        assertThrows(UnsupportedOperationException.class, () -> appendToList(storedArmor));
        assertThrows(UnsupportedOperationException.class, () -> storedExtra.remove(0));
    }

    @Test
    void kitScalarsRejectImpossibleStateAndDefinitionCopiesEffects() throws ReflectiveOperationException {
        Class<?> kitInventoryType = loadClass(KIT_INVENTORY_TYPE);
        Class<?> kitPotionEffectType = loadClass(KIT_POTION_EFFECT_TYPE);
        Class<?> kitRulesType = loadClass(KIT_RULES_TYPE);
        Class<?> kitDefinitionType = loadClass(KIT_DEFINITION_TYPE);

        Constructor<?> inventoryConstructor =
                kitInventoryType.getDeclaredConstructor(List.class, List.class, List.class, int.class);
        inventoryConstructor.setAccessible(true);
        assertIllegalArgument(
                () -> inventoryConstructor.newInstance(List.of(), List.of(), List.of(), -1),
                "KitInventory should reject negative selectedSlot");
        assertIllegalArgument(
                () -> inventoryConstructor.newInstance(List.of(), List.of(), List.of(), 9),
                "KitInventory should reject selectedSlot above 8");

        Constructor<?> potionEffectConstructor =
                kitPotionEffectType.getDeclaredConstructor(String.class, int.class, int.class, boolean.class, boolean.class, boolean.class);
        potionEffectConstructor.setAccessible(true);
        assertIllegalArgument(
                () -> potionEffectConstructor.newInstance(" ", 40, 1, false, true, true),
                "KitPotionEffect should reject blank effect keys");
        assertIllegalArgument(
                () -> potionEffectConstructor.newInstance("minecraft:speed", -1, 1, false, true, true),
                "KitPotionEffect should reject negative durations");
        assertIllegalArgument(
                () -> potionEffectConstructor.newInstance("minecraft:speed", 40, -1, false, true, true),
                "KitPotionEffect should reject negative amplifiers");

        Object effect = instantiateRecord(
                kitPotionEffectType,
                kitPotionEffectValues("minecraft:speed", 40, 1, false, true, true));
        List<Object> potionEffects = new ArrayList<>();
        potionEffects.add(effect);

        Object rules = instantiateRecord(kitRulesType, kitRulesValues(false, true, false, false));
        Object inventory = instantiateRecord(kitInventoryType, kitInventoryValues(List.of(), List.of(), List.of(), 0));

        Constructor<?> definitionConstructor = kitDefinitionType.getDeclaredConstructor(
                loadClass(KIT_ID_TYPE), String.class, kitInventoryType, List.class, kitRulesType, boolean.class);
        definitionConstructor.setAccessible(true);
        assertIllegalArgument(
                () -> definitionConstructor.newInstance(
                        instantiateRecord(loadClass(KIT_ID_TYPE), Map.of("value", "nodebuff")),
                        " ",
                        inventory,
                        potionEffects,
                        rules,
                        true),
                "KitDefinition should reject blank display names");
        assertIllegalArgument(
                () -> definitionConstructor.newInstance(
                        instantiateRecord(loadClass(KIT_ID_TYPE), Map.of("value", "nodebuff")),
                        "NoDebuff",
                        inventory,
                        Arrays.asList(effect, null),
                        rules,
                        true),
                "KitDefinition should reject null potion effect entries");

        Object definition = definitionConstructor.newInstance(
                instantiateRecord(loadClass(KIT_ID_TYPE), Map.of("value", "nodebuff")),
                "NoDebuff",
                inventory,
                potionEffects,
                rules,
                true);

        potionEffects.clear();
        List<?> storedPotionEffects = assertInstanceOf(List.class, recordComponentValue(definition, "potionEffects"));
        assertEquals(List.of(effect), storedPotionEffects, "KitDefinition should defensively copy potion effects");
        assertThrows(UnsupportedOperationException.class, () -> appendToList(storedPotionEffects));
        assertThrows(UnsupportedOperationException.class, () -> storedPotionEffects.clear());
    }

    @Test
    void domainKitSourcesStayFreeOfBukkitAndPaperImports() throws IOException {
        Path domainDirectory = Path.of(DOMAIN_KITS_DIR);

        assertTrue(Files.isDirectory(domainDirectory), "Expected kit domain directory to exist: " + domainDirectory);

        try (Stream<Path> sources = Files.walk(domainDirectory)) {
            List<Path> javaSources = sources
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .toList();

            assertFalse(javaSources.isEmpty(), "Expected kit domain source files to exist");

            for (Path source : javaSources) {
                String contents = Files.readString(source);
                assertFalse(contents.contains("org.bukkit"), "Domain source must not import Bukkit: " + source);
                assertFalse(contents.contains("io.papermc.paper"), "Domain source must not import Paper: " + source);
            }
        }
    }

    private static Map<String, Object> kitInventoryValues(
            List<String> storage, List<String> armor, List<String> extra, int selectedSlot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("storage", storage);
        values.put("armor", armor);
        values.put("extra", extra);
        values.put("selectedSlot", selectedSlot);
        return values;
    }

    private static Map<String, Object> kitPotionEffectValues(
            String effectKey, int durationTicks, int amplifier, boolean ambient, boolean particles, boolean icon) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("effectKey", effectKey);
        values.put("durationTicks", durationTicks);
        values.put("amplifier", amplifier);
        values.put("ambient", ambient);
        values.put("particles", particles);
        values.put("icon", icon);
        return values;
    }

    private static Map<String, Object> kitRulesValues(
            boolean allowBuilding, boolean allowHunger, boolean allowNaturalRegeneration, boolean ranked) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("allowBuilding", allowBuilding);
        values.put("allowHunger", allowHunger);
        values.put("allowNaturalRegeneration", allowNaturalRegeneration);
        values.put("ranked", ranked);
        return values;
    }

    private static void assertIllegalArgument(ThrowingOperation operation, String message) {
        InvocationTargetException exception = assertThrows(InvocationTargetException.class, operation::run, message);
        assertTrue(exception.getCause() instanceof IllegalArgumentException, message);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void mutateList(List<?> values) {
        ((List) values).set(0, "changed");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void appendToList(List<?> values) {
        ((List) values).add("helmet-bytes");
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws ReflectiveOperationException;
    }
}
