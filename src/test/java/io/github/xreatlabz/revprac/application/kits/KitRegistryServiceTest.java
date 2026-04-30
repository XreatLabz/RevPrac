package io.github.xreatlabz.revprac.application.kits;

import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static io.github.xreatlabz.revprac.ContractTestSupport.recordComponentValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class KitRegistryServiceTest {

    private static final String APPLICATION_KITS_DIR = "src/main/java/io/github/xreatlabz/revprac/application/kits";
    private static final String PORTS_KITS_DIR = "src/main/java/io/github/xreatlabz/revprac/ports/kits";
    private static final String KIT_ID_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitId";
    private static final String KIT_INVENTORY_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitInventory";
    private static final String KIT_POTION_EFFECT_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitPotionEffect";
    private static final String KIT_RULES_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitRules";
    private static final String KIT_DEFINITION_TYPE = "io.github.xreatlabz.revprac.domain.kits.KitDefinition";
    private static final String KIT_REGISTRY_REPOSITORY_TYPE =
            "io.github.xreatlabz.revprac.ports.kits.KitRegistryRepository";
    private static final String KIT_REGISTRY_SERVICE_TYPE =
            "io.github.xreatlabz.revprac.application.kits.KitRegistryService";

    @Test
    void registerAndListReturnDeterministicKitIdOrder() {
        ServiceHarness harness = newHarness();
        harness.register(harness.kitDefinition("sumo", "Sumo", true));
        harness.register(harness.kitDefinition("archer", "Archer", true));

        assertEquals(List.of("archer", "sumo"), harness.kits().stream()
                .map(kit -> recordComponentValue(recordComponentValue(kit, "id"), "value"))
                .toList());
    }

    @Test
    void duplicateKitIdsAreRejected() {
        ServiceHarness harness = newHarness();
        harness.register(harness.kitDefinition("nodebuff", "NoDebuff", true));

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> harness.registerMethod.invoke(harness.service, harness.kitDefinition("nodebuff", "Alt", true)));

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals("Kit already exists: nodebuff", exception.getCause().getMessage());
    }

    @Test
    void disabledKitsRemainRegisteredButAreExcludedFromEnabledKits() {
        ServiceHarness harness = newHarness();
        harness.register(harness.kitDefinition("nodebuff", "NoDebuff", true));
        harness.register(harness.kitDefinition("boxing", "Boxing", false));

        assertEquals(List.of("boxing", "nodebuff"), harness.kits().stream()
                .map(kit -> recordComponentValue(recordComponentValue(kit, "id"), "value"))
                .toList());
        assertEquals(List.of("nodebuff"), harness.enabledKits().stream()
                .map(kit -> recordComponentValue(recordComponentValue(kit, "id"), "value"))
                .toList());
    }

    @Test
    void applicationAndPortKitSourcesStayFreeOfBukkitAndPaperImports() throws IOException {
        assertNoBukkitOrPaperImports(Path.of(APPLICATION_KITS_DIR));
        assertNoBukkitOrPaperImports(Path.of(PORTS_KITS_DIR));
    }

    private static void assertNoBukkitOrPaperImports(Path directory) throws IOException {
        assertTrue(Files.isDirectory(directory), "Expected directory to exist: " + directory);

        try (Stream<Path> sources = Files.walk(directory)) {
            List<Path> javaSources = sources
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .toList();

            assertFalse(javaSources.isEmpty(), "Expected Java sources in " + directory);
            for (Path source : javaSources) {
                String contents = Files.readString(source);
                assertFalse(contents.contains("org.bukkit"), "Source must not import Bukkit: " + source);
                assertFalse(contents.contains("io.papermc.paper"), "Source must not import Paper: " + source);
            }
        }
    }

    private static ServiceHarness newHarness() {
        Class<?> kitIdType = loadClass(KIT_ID_TYPE);
        Class<?> kitInventoryType = loadClass(KIT_INVENTORY_TYPE);
        Class<?> kitPotionEffectType = loadClass(KIT_POTION_EFFECT_TYPE);
        Class<?> kitRulesType = loadClass(KIT_RULES_TYPE);
        Class<?> kitDefinitionType = loadClass(KIT_DEFINITION_TYPE);
        Class<?> repositoryType = loadClass(KIT_REGISTRY_REPOSITORY_TYPE);
        Class<?> serviceType = loadClass(KIT_REGISTRY_SERVICE_TYPE);

        RepositoryDouble repository = new RepositoryDouble();
        Object repositoryProxy = Proxy.newProxyInstance(
                repositoryType.getClassLoader(), new Class<?>[] {repositoryType}, repository);

        try {
            Object service = serviceType.getDeclaredConstructor(repositoryType).newInstance(repositoryProxy);
            return new ServiceHarness(
                    service,
                    kitIdType,
                    kitInventoryType,
                    kitPotionEffectType,
                    kitRulesType,
                    kitDefinitionType,
                    serviceType.getMethod("register", kitDefinitionType),
                    serviceType.getMethod("kits"),
                    serviceType.getMethod("enabledKits"),
                    repository);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not build kit service harness", exception);
        }
    }

    private record ServiceHarness(
            Object service,
            Class<?> kitIdType,
            Class<?> kitInventoryType,
            Class<?> kitPotionEffectType,
            Class<?> kitRulesType,
            Class<?> kitDefinitionType,
            Method registerMethod,
            Method kitsMethod,
            Method enabledKitsMethod,
            RepositoryDouble repository) {

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

        void register(Object kitDefinition) {
            try {
                registerMethod.invoke(service, kitDefinition);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new AssertionError("Could not register kit", exception);
            }
        }

        List<?> kits() {
            try {
                return List.copyOf((Collection<?>) kitsMethod.invoke(service));
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new AssertionError("Could not list kits", exception);
            }
        }

        List<?> enabledKits() {
            try {
                return List.copyOf((Collection<?>) enabledKitsMethod.invoke(service));
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new AssertionError("Could not list enabled kits", exception);
            }
        }
    }

    private static final class RepositoryDouble implements InvocationHandler {

        private final Map<Object, Object> definitions = new ConcurrentHashMap<>();

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "find" -> Optional.ofNullable(definitions.get(arguments[0]));
                case "save" -> {
                    Object kitDefinition = arguments[0];
                    definitions.put(recordComponentValue(kitDefinition, "id"), kitDefinition);
                    yield null;
                }
                case "findAll" -> List.copyOf(definitions.values());
                default -> throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
            };
        }
    }
}
