package com.swag.tournaments.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("deprecation")
public abstract class GUIBase {

    protected Inventory inventory;

    public abstract void buildGUI();

    public abstract void handleClick(InventoryClickEvent event);

    public void open(Player player) {
        buildGUI();
        player.openInventory(inventory);
    }

    protected void setItem(int slot, ItemStack item) {
        if (inventory != null && slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    protected ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore.length > 0) {
                List<String> loreList = Arrays.stream(lore)
                        .map(l -> ChatColor.translateAlternateColorCodes('&', l))
                        .toList();
                meta.setLore(loreList);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    protected ItemStack createGlass(int colorIndex) {
        Material[] glasses = {
                Material.BLACK_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE,
                Material.LIGHT_GRAY_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE,
                Material.BLUE_STAINED_GLASS_PANE
        };
        Material mat = glasses[Math.abs(colorIndex) % glasses.length];
        ItemStack pane = new ItemStack(mat);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    protected void fillBorder(Material material) {
        ItemStack pane = createItem(material, " ");
        for (int i = 0; i < 9; i++) setItem(i, pane);
        for (int i = 45; i < 54; i++) setItem(i, pane);
        for (int i = 9; i < 45; i += 9) setItem(i, pane);
        for (int i = 17; i < 45; i += 9) setItem(i, pane);
    }
}
