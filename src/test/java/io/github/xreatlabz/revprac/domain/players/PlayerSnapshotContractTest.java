package io.github.xreatlabz.revprac.domain.players;

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

final class PlayerSnapshotContractTest {

    private static final String DOMAIN_PLAYERS_DIR = "src/main/java/io/github/xreatlabz/revprac/domain/players";
    private static final String PLAYER_ID_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerId";
    private static final String LOCATION_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.LocationSnapshot";
    private static final String INVENTORY_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.InventorySnapshot";
    private static final String PLAYER_STATUS_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot";
    private static final String POTION_EFFECT_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.PotionEffectSnapshot";
    private static final String PLAYER_SAFETY_SNAPSHOT_TYPE = "io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot";
    private static final String PENDING_RESTORATION_TYPE = "io.github.xreatlabz.revprac.domain.players.PendingRestoration";

    @Test
    void snapshotContractsUseImmutableRecordsAndPreserveNullInventorySlots() {
        Class<?> playerIdType = loadClass(PLAYER_ID_TYPE);
        Class<?> locationSnapshotType = loadClass(LOCATION_SNAPSHOT_TYPE);
        Class<?> inventorySnapshotType = loadClass(INVENTORY_SNAPSHOT_TYPE);
        Class<?> playerStatusSnapshotType = loadClass(PLAYER_STATUS_SNAPSHOT_TYPE);
        Class<?> potionEffectSnapshotType = loadClass(POTION_EFFECT_SNAPSHOT_TYPE);
        Class<?> playerSafetySnapshotType = loadClass(PLAYER_SAFETY_SNAPSHOT_TYPE);
        Class<?> pendingRestorationType = loadClass(PENDING_RESTORATION_TYPE);

        assertTrue(playerIdType.isRecord(), "PlayerId should be a record");
        assertTrue(locationSnapshotType.isRecord(), "LocationSnapshot should be a record");
        assertTrue(inventorySnapshotType.isRecord(), "InventorySnapshot should be a record");
        assertTrue(playerStatusSnapshotType.isRecord(), "PlayerStatusSnapshot should be a record");
        assertTrue(potionEffectSnapshotType.isRecord(), "PotionEffectSnapshot should be a record");
        assertTrue(playerSafetySnapshotType.isRecord(), "PlayerSafetySnapshot should be a record");
        assertTrue(pendingRestorationType.isRecord(), "PendingRestoration should be a record");

        List<String> storage = new ArrayList<>(Arrays.asList("sword", null, "bow"));
        List<String> armor = new ArrayList<>(Arrays.asList(null, "chestplate", null, "boots"));
        List<String> extra = new ArrayList<>(Arrays.asList("offhand", null));
        List<String> enderChest = new ArrayList<>(Arrays.asList(null, "totem"));

        Object inventorySnapshot = instantiateRecord(inventorySnapshotType, inventorySnapshotValues(
                storage,
                armor,
                extra,
                enderChest,
                "cursor",
                4));

        storage.set(0, "mutated");
        armor.set(1, "mutated");
        extra.set(0, "mutated");
        enderChest.set(1, "mutated");

        List<?> storedStorage = assertInstanceOf(List.class, recordComponentValue(inventorySnapshot, "storage"));
        List<?> storedArmor = assertInstanceOf(List.class, recordComponentValue(inventorySnapshot, "armor"));
        List<?> storedExtra = assertInstanceOf(List.class, recordComponentValue(inventorySnapshot, "extra"));
        List<?> storedEnderChest = assertInstanceOf(List.class, recordComponentValue(inventorySnapshot, "enderChest"));

        assertEquals(Arrays.asList("sword", null, "bow"), storedStorage);
        assertEquals(Arrays.asList(null, "chestplate", null, "boots"), storedArmor);
        assertEquals(Arrays.asList("offhand", null), storedExtra);
        assertEquals(Arrays.asList(null, "totem"), storedEnderChest);
        assertThrows(UnsupportedOperationException.class, () -> mutateList(storedStorage));
        assertThrows(UnsupportedOperationException.class, () -> appendToList(storedArmor));
        assertThrows(UnsupportedOperationException.class, () -> storedExtra.remove(0));
        assertThrows(UnsupportedOperationException.class, () -> storedEnderChest.clear());
    }

    @Test
    void snapshotScalarsRejectImpossibleState() throws ReflectiveOperationException {
        Class<?> inventorySnapshotType = loadClass(INVENTORY_SNAPSHOT_TYPE);
        Class<?> playerStatusSnapshotType = loadClass(PLAYER_STATUS_SNAPSHOT_TYPE);
        Class<?> potionEffectSnapshotType = loadClass(POTION_EFFECT_SNAPSHOT_TYPE);

        Constructor<?> inventoryConstructor =
                inventorySnapshotType.getDeclaredConstructor(List.class, List.class, List.class, List.class, String.class, int.class);
        inventoryConstructor.setAccessible(true);
        assertIllegalArgument(
                () -> inventoryConstructor.newInstance(List.of(), List.of(), List.of(), List.of(), null, -1),
                "InventorySnapshot should reject a negative selectedSlot");
        assertIllegalArgument(
                () -> inventoryConstructor.newInstance(List.of(), List.of(), List.of(), List.of(), null, 9),
                "InventorySnapshot should reject selectedSlot values above 8");

        Constructor<?> statusConstructor = playerStatusSnapshotType.getDeclaredConstructor(
                String.class,
                double.class,
                int.class,
                float.class,
                float.class,
                int.class,
                boolean.class,
                boolean.class,
                List.class);
        statusConstructor.setAccessible(true);
        List<?> potionEffects = List.of();
        assertIllegalArgument(
                () -> statusConstructor.newInstance("SURVIVAL", -0.01d, 20, 5.0f, 0.25f, 12, false, false, potionEffects),
                "PlayerStatusSnapshot should reject negative health");
        assertIllegalArgument(
                () -> statusConstructor.newInstance("SURVIVAL", 20.0d, -1, 5.0f, 0.25f, 12, false, false, potionEffects),
                "PlayerStatusSnapshot should reject food levels below 0");
        assertIllegalArgument(
                () -> statusConstructor.newInstance("SURVIVAL", 20.0d, 21, 5.0f, 0.25f, 12, false, false, potionEffects),
                "PlayerStatusSnapshot should reject food levels above 20");
        assertIllegalArgument(
                () -> statusConstructor.newInstance("SURVIVAL", 20.0d, 20, -0.01f, 0.25f, 12, false, false, potionEffects),
                "PlayerStatusSnapshot should reject negative saturation");
        assertIllegalArgument(
                () -> statusConstructor.newInstance("SURVIVAL", 20.0d, 20, 5.0f, -0.01f, 12, false, false, potionEffects),
                "PlayerStatusSnapshot should reject expProgress below 0.0");
        assertIllegalArgument(
                () -> statusConstructor.newInstance("SURVIVAL", 20.0d, 20, 5.0f, 1.01f, 12, false, false, potionEffects),
                "PlayerStatusSnapshot should reject expProgress above 1.0");
        assertIllegalArgument(
                () -> statusConstructor.newInstance("SURVIVAL", 20.0d, 20, 5.0f, 0.25f, -1, false, false, potionEffects),
                "PlayerStatusSnapshot should reject negative levels");

        Constructor<?> potionEffectConstructor = potionEffectSnapshotType.getDeclaredConstructor(
                String.class, int.class, int.class, boolean.class, boolean.class, boolean.class);
        potionEffectConstructor.setAccessible(true);
        assertIllegalArgument(
                () -> potionEffectConstructor.newInstance("minecraft:speed", -1, 0, false, true, true),
                "PotionEffectSnapshot should reject negative durationTicks");
        assertIllegalArgument(
                () -> potionEffectConstructor.newInstance("minecraft:speed", 40, -1, false, true, true),
                "PotionEffectSnapshot should reject negative amplifier");
    }

    @Test
    void playerStatusSnapshotDefensivelyCopiesPotionEffectsAndExposesUnmodifiableList() throws ReflectiveOperationException {
        Class<?> playerStatusSnapshotType = loadClass(PLAYER_STATUS_SNAPSHOT_TYPE);
        Class<?> potionEffectSnapshotType = loadClass(POTION_EFFECT_SNAPSHOT_TYPE);

        Object speed = instantiateRecord(potionEffectSnapshotType, potionEffectValues("minecraft:speed", 40, 1));
        Object strength = instantiateRecord(potionEffectSnapshotType, potionEffectValues("minecraft:strength", 80, 2));

        List<Object> potionEffects = new ArrayList<>();
        potionEffects.add(speed);

        Object statusSnapshot = instantiateRecord(playerStatusSnapshotType, statusSnapshotValues(potionEffects));

        potionEffects.clear();
        potionEffects.add(strength);

        List<?> storedPotionEffects = assertInstanceOf(List.class, recordComponentValue(statusSnapshot, "potionEffects"));
        assertEquals(List.of(speed), storedPotionEffects, "PlayerStatusSnapshot should defensively copy potion effects");
        assertThrows(UnsupportedOperationException.class, () -> appendToList(storedPotionEffects));
        assertThrows(UnsupportedOperationException.class, () -> storedPotionEffects.clear());
    }

    @Test
    void domainPlayerSourcesStayFreeOfBukkitAndPaperImports() throws IOException {
        Path domainDirectory = Path.of(DOMAIN_PLAYERS_DIR);

        assertTrue(Files.isDirectory(domainDirectory), "Expected player domain directory to exist: " + domainDirectory);

        try (Stream<Path> sources = Files.walk(domainDirectory)) {
            List<Path> javaSources = sources
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .toList();

            assertFalse(javaSources.isEmpty(), "Expected player domain source files to exist");

            for (Path source : javaSources) {
                String contents = Files.readString(source);
                assertFalse(contents.contains("org.bukkit"), "Domain source must not import Bukkit: " + source);
                assertFalse(contents.contains("io.papermc.paper"), "Domain source must not import Paper: " + source);
            }
        }
    }

    private static Map<String, Object> inventorySnapshotValues(
            List<String> storage,
            List<String> armor,
            List<String> extra,
            List<String> enderChest,
            String cursorItem,
            int selectedSlot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("storage", storage);
        values.put("armor", armor);
        values.put("extra", extra);
        values.put("enderChest", enderChest);
        values.put("cursorItem", cursorItem);
        values.put("selectedSlot", selectedSlot);
        return values;
    }

    private static Map<String, Object> statusSnapshotValues(List<Object> potionEffects) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("gameMode", "SURVIVAL");
        values.put("health", 20.0d);
        values.put("foodLevel", 20);
        values.put("saturation", 5.0f);
        values.put("expProgress", 0.25f);
        values.put("level", 12);
        values.put("allowFlight", false);
        values.put("flying", false);
        values.put("potionEffects", potionEffects);
        return values;
    }

    private static Map<String, Object> potionEffectValues(String effectKey, int durationTicks, int amplifier) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("effectKey", effectKey);
        values.put("durationTicks", durationTicks);
        values.put("amplifier", amplifier);
        values.put("ambient", false);
        values.put("particles", true);
        values.put("icon", true);
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
        ((List) values).add("helmet");
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws ReflectiveOperationException;
    }
}
