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
import me.whitrope.guardian.module.GuardianModule;
import me.whitrope.guardian.processor.PacketProcessor;
import me.whitrope.guardian.util.RateLimiterUtil;
import me.whitrope.guardian.util.ReflectionUtil;
import me.whitrope.guardian.util.UnsafeUtil;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates inventory click packets to prevent illegal item movements.
 */
public class WindowClickProcessor implements PacketProcessor {

    private final GuardianModule module;
    private final Map<Class<?>, PacketFieldMap> fieldCache = new ConcurrentHashMap<>();
    private final RateLimiterUtil secondLimiter = new RateLimiterUtil(1000L);
    private final RateLimiterUtil tickLimiter = new RateLimiterUtil(50L);

    private int maxClicksPerSecond;
    private int maxClicksPerTick;
    private int maxSlotValue;

    public WindowClickProcessor(GuardianModule module) {
        this.module = module;
        reloadValues();
    }

    @Override
    public void reloadValues() {
        this.maxClicksPerSecond = module.getConfigManager().getLimitConfig("activity-guard.max-clicks-per-second", 80);
        this.maxClicksPerTick = module.getConfigManager().getLimitConfig("activity-guard.max-clicks-per-tick", 16);
        this.maxSlotValue = module.getConfigManager().getLimitConfig("activity-guard.max-slot-value", 128);
        this.fieldCache.clear();
    }

    @Override
    public boolean process(Object packet, Player player, String packetName, Channel channel) {
        try {
            if (maxClicksPerTick > 0 && tickLimiter.checkExceeded(player.getUniqueId(), maxClicksPerTick)) {
                module.flag(player, "Exploit: WindowClick burst", 2.5);
                return false;
            }
            if (maxClicksPerSecond > 0 && secondLimiter.checkExceeded(player.getUniqueId(), maxClicksPerSecond)) {
                module.flag(player, "Exploit: WindowClick flood", 5.0);
                return false;
            }

            PacketFieldMap fMap = fieldCache.computeIfAbsent(packet.getClass(), this::mapFields);

            Integer slotNumInt = null;
            if (fMap.slotOffset != -1) {
                if (fMap.slotIsShort) {
                    slotNumInt = (int) UnsafeUtil.getShort(packet, fMap.slotOffset);
                } else {
                    slotNumInt = UnsafeUtil.getInt(packet, fMap.slotOffset);
                }
            }

            Byte modeByte = fMap.modeByteOffset != -1 ? UnsafeUtil.getByte(packet, fMap.modeByteOffset) : null;
            Byte buttonByte = fMap.buttonByteOffset != -1 ? UnsafeUtil.getByte(packet, fMap.buttonByteOffset) : null;
            Integer buttonInt = fMap.buttonIntOffset != -1 ? UnsafeUtil.getInt(packet, fMap.buttonIntOffset) : null;

            int clickTypeOrdinal = -1;
            if (fMap.enumOffset != -1) {
                Enum<?> e = (Enum<?>) UnsafeUtil.getObject(packet, fMap.enumOffset);
                if (e != null) clickTypeOrdinal = e.ordinal();
            }

            if (buttonInt != null && (buttonInt < -999 || buttonInt > 999)) {
                module.flag(player, "Exploit: Invalid WindowClick int (" + buttonInt + ")", 5.0);
                return false;
            }

            if (slotNumInt != null && slotNumInt != -999 && slotNumInt != -1) {
                if (slotNumInt < -999 || slotNumInt > 999) {
                    module.flag(player, "Exploit: Invalid WindowClick int (" + slotNumInt + ")", 5.0);
                    return false;
                }

                if (slotNumInt < 0 || slotNumInt > maxSlotValue) {
                    module.flag(player, "Exploit: Invalid slot (" + slotNumInt + ")", 5.0);
                    return false;
                }
            }

            if (modeByte != null) {
                int mode = modeByte & 0xFF;
                if (mode > 6) {
                    module.flag(player, "Exploit: Invalid WindowClick mode (" + mode + ")", 5.0);
                    return false;
                }

                if (mode == 2 && buttonByte != null) {
                    int btn = buttonByte & 0xFF;
                    if (btn > 8 && btn != 40) {
                        module.flag(player, "Exploit: Invalid SWAP button (" + btn + ")", 5.0);
                        return false;
                    }
                }
            }

            if (clickTypeOrdinal == 2 && buttonInt != null) {
                if (buttonInt < 0 || (buttonInt > 8 && buttonInt != 40)) {
                    module.flag(player, "Exploit: Invalid SWAP button (" + buttonInt + ")", 5.0);
                    return false;
                }
            }
        } catch (Exception e) {
            if (module.getConfigManager().isDebugMode()) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private PacketFieldMap mapFields(Class<?> clazz) {
        PacketFieldMap map = new PacketFieldMap();
        for (Field f : ReflectionUtil.getCachedFields(clazz)) {
            long offset = UnsafeUtil.objectFieldOffset(f);
            if (offset == -1) continue;

            Class<?> type = f.getType();
            if (type == byte.class) {
                if (map.modeByteOffset == -1) {
                    map.modeByteOffset = offset;
                } else if (map.buttonByteOffset == -1) {
                    map.buttonByteOffset = offset;
                }
            } else if (Enum.class.isAssignableFrom(type)) {
                String enumName = type.getSimpleName();
                if (enumName.contains("Click") || enumName.contains("Action") || enumName.contains("Mode")) {
                    map.enumOffset = offset;
                }
            } else if (type == int.class) {
                String fieldName = f.getName().toLowerCase();
                if (fieldName.contains("slot") || fieldName.contains("slotnum")) {
                    map.slotOffset = offset;
                } else if (fieldName.contains("button") || fieldName.equals("d")) {
                    map.buttonIntOffset = offset;
                }
            } else if (type == short.class) {
                String fieldName = f.getName().toLowerCase();
                if (fieldName.contains("slot") || fieldName.contains("slotnum")) {
                    map.slotOffset = offset;
                    map.slotIsShort = true;
                }
            }
        }
        return map;
    }

    private static class PacketFieldMap {
        long modeByteOffset = -1;
        long buttonByteOffset = -1;
        long enumOffset = -1;
        long slotOffset = -1;
        long buttonIntOffset = -1;
        boolean slotIsShort = false;
    }
}
