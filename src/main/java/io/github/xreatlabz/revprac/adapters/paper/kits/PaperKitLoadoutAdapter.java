package io.github.xreatlabz.revprac.adapters.paper.kits;

import io.github.xreatlabz.revprac.domain.kits.KitDefinition;
import io.github.xreatlabz.revprac.domain.kits.KitId;
import io.github.xreatlabz.revprac.domain.kits.KitInventory;
import io.github.xreatlabz.revprac.domain.kits.KitPotionEffect;
import io.github.xreatlabz.revprac.domain.kits.KitRules;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class PaperKitLoadoutAdapter {

    public KitDefinition capture(Player player, KitId id, String displayName, KitRules rules, boolean enabled) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rules, "rules");

        PlayerInventory inventory = player.getInventory();
        return new KitDefinition(
                id,
                displayName,
                new KitInventory(
                        serializeItems(inventory.getStorageContents()),
                        serializeItems(inventory.getArmorContents()),
                        serializeItems(inventory.getExtraContents()),
                        inventory.getHeldItemSlot()),
                player.getActivePotionEffects().stream()
                        .map(PaperKitLoadoutAdapter::captureEffect)
                        .sorted(Comparator.comparing(KitPotionEffect::effectKey))
                        .toList(),
                rules,
                enabled);
    }

    public void apply(Player player, KitDefinition definition) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(definition, "definition");

        ItemStack[] storageContents = deserializeItems(definition.inventory().storage(), "storage");
        ItemStack[] armorContents = deserializeItems(definition.inventory().armor(), "armor");
        ItemStack[] extraContents = deserializeItems(definition.inventory().extra(), "extra");
        List<PotionEffect> potionEffects = definition.potionEffects().stream()
                .map(PaperKitLoadoutAdapter::restoreEffect)
                .toList();

        player.closeInventory();
        player.getInventory().setStorageContents(storageContents);
        player.getInventory().setArmorContents(armorContents);
        player.getInventory().setExtraContents(extraContents);
        player.getInventory().setHeldItemSlot(definition.inventory().selectedSlot());

        player.getActivePotionEffects().stream()
                .map(PotionEffect::getType)
                .forEach(player::removePotionEffect);
        for (PotionEffect effect : potionEffects) {
            player.addPotionEffect(effect);
        }
    }

    private static KitPotionEffect captureEffect(PotionEffect effect) {
        return new KitPotionEffect(
                effect.getType().getKey().asString(),
                effect.getDuration(),
                effect.getAmplifier(),
                effect.isAmbient(),
                effect.hasParticles(),
                effect.hasIcon());
    }

    private static PotionEffect restoreEffect(KitPotionEffect effect) {
        NamespacedKey effectKey = NamespacedKey.fromString(effect.effectKey());
        if (effectKey == null) {
            throw new IllegalArgumentException("Invalid potion effect key: " + effect.effectKey());
        }

        PotionEffectType effectType = Registry.EFFECT.get(effectKey);
        if (effectType == null) {
            throw new IllegalArgumentException("Unknown potion effect type: " + effect.effectKey());
        }

        return new PotionEffect(
                effectType,
                effect.durationTicks(),
                effect.amplifier(),
                effect.ambient(),
                effect.particles(),
                effect.icon());
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

    private static ItemStack[] deserializeItems(List<String> encodedItems, String section) {
        ItemStack[] items = new ItemStack[encodedItems.size()];
        for (int index = 0; index < encodedItems.size(); index++) {
            items[index] = deserializeItem(encodedItems.get(index), section + "[" + index + "]");
        }
        return items;
    }

    private static ItemStack deserializeItem(String encodedItem, String path) {
        if (encodedItem == null) {
            return null;
        }

        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encodedItem));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid item payload at " + path, exception);
        }
    }
}
