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
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import me.whitrope.guardian.util.UnsafeUtil;

/**
 * Monitors keep-alive packets to detect potential network manipulation.
 */
public class KeepAliveProcessor implements PacketProcessor {

    private static final AttributeKey<Data> KEY = AttributeKey.valueOf("guardian_keepalive");

    private final GuardianModule module;
    private int maxPerSecond;
    private final Map<Class<?>, long[]> fieldCache = new ConcurrentHashMap<>();

    public KeepAliveProcessor(GuardianModule module) {
        this.module = module;
        reloadValues();
    }

    @Override
    public void reloadValues() {
        this.maxPerSecond = module.getConfigManager().getLimitConfig("activity-guard.keepalive.per-second", 4);
        this.fieldCache.clear();
    }

    @Override
    public boolean process(Object packet, Player player, String packetName, Channel channel) {
        Data data = AttributeUtil.getOrCreate(channel, KEY, Data::new);
        long now = System.currentTimeMillis();
        if (now - data.windowStart >= 1000L) {
            data.windowStart = now;
            data.count.set(0);
        }
        if (maxPerSecond > 0 && data.count.incrementAndGet() > maxPerSecond) {
            module.flag(player, "Exploit: KeepAlive flood", 10.0);
            return false;
        }

        long[] offsets = fieldCache.computeIfAbsent(packet.getClass(), this::mapFields);

        for (long offset : offsets) {
            Object val = UnsafeUtil.getObject(packet, offset);
            if (val instanceof Long l) {
                if (l == 0L && data.lastId == 0L) {
                    module.flag(player, "Exploit: KeepAlive zero-id spam", 10.0);
                    return false;
                }
                data.lastId = l;
            } else if (val instanceof Integer i) {
                if (i == 0 && data.lastId == 0L) {
                    module.flag(player, "Exploit: KeepAlive zero-id spam", 10.0);
                    return false;
                }
                data.lastId = i;
            }
        }
        return true;
    }

    private long[] mapFields(Class<?> clazz) {
        List<Long> offsets = new ArrayList<>();
        for (Field f : ReflectionUtil.getCachedFields(clazz)) {
            Class<?> t = f.getType();
            if (t == long.class || t == int.class || t == Long.class || t == Integer.class) {
                long offset = UnsafeUtil.objectFieldOffset(f);
                if (offset != -1) {
                    offsets.add(offset);
                }
            }
        }
        return offsets.stream().mapToLong(l -> l).toArray();
    }

    private static class Data {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
        volatile long lastId = -1L;
    }
}
