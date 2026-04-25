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
 * Validates sign update packets to prevent text-based exploits or crashes.
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

public class SignUpdateProcessor implements PacketProcessor {

    private static final AttributeKey<Data> KEY = AttributeKey.valueOf("guardian_sign_update");

    private final GuardianModule module;
    private int maxLineLength;
    private int maxColorCodes;
    private int maxPerSecond;

    public SignUpdateProcessor(GuardianModule module) {
        this.module = module;
        reloadValues();
    }

    @Override
    public void reloadValues() {
        this.maxLineLength = module.getConfigManager().getLimitConfig("activity-guard.sign.max-line-length", 384);
        this.maxColorCodes = module.getConfigManager().getLimitConfig("activity-guard.sign.max-color-codes", 16);
        this.maxPerSecond = module.getConfigManager().getLimitConfig("activity-guard.sign.per-second", 4);
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
                module.flag(player, "Exploit: Sign update flood", 5.0);
                return false;
            }

            for (Field f : ReflectionUtil.getCachedFields(packet.getClass())) {
                Object val = f.get(packet);
                if (val instanceof String[] arr) {
                    if (arr.length > 4) {
                        module.flag(player, "Exploit: Sign has " + arr.length + " lines", 10.0);
                        return false;
                    }
                    for (String line : arr) {
                        if (!checkLine(line, player)) return false;
                    }
                } else if (val instanceof String s) {
                    if (!checkLine(s, player)) return false;
                }
            }
        } catch (Exception e) {
            if (module.getConfigManager().isDebugMode()) e.printStackTrace();
        }
        return true;
    }

    private boolean checkLine(String s, Player player) {
        if (s == null) return true;
        if (maxLineLength > 0 && s.length() > maxLineLength) {
            module.flag(player, "Exploit: Sign line length " + s.length(), 10.0);
            return false;
        }
        int cc = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') cc++;
            if (c == '\u0000') {
                module.flag(player, "Exploit: NUL in sign line", 10.0);
                return false;
            }
        }
        if (maxColorCodes > 0 && cc > maxColorCodes) {
            module.flag(player, "Exploit: Sign color-code spam", 5.0);
            return false;
        }
        return true;
    }

    private static class Data {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
    }
}
