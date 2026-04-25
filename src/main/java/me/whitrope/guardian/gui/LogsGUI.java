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
import me.whitrope.guardian.util.ChatUtil;
import me.whitrope.guardian.violation.ViolationLog;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * GUI for viewing violation logs and player history.
 */
public class LogsGUI implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final int PREV_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final Guardian plugin;
    private final SimpleDateFormat dateFormat;

    public LogsGUI(Guardian plugin) {
        this.plugin = plugin;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    public void open(Player player, int page) {
        List<ViolationLog> logs = plugin.getViolationManager().getHistory();
        int totalPages = Math.max(1, (int) Math.ceil(logs.size() / (double) PAGE_SIZE));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        Holder holder = new Holder(page, totalPages);
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatUtil.fix("&b&lGuardian &8| &7Logs &8(&f" + (page + 1) + "&8/&f" + totalPages + "&8)"));
        holder.inventory = inv;

        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, logs.size());
        for (int i = from; i < to; i++) {
            int slot = i - from;
            ViolationLog log = logs.get(i);
            inv.setItem(slot, buildLogItem(log));
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

    private ItemStack buildLogItem(ViolationLog log) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            if (log.getPlayerUUID() != null) {
                skull.setOwningPlayer(Bukkit.getOfflinePlayer(log.getPlayerUUID()));
            }
            skull.setDisplayName(ChatUtil.fix("&b&l" + log.getPlayerName()));
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatUtil.fix("&7Module: &f" + log.getModuleName()));
            lore.add(ChatUtil.fix("&7Detail: &f" + log.getDetail()));
            lore.add(ChatUtil.fix("&7Time: &f" + dateFormat.format(new Date(log.getTimestamp()))));
            lore.add(ChatUtil.fix("&7Action: " + (log.isCrash() ? "&cCRASH" : "&cKICK")));
            lore.add("");
            skull.setLore(lore);
            item.setItemMeta(skull);
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
        if (slot == PREV_SLOT) {
            open(player, holder.page - 1);
        } else if (slot == NEXT_SLOT) {
            open(player, holder.page + 1);
        } else if (slot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    public static final class Holder implements InventoryHolder {
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
