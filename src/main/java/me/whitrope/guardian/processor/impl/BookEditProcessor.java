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


package me.whitrope.guardian.processor.impl;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import me.whitrope.guardian.module.GuardianModule;
import me.whitrope.guardian.processor.PacketProcessor;
import me.whitrope.guardian.util.AttributeUtil;
import me.whitrope.guardian.util.ReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes and validates book edit packets to prevent crashes.
 */
public class BookEditProcessor implements PacketProcessor {

    private static final AttributeKey<BookData> BOOK_DATA_KEY = AttributeKey.valueOf("guardian_book_data");

    private final GuardianModule module;
    private int maxPages;
    private int maxPageLength;
    private int maxTotalLength;
    private int maxColorCodes;
    private int maxEditsPerSecond;

    public BookEditProcessor(GuardianModule module) {
        this.module = module;
        reloadValues();
    }

    @Override
    public void reloadValues() {
        this.maxPages = module.getConfigManager().getLimitConfig("crash-shield.book.max-pages", 50);
        this.maxPageLength = module.getConfigManager().getLimitConfig("crash-shield.book.max-page-length", 2048);
        this.maxTotalLength = module.getConfigManager().getLimitConfig("crash-shield.book.max-total-length", 12288);
        this.maxColorCodes = module.getConfigManager().getLimitConfig("crash-shield.book.max-color-codes", 32);
        this.maxEditsPerSecond = module.getConfigManager().getLimitConfig("crash-shield.book.max-edits-per-second", 2);
    }

    @Override
    public boolean process(Object packet, Player player, String packetName, Channel channel) {
        try {
            Bukkit.getScheduler().runTask(module.getPlugin(), () -> {
                if (player == null || !player.isOnline()) return;
                try {
                    ItemStack mainHand = player.getInventory().getItemInMainHand();
                    ItemStack offHand = player.getInventory().getItemInOffHand();
                    if (!isBook(mainHand) && !isBook(offHand)) {
                        module.flag(player, "Exploit: Ghost Book Edit", 10.0);
                    }
                } catch (Exception ignored) {
                }
            });

            BookData data = AttributeUtil.getOrCreate(channel, BOOK_DATA_KEY, BookData::new);
            long now = System.currentTimeMillis();
            if (now - data.windowStart >= 1000L) {
                data.windowStart = now;
                data.edits.set(0);
            }
            if (maxEditsPerSecond > 0 && data.edits.incrementAndGet() > maxEditsPerSecond) {
                module.flag(player, "Exploit: Book edit flood", 5.0);
                return false;
            }

            for (Field f : ReflectionUtil.getCachedFields(packet.getClass())) {
                MethodHandle mh = ReflectionUtil.getGetter(f);
                if (mh == null) continue;
                Object val = mh.invoke(packet);
                if (val instanceof List<?> list) {
                    if (!validatePages(list, player)) return false;
                } else if (val instanceof Collection<?> col) {
                    if (!validatePages(col, player)) return false;
                }
            }
        } catch (Throwable e) {
            if (module.getConfigManager().isDebugMode()) e.printStackTrace();
        }

        return true;
    }

    private boolean validatePages(Collection<?> pages, Player player) {
        if (pages.isEmpty()) return true;
        Object first = pages.iterator().next();
        if (!(first instanceof String)) return true;

        if (maxPages > 0 && pages.size() > maxPages) {
            module.flag(player, "Exploit: Book pages count (" + pages.size() + ")", 10.0);
            return false;
        }

        int total = 0;
        for (Object o : pages) {
            if (!(o instanceof String s)) continue;
            if (maxPageLength > 0 && s.length() > maxPageLength) {
                module.flag(player, "Exploit: Oversized book page", 10.0);
                return false;
            }
            if (maxColorCodes > 0) {
                int cc = 0;
                for (int i = 0; i < s.length(); i++) {
                    if (s.charAt(i) == '§') cc++;
                }
                if (cc > maxColorCodes) {
                    module.flag(player, "Exploit: Book color-code spam", 10.0);
                    return false;
                }
            }
            total += s.length();
            if (maxTotalLength > 0 && total > maxTotalLength) {
                module.flag(player, "Exploit: Oversized book total", 10.0);
                return false;
            }
        }
        return true;
    }

    private boolean isBook(ItemStack item) {
        if (item == null) return false;
        Material mat = item.getType();
        return mat == Material.WRITABLE_BOOK || mat == Material.WRITTEN_BOOK;
    }

    private static class BookData {
        final AtomicInteger edits = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
    }
}
