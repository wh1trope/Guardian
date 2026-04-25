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
 * Validates inventory click packets to prevent illegal item movements.
 */
package me.whitrope.guardian.processor.impl;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import me.whitrope.guardian.module.GuardianModule;
import me.whitrope.guardian.processor.PacketProcessor;
import me.whitrope.guardian.util.AttributeUtil;
import me.whitrope.guardian.util.ReflectionUtil;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class WindowClickProcessor implements PacketProcessor {

    private static final AttributeKey<ClickData> CLICK_DATA_KEY = AttributeKey.valueOf("guardian_click_data");

    private final GuardianModule module;
    private final Map<Class<?>, PacketFieldMap> fieldCache = new ConcurrentHashMap<>();
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
            ClickData data = AttributeUtil.getOrCreate(channel, CLICK_DATA_KEY, ClickData::new);

            long now = System.currentTimeMillis();
            if (now - data.windowStart >= 1000L) {
                data.windowStart = now;
                data.perSecond.set(0);
            }
            if (now - data.tickStart >= 50L) {
                data.tickStart = now;
                data.perTick.set(0);
            }

            int ps = data.perSecond.incrementAndGet();
            int pt = data.perTick.incrementAndGet();
            if (maxClicksPerTick > 0 && pt > maxClicksPerTick) {
                module.flag(player, "Exploit: WindowClick burst (" + pt + "/tick)", 2.5);
                return false;
            }
            if (maxClicksPerSecond > 0 && ps > maxClicksPerSecond) {
                module.flag(player, "Exploit: WindowClick flood (" + ps + "/s)", 5.0);
                return false;
            }

            PacketFieldMap fMap = fieldCache.computeIfAbsent(packet.getClass(), this::mapFields);

            Integer slotNumInt = fMap.slotField != null ? getIntOrShort(fMap.slotField, packet) : null;
            Byte modeByte = fMap.modeByteField != null ? (Byte) fMap.modeByteField.invoke(packet) : null;
            Byte buttonByte = fMap.buttonByteField != null ? (Byte) fMap.buttonByteField.invoke(packet) : null;
            Integer buttonInt = fMap.buttonIntField != null ? (Integer) fMap.buttonIntField.invoke(packet) : null;

            int clickTypeOrdinal = -1;
            if (fMap.enumField != null) {
                Enum<?> e = (Enum<?>) fMap.enumField.invoke(packet);
                if (e != null) clickTypeOrdinal = e.ordinal();
            }

            if (fMap.buttonIntField != null && buttonInt != null && (buttonInt < -999 || buttonInt > 999)) {
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
        } catch (Throwable e) {
            if (module.getConfigManager().isDebugMode()) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private Integer getIntOrShort(MethodHandle mh, Object packet) {
        try {
            Object val = mh.invoke(packet);
            if (val instanceof Integer) return (Integer) val;
            if (val instanceof Short) return ((Short) val).intValue();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private PacketFieldMap mapFields(Class<?> clazz) {
        PacketFieldMap map = new PacketFieldMap();
        for (Field f : ReflectionUtil.getCachedFields(clazz)) {
            MethodHandle mh = ReflectionUtil.getGetter(f);
            if (mh == null) continue;

            Class<?> type = f.getType();
            if (type == byte.class || type == Byte.class) {
                if (map.modeByteField == null) {
                    map.modeByteField = mh;
                } else if (map.buttonByteField == null) {
                    map.buttonByteField = mh;
                }
            } else if (Enum.class.isAssignableFrom(type)) {
                String enumName = type.getSimpleName();
                if (enumName.contains("Click") || enumName.contains("Action") || enumName.contains("Mode")) {
                    map.enumField = mh;
                }
            } else if (type == int.class || type == Integer.class) {
                String fieldName = f.getName().toLowerCase();
                if (fieldName.contains("slot") || fieldName.contains("slotnum")) {
                    map.slotField = mh;
                } else if (fieldName.contains("button") || fieldName.equals("d")) {
                    map.buttonIntField = mh;
                }
            } else if (type == short.class || type == Short.class) {
                String fieldName = f.getName().toLowerCase();
                if (fieldName.contains("slot")) {
                    map.slotField = mh;
                }
            }
        }
        return map;
    }

    private static class PacketFieldMap {
        MethodHandle modeByteField;
        MethodHandle buttonByteField;
        MethodHandle enumField;
        MethodHandle slotField;
        MethodHandle buttonIntField;
    }

    private static class ClickData {
        final AtomicInteger perSecond = new AtomicInteger(0);
        final AtomicInteger perTick = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
        volatile long tickStart = System.currentTimeMillis();
    }
}
