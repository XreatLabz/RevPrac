package io.github.xreatlabz.revprac.adapters.paper.players;

import io.github.xreatlabz.revprac.domain.players.InventorySnapshot;
import io.github.xreatlabz.revprac.domain.players.LocationSnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerId;
import io.github.xreatlabz.revprac.domain.players.PlayerSafetySnapshot;
import io.github.xreatlabz.revprac.domain.players.PlayerStatusSnapshot;
import io.github.xreatlabz.revprac.domain.players.PotionEffectSnapshot;
import io.github.xreatlabz.revprac.ports.players.PlayerStatePort;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class PaperPlayerStateAdapter implements PlayerStatePort {

    private final Server server;

    public PaperPlayerStateAdapter(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public PlayerSafetySnapshot capture(PlayerId playerId) {
        Player player = requireOnlinePlayer(playerId);
        PlayerInventory inventory = player.getInventory();
        Location location = player.getLocation();
        World world = Objects.requireNonNull(location.getWorld(), "player location world");

        return new PlayerSafetySnapshot(
                new LocationSnapshot(
                        world.getKey().asString(),
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getYaw(),
                        location.getPitch()),
                new InventorySnapshot(
                        serializeItems(inventory.getStorageContents()),
                        serializeItems(inventory.getArmorContents()),
                        serializeItems(inventory.getExtraContents()),
                        serializeItems(player.getEnderChest().getContents()),
                        serializeItem(player.getItemOnCursor()),
                        inventory.getHeldItemSlot()),
                new PlayerStatusSnapshot(
                        player.getGameMode().name(),
                        player.getHealth(),
                        player.getFoodLevel(),
                        player.getSaturation(),
                        player.getExp(),
                        player.getLevel(),
                        player.getAllowFlight(),
                        player.isFlying(),
                        player.getActivePotionEffects().stream()
                                .map(PaperPlayerStateAdapter::capturePotionEffect)
                                .sorted(Comparator.comparing(PotionEffectSnapshot::effectKey))
                                .toList()));
    }

    @Override
    public void restore(PlayerId playerId, PlayerSafetySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Player player = requireOnlinePlayer(playerId);

        LocationSnapshot locationSnapshot = snapshot.location();
        World world = resolveWorld(locationSnapshot.worldKey());
        Location restoreLocation = new Location(
                world,
                locationSnapshot.x(),
                locationSnapshot.y(),
                locationSnapshot.z(),
                locationSnapshot.yaw(),
                locationSnapshot.pitch());
        if (!player.teleport(restoreLocation)) {
            throw new IllegalStateException("Failed to restore player location for " + playerId.value());
        }

        ItemStack[] storageContents = deserializeItems(snapshot.inventory().storage());
        ItemStack[] armorContents = deserializeItems(snapshot.inventory().armor());
        ItemStack[] extraContents = deserializeItems(snapshot.inventory().extra());
        ItemStack[] enderChestContents = deserializeItems(snapshot.inventory().enderChest());
        ItemStack cursorItem = deserializeItem(snapshot.inventory().cursorItem());

        player.setItemOnCursor(null);
        player.closeInventory();
        player.getInventory().setStorageContents(storageContents);
        player.getInventory().setArmorContents(armorContents);
        player.getInventory().setExtraContents(extraContents);
        player.getEnderChest().setContents(enderChestContents);
        player.getInventory().setHeldItemSlot(snapshot.inventory().selectedSlot());
        player.setItemOnCursor(cursorItem);

        PlayerStatusSnapshot status = snapshot.status();
        player.setGameMode(GameMode.valueOf(status.gameMode()));
        player.setAllowFlight(status.allowFlight());
        player.setFlying(status.flying());

        double maxHealth = Objects.requireNonNull(
                        player.getAttribute(Attribute.MAX_HEALTH),
                        "player max health attribute")
                .getValue();
        player.setHealth(Math.min(status.health(), maxHealth));
        player.setFoodLevel(status.foodLevel());
        player.setSaturation(status.saturation());
        player.setExp(status.expProgress());
        player.setLevel(status.level());

        player.getActivePotionEffects().stream()
                .map(PotionEffect::getType)
                .forEach(player::removePotionEffect);
        for (PotionEffectSnapshot effectSnapshot : status.potionEffects()) {
            player.addPotionEffect(restorePotionEffect(effectSnapshot));
        }
    }

    @Override
    public boolean isOnline(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Player player = server.getPlayer(playerId.value());
        return player != null && player.isOnline();
    }

    private Player requireOnlinePlayer(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Player player = server.getPlayer(playerId.value());
        if (player == null || !player.isOnline()) {
            throw new IllegalStateException("Player is not currently online: " + playerId.value());
        }
        return player;
    }

    private World resolveWorld(String worldKey) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(worldKey);
        if (namespacedKey == null) {
            throw new IllegalArgumentException("Invalid world key: " + worldKey);
        }
        World world = server.getWorld(namespacedKey);
        if (world == null) {
            throw new IllegalStateException("World is not loaded: " + worldKey);
        }
        return world;
    }

    private static PotionEffectSnapshot capturePotionEffect(PotionEffect effect) {
        return new PotionEffectSnapshot(
                effect.getType().getKey().asString(),
                effect.getDuration(),
                effect.getAmplifier(),
                effect.isAmbient(),
                effect.hasParticles(),
                effect.hasIcon());
    }

    private static PotionEffect restorePotionEffect(PotionEffectSnapshot snapshot) {
        NamespacedKey effectKey = NamespacedKey.fromString(snapshot.effectKey());
        if (effectKey == null) {
            throw new IllegalArgumentException("Invalid potion effect key: " + snapshot.effectKey());
        }
        PotionEffectType effectType = Registry.EFFECT.get(effectKey);
        if (effectType == null) {
            throw new IllegalStateException("Unknown potion effect type: " + snapshot.effectKey());
        }
        return new PotionEffect(
                effectType,
                snapshot.durationTicks(),
                snapshot.amplifier(),
                snapshot.ambient(),
                snapshot.particles(),
                snapshot.icon());
    }

    private static List<String> serializeItems(ItemStack[] items) {
        List<String> serialized = new ArrayList<>(items.length);
        for (ItemStack item : items) {
            serialized.add(serializeItem(item));
        }
        return serialized;
    }

    private static String serializeItem(ItemStack item) {
        return item == null || item.isEmpty()
                ? null
                : Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private static ItemStack[] deserializeItems(List<String> encodedItems) {
        ItemStack[] items = new ItemStack[encodedItems.size()];
        for (int index = 0; index < encodedItems.size(); index++) {
            String encodedItem = encodedItems.get(index);
            items[index] = encodedItem == null
                    ? null
                    : ItemStack.deserializeBytes(Base64.getDecoder().decode(encodedItem));
        }
        return items;
    }

    private static ItemStack deserializeItem(String encodedItem) {
        return encodedItem == null
                ? null
                : ItemStack.deserializeBytes(Base64.getDecoder().decode(encodedItem));
    }
}
