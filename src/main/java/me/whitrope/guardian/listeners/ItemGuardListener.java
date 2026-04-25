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


/**
 * Listener for preventing item-related exploits and malicious item interaction.
 */
package me.whitrope.guardian.listeners;

import me.whitrope.guardian.config.ConfigManager;
import me.whitrope.guardian.module.GuardianModule;
import org.bukkit.Material;
import org.bukkit.block.Beehive;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.Objects;

public class ItemGuardListener implements Listener {

    private final GuardianModule module;

    private boolean checkInvalidStackSize;
    private boolean checkOversizedDisplayName;
    private boolean checkIllegalEnchantLevel;
    private boolean checkOversizedBook;
    private boolean checkNestedShulkerBox;
    private boolean checkOversizedBeehive;
    private boolean checkBlockBundles;
    private boolean checkBlockBooks;
    private boolean checkBlockSkulls;
    private boolean checkBlockMaps;
    private boolean checkBlockArmorstands;
    private boolean checkBlockLecterns;

    private int maxDisplayNameLength;
    private int maxEnchantLevel;
    private int maxBookPages;
    private int maxBeehiveSize;

    public ItemGuardListener(GuardianModule module) {
        this.module = module;
        reloadValues();
    }

    public void reloadValues() {
        ConfigManager cfg = module.getConfigManager();

        checkInvalidStackSize = cfg.isCheckEnabled("ExploitBlocker", "invalid-stack-size");
        checkOversizedDisplayName = cfg.isCheckEnabled("ExploitBlocker", "oversized-display-name");
        checkIllegalEnchantLevel = cfg.isCheckEnabled("ExploitBlocker", "illegal-enchant-level");
        checkOversizedBook = cfg.isCheckEnabled("ExploitBlocker", "oversized-book");
        checkNestedShulkerBox = cfg.isCheckEnabled("ExploitBlocker", "nested-shulker-box");
        checkOversizedBeehive = cfg.isCheckEnabled("ExploitBlocker", "oversized-beehive");
        checkBlockBundles = cfg.isCheckEnabled("ExploitBlocker", "block-bundles");
        checkBlockBooks = cfg.isCheckEnabled("ExploitBlocker", "block-books");
        checkBlockSkulls = cfg.isCheckEnabled("ExploitBlocker", "block-skulls");
        checkBlockMaps = cfg.isCheckEnabled("ExploitBlocker", "block-maps");
        checkBlockArmorstands = cfg.isCheckEnabled("ExploitBlocker", "block-armorstands");
        checkBlockLecterns = cfg.isCheckEnabled("ExploitBlocker", "block-lecterns");

        maxDisplayNameLength = cfg.getLimitConfig("exploit-blocker.max-display-name-length", 512);
        maxEnchantLevel = cfg.getLimitConfig("exploit-blocker.max-enchant-level", 255);
        maxBookPages = cfg.getLimitConfig("exploit-blocker.max-book-pages", 100);
        maxBeehiveSize = cfg.getLimitConfig("exploit-blocker.max-beehive-size", 10);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreativeSlot(InventoryCreativeEvent event) {
        if (!validateItem(event.getCursor(), (Player) event.getWhoClicked())) {
            event.setCancelled(true);
            event.setCursor(new ItemStack(Material.AIR));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!validateItem(event.getCursor(), (Player) event.getWhoClicked()) ||
                !validateItem(event.getCurrentItem(), (Player) event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!validateItem(event.getItem(), event.getPlayer())) {
            event.setCancelled(true);
            return;
        }

        if (checkBlockLecterns && event.getClickedBlock() != null) {
            if (event.getClickedBlock().getType() == Material.LECTERN && !event.getPlayer().hasPermission("guardian.bypass")) {
                module.flag(event.getPlayer(), "Exploit: Blocked interaction (Lectern)", 2.0);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCraftItem(CraftItemEvent event) {
        ItemStack item = event.getRecipe().getResult();
        if (item.getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        String blockedType = getBlockedItemType(item);
        if (blockedType != null) {

            if (!player.hasPermission("guardian.bypass")) {
                module.flag(player, "Exploit: Trying to craft blocked item (" + blockedType + ")", 2.0);
                event.setCancelled(true);
            }
        }
    }

    private boolean validateItem(ItemStack item, Player player) {
        if (item == null || item.getType() == Material.AIR) return true;

        String blockedType = getBlockedItemType(item);
        if (blockedType != null) {
            if (!player.hasPermission("guardian.bypass")) {
                module.flag(player, "Exploit: Trying to use blocked item (" + blockedType + ")", 2.0);
                return false;
            }
        }

        if (checkInvalidStackSize) {
            if (item.getAmount() < 0 || item.getAmount() > 127) {
                module.flag(player, "Exploit: Invalid stack size", 5.0);
                return false;
            }
        }

        if (!item.hasItemMeta()) return true;
        ItemMeta meta = item.getItemMeta();

        if (checkOversizedDisplayName) {
            if (Objects.requireNonNull(meta).hasDisplayName() && meta.getDisplayName().length() > maxDisplayNameLength) {
                module.flag(player, "Exploit: Oversized DisplayName", 5.0);
                return false;
            }
        }

        if (checkIllegalEnchantLevel) {
            if (Objects.requireNonNull(meta).hasEnchants()) {
                for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                    if (entry.getValue() > maxEnchantLevel || entry.getValue() < 0) {
                        module.flag(player, "Exploit: Illegal Enchantment Level", 5.0);
                        return false;
                    }
                }
            }
        }

        if (checkOversizedBook && meta instanceof BookMeta bookMeta) {
            if (bookMeta.hasPages() && bookMeta.getPageCount() > maxBookPages) {
                module.flag(player, "Exploit: Oversized Book Pages", 5.0);
                return false;
            }
        }

        if (checkNestedShulkerBox && isShulkerBox(item.getType()) && meta instanceof BlockStateMeta bsm) {
            try {
                if (bsm.hasBlockState()) {
                    BlockState state = bsm.getBlockState();
                    if (state instanceof Container container) {
                        for (ItemStack content : container.getInventory().getContents()) {
                            if (content != null && content.hasItemMeta() && content.getItemMeta() instanceof BlockStateMeta) {
                                module.flag(player, "Exploit: Nested Shulker Box", 10.0);
                                return false;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (module.getConfigManager().isDebugMode()) e.printStackTrace();
            }
        }

        if (checkOversizedBeehive && isBeehive(item.getType()) && meta instanceof BlockStateMeta bsm) {
            try {
                if (bsm.hasBlockState()) {
                    BlockState state = bsm.getBlockState();
                    if (state instanceof Beehive beehive) {
                        if (beehive.getEntityCount() > maxBeehiveSize) {
                            module.flag(player, "Exploit: Oversized Beehive", 10.0);
                            return false;
                        }
                    }
                }
            } catch (Exception e) {
                if (module.getConfigManager().isDebugMode()) e.printStackTrace();
            }
        }

        return true;
    }

    private String getBlockedItemType(ItemStack item) {
        Material mat = item.getType();

        if (checkBlockBundles && mat == Material.BUNDLE) {
            return "Bundle";
        }
        if (checkBlockBooks && isBook(mat)) {
            return "Book";
        }
        if (checkBlockSkulls && isSkull(mat)) {
            return "Skull";
        }
        if (checkBlockMaps && isMap(mat)) {
            return "Map";
        }
        if (checkBlockArmorstands && mat == Material.ARMOR_STAND) {
            return "Armor Stand";
        }
        if (checkBlockLecterns && mat == Material.LECTERN) {
            return "Lectern";
        }
        return null;
    }

    private boolean isBook(Material mat) {
        return mat == Material.BOOK || mat == Material.WRITTEN_BOOK || mat == Material.WRITABLE_BOOK
                || mat == Material.ENCHANTED_BOOK || mat == Material.KNOWLEDGE_BOOK;
    }

    private boolean isMap(Material mat) {
        return mat == Material.MAP || mat == Material.FILLED_MAP;
    }

    private boolean isSkull(Material mat) {
        String name = mat.name();
        return name.endsWith("_SKULL") || name.endsWith("_HEAD");
    }

    private boolean isShulkerBox(Material mat) {
        return mat.name().endsWith("SHULKER_BOX");
    }

    private boolean isBeehive(Material mat) {
        return mat == Material.BEEHIVE || mat == Material.BEE_NEST;
    }
}
