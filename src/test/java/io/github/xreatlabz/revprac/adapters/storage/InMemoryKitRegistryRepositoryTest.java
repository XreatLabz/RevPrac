package io.github.xreatlabz.revprac.adapters.storage;

import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateNoArgs;
import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static io.github.xreatlabz.revprac.ContractTestSupport.recordComponentValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class InMemoryKitRegistryRepositoryTest {

    private static final String KIT_ID_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitId";
    private static final String KIT_INVENTORY_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitInventory";
    private static final String KIT_POTION_EFFECT_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitPotionEffect";
    private static final String KIT_RULES_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitRules";
    private static final String KIT_DEFINITION_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitDefinition";
    private static final String KIT_REGISTRY_REPOSITORY_TYPE =
            "io.github.xreatlabz.revprac.ports.kits.KitRegistryRepository";
    private static final String IN_MEMORY_KIT_REGISTRY_REPOSITORY_TYPE =
            "io.github.xreatlabz.revprac.adapters.storage.InMemoryKitRegistryRepository";

    @Test
    void repositoryStoresFindsAndReplacesKitsById() {
        RepositoryHarness harness = newHarness();
        Object original = harness.kitDefinition("nodebuff", "NoDebuff", true);
        Object replacement = harness.kitDefinition("nodebuff", "NoDebuff II", false);

        harness.save(original);
        Optional<?> stored = harness.find("nodebuff");
        assertTrue(stored.isPresent(), "Repository should find the stored kit");
        assertEquals("NoDebuff", recordComponentValue(stored.get(), "displayName"));

        harness.save(replacement);
        Optional<?> updated = harness.find("nodebuff");
        assertTrue(updated.isPresent(), "Repository should replace existing kit for the same id");
        assertEquals("NoDebuff II", recordComponentValue(updated.get(), "displayName"));
        assertEquals(false, recordComponentValue(updated.get(), "enabled"));
    }

    @Test
    void repositoryFindAllReturnsImmutableSnapshots() {
        RepositoryHarness harness = newHarness();
        harness.save(harness.kitDefinition("nodebuff", "NoDebuff", true));

        Collection<?> firstSnapshot = harness.findAll();
        assertEquals(1, firstSnapshot.size(), "Initial snapshot should contain the first kit");
        assertThrows(UnsupportedOperationException.class, firstSnapshot::clear);

        harness.save(harness.kitDefinition("boxing", "Boxing", false));

        assertEquals(1, firstSnapshot.size(), "Earlier snapshot should not change after later saves");
        assertEquals(List.of("boxing", "nodebuff"), harness.findAll().stream()
                .map(kit -> recordComponentValue(recordComponentValue(kit, "id"), "value"))
                .sorted()
                .toList());
    }

    private static RepositoryHarness newHarness() {
        Class<?> kitIdType = loadClass(KIT_ID_TYPE);
        Class<?> kitInventoryType = loadClass(KIT_INVENTORY_TYPE);
        Class<?> kitPotionEffectType = loadClass(KIT_POTION_EFFECT_TYPE);
        Class<?> kitRulesType = loadClass(KIT_RULES_TYPE);
        Class<?> kitDefinitionType = loadClass(KIT_DEFINITION_TYPE);
        Class<?> repositoryType = loadClass(KIT_REGISTRY_REPOSITORY_TYPE);
        Object repository = instantiateNoArgs(loadClass(IN_MEMORY_KIT_REGISTRY_REPOSITORY_TYPE));

        try {
            return new RepositoryHarness(
                    repository,
                    kitIdType,
                    kitInventoryType,
                    kitPotionEffectType,
                    kitRulesType,
                    kitDefinitionType,
                    repositoryType.getMethod("save", kitDefinitionType),
                    repositoryType.getMethod("find", kitIdType),
                    repositoryType.getMethod("findAll"));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not build kit repository harness", exception);
        }
    }

    private record RepositoryHarness(
            Object repository,
            Class<?> kitIdType,
            Class<?> kitInventoryType,
            Class<?> kitPotionEffectType,
            Class<?> kitRulesType,
            Class<?> kitDefinitionType,
            Method saveMethod,
            Method findMethod,
            Method findAllMethod) {

        Object kitDefinition(String id, String displayName, boolean enabled) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", instantiateRecord(kitIdType, Map.of("value", id)));
            values.put("displayName", displayName);
            values.put("inventory", instantiateRecord(kitInventoryType, Map.of(
                    "storage", Arrays.asList("item-" + id, null, "rod-" + id),
                    "armor", List.of("helmet", "chestplate", "leggings", "boots"),
                    "extra", List.of("totem-" + id),
                    "selectedSlot", 0)));
            values.put("potionEffects", List.of(instantiateRecord(kitPotionEffectType, Map.of(
                    "effectKey", "minecraft:speed",
                    "durationTicks", 1200,
                    "amplifier", 1,
                    "ambient", false,
                    "particles", true,
                    "icon", true))));
            values.put("rules", instantiateRecord(kitRulesType, Map.of(
                    "allowBuilding", false,
                    "allowHunger", false,
                    "allowNaturalRegeneration", true,
                    "ranked", false)));
            values.put("enabled", enabled);
            return instantiateRecord(kitDefinitionType, values);
        }

        void save(Object kitDefinition) {
            try {
                saveMethod.invoke(repository, kitDefinition);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Could not save kit definition", exception);
            }
        }

        Optional<?> find(String id) {
            try {
                Object kitId = instantiateRecord(kitIdType, Map.of("value", id));
                return (Optional<?>) findMethod.invoke(repository, kitId);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Could not find kit definition", exception);
            }
        }

        Collection<?> findAll() {
            try {
                return (Collection<?>) findAllMethod.invoke(repository);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Could not list kit definitions", exception);
            }
        }
    }
}
