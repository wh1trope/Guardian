/*
 * Guardian project - https://github.com/wh1trope/Guardian
 * Copyright (C) 2026 wh1trope and contributors
 *
 * This software is distributed under the GNU General Public License v3
 * or any later version as published by the Free Software Foundation.
 *
 * It is provided in the hope that it may be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * See the GNU General Public License for more details.
 * You should have received a copy of the license along with this project.
 * If not, see <http://www.gnu.org/licenses/>.
 */


package me.whitrope.guardian.gui;

import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.module.GuardianModule;
import me.whitrope.guardian.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI for managing plugin settings and module status in real-time.
 */
public class SettingsGUI implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final int PREV_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final Guardian plugin;

    public SettingsGUI(Guardian plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int page) {
        List<String> keys = collectBooleanKeys();
        int totalPages = Math.max(1, (int) Math.ceil(keys.size() / (double) PAGE_SIZE));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        Holder holder = new Holder(page, totalPages);
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatUtil.fix("&b&lGuardian &8| &7Settings &8(&f" + (page + 1) + "&8/&f" + totalPages + "&8)"));
        holder.inventory = inv;

        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, keys.size());
        for (int i = from; i < to; i++) {
            String key = keys.get(i);
            boolean value = plugin.getConfigManager().getConfig().getBoolean(key);
            int slot = i - from;
            ItemStack item = buildToggleItem(key, value);
            inv.setItem(slot, item);
            holder.slotToPath.put(slot, key);
        }

        inv.setItem(PREV_SLOT, buildNavItem(Material.ARROW, "&b« Previous page",
                page > 0 ? "&7Go to page " + page : "&7Already on the first page"));
        inv.setItem(CLOSE_SLOT, buildNavItem(Material.BARRIER, "&bClose",
                "&7Save & close this menu"));
        inv.setItem(NEXT_SLOT, buildNavItem(Material.ARROW, "&bNext page »",
                page < totalPages - 1 ? "&7Go to page " + (page + 2) : "&7Already on the last page"));

        ItemStack filler = buildNavItem(Material.GRAY_STAINED_GLASS_PANE, "&0", null);
        for (int s = PAGE_SIZE; s < 54; s++) {
            if (s == PREV_SLOT || s == CLOSE_SLOT || s == NEXT_SLOT) continue;
            inv.setItem(s, filler);
        }

        player.openInventory(inv);
    }

    private List<String> collectBooleanKeys() {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        List<String> keys = new ArrayList<>();
        for (String key : cfg.getKeys(true)) {
            if (cfg.isBoolean(key)) {
                keys.add(key);
            }
        }
        Collections.sort(keys);
        return keys;
    }

    private ItemStack buildToggleItem(String path, boolean value) {
        Material mat = value ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String last = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            meta.setDisplayName(ChatUtil.fix((value ? "&b" : "&7") + "&l" + last));
            List<String> lore = new ArrayList<>();
            lore.add(ChatUtil.fix("&7Path: &b" + path));
            lore.add("");
            lore.add(ChatUtil.fix("&7Status: " + (value ? "&a&lENABLED" : "&c&lDISABLED")));
            lore.add("");
            lore.add(ChatUtil.fix("&b▶ &7Click to toggle"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildNavItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatUtil.fix(name));
            if (lore != null) {
                meta.setLore(Collections.singletonList(ChatUtil.fix(lore)));
            } else {
                meta.setLore(Collections.emptyList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(top)) return;

        int slot = event.getRawSlot();
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) {
            return;
        }

        if (slot == PREV_SLOT) {
            open(player, holder.page - 1);
            return;
        }
        if (slot == NEXT_SLOT) {
            open(player, holder.page + 1);
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        String path = holder.slotToPath.get(slot);
        if (path == null) return;

        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        if (!cfg.isBoolean(path)) return;

        boolean current = cfg.getBoolean(path);
        boolean next = !current;
        cfg.set(path, next);
        plugin.getConfigManager().save();
        plugin.getConfigManager().reload();

        applyChange(path, next);

        ItemStack refreshed = buildToggleItem(path, next);
        top.setItem(slot, refreshed);

        ChatUtil.sendMessage(player, "&7[&b&lGuardian&7] &7Set &b" + path + " &7to "
                + (next ? "&aTRUE" : "&cFALSE"));
    }

    private void applyChange(String path, boolean value) {

        if (path.startsWith("modules.") && path.endsWith(".enabled")) {
            String moduleName = path.substring("modules.".length(),
                    path.length() - ".enabled".length());
            for (GuardianModule module : plugin.getModuleManager().getModules()) {
                if (module.getModuleName().equalsIgnoreCase(moduleName)) {
                    if (module.isEnabled() != value) {
                        module.setEnabled(value);
                    }
                    break;
                }
            }
            return;
        }
        for (GuardianModule module : plugin.getModuleManager().getModules()) {
            try {
                module.reloadValues();
            } catch (Throwable ignored) {
            }
        }
    }

    public static final class Holder implements InventoryHolder {
        final Map<Integer, String> slotToPath = new HashMap<>();
        final int page;
        final int totalPages;
        Inventory inventory;

        Holder(int page, int totalPages) {
            this.page = page;
            this.totalPages = totalPages;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
