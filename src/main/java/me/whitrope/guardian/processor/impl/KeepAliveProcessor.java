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
 * Monitors keep-alive packets to detect potential network manipulation.
 */
package me.whitrope.guardian.processor.impl;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import me.whitrope.guardian.module.GuardianModule;
import me.whitrope.guardian.processor.PacketProcessor;
import me.whitrope.guardian.util.ReflectionUtil;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

public class KeepAliveProcessor implements PacketProcessor {

    private static final AttributeKey<Data> KEY = AttributeKey.valueOf("guardian_keepalive");

    private final GuardianModule module;
    private int maxPerSecond;

    public KeepAliveProcessor(GuardianModule module) {
        this.module = module;
        reloadValues();
    }

    @Override
    public void reloadValues() {
        this.maxPerSecond = module.getConfigManager().getLimitConfig("activity-guard.keepalive.per-second", 4);
    }

    @Override
    public boolean process(Object packet, Player player, String packetName, Channel channel) {
        try {
            Data data = channel.attr(KEY).get();
            if (data == null) {
                data = new Data();
                channel.attr(KEY).set(data);
            }
            long now = System.currentTimeMillis();
            if (now - data.windowStart >= 1000L) {
                data.windowStart = now;
                data.count.set(0);
            }
            if (maxPerSecond > 0 && data.count.incrementAndGet() > maxPerSecond) {
                module.flag(player, "Exploit: KeepAlive flood", 10.0);
                return false;
            }

            for (Field f : ReflectionUtil.getCachedFields(packet.getClass())) {
                Object val = f.get(packet);
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
        } catch (Exception e) {
            if (module.getConfigManager().isDebugMode()) e.printStackTrace();
        }
        return true;
    }

    private static class Data {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
        volatile long lastId = -1L;
    }
}
