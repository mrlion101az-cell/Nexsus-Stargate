package com.nexuscraft.nexusstargate;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The two things players carry: the dialing device and the address tablet. Both are plain items marked with item data. */
final class Items {

    static final NamespacedKey DHD_KEY = NamespacedKey.fromString("nexusstargate:dhd");
    static final NamespacedKey TABLET_KEY = NamespacedKey.fromString("nexusstargate:tablet");
    static final NamespacedKey RECIPE_KEY = NamespacedKey.fromString("nexusstargate:dhd_recipe");
    static final Material TABLET_MATERIAL = Material.DISC_FRAGMENT_5;

    private final StargateConfig cfg;

    Items(StargateConfig cfg) {
        this.cfg = cfg;
    }

    ItemStack dhd() {
        ItemStack item = new ItemStack(cfg.materials.dhd);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorText.translate(cfg.dhdName));
        meta.setLore(List.of(
                ColorText.translate("&7Place it within " + cfg.range + " blocks of a complete gate"),
                ColorText.translate("&7to link it. Right-click it to dial.")));
        meta.getPersistentDataContainer().set(DHD_KEY, PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

    boolean isDhd(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(DHD_KEY, PersistentDataType.INTEGER);
    }

    ItemStack tablet(Gate g) {
        ItemStack item = new ItemStack(TABLET_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorText.translate("&6Address tablet: &e" + g.name));
        List<String> lore = new ArrayList<>();
        lore.add(ColorText.translate("&7" + g.address.names()));
        lore.add(ColorText.translate("&7Right-click to learn this address."));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(TABLET_KEY, PersistentDataType.STRING, g.id);
        item.setItemMeta(meta);
        return item;
    }

    /** The gate id written on a tablet, or null if the item is not one. */
    String tabletGate(ItemStack item) {
        if (item == null || item.getType() != TABLET_MATERIAL || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(TABLET_KEY, PersistentDataType.STRING);
    }

    /** Registers (or re-registers) the crafting recipe for the dialing device. */
    void registerRecipe() {
        org.bukkit.Bukkit.removeRecipe(RECIPE_KEY);
        if (!cfg.recipeEnabled) {
            return;
        }
        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, dhd());
        recipe.shape(cfg.recipeShape.toArray(new String[0]));
        for (Map.Entry<Character, Material> e : cfg.recipeIngredients.entrySet()) {
            recipe.setIngredient(e.getKey(), e.getValue());
        }
        org.bukkit.Bukkit.addRecipe(recipe);
    }

    void unregisterRecipe() {
        org.bukkit.Bukkit.removeRecipe(RECIPE_KEY);
    }
}
