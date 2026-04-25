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
 * Enforces rate limits on various packet types to prevent spam.
 */
package me.whitrope.guardian.processor.impl;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import me.whitrope.guardian.module.GuardianModule;
import me.whitrope.guardian.processor.PacketProcessor;
import me.whitrope.guardian.util.AttributeUtil;
import org.bukkit.entity.Player;

public class RateLimitProcessor implements PacketProcessor {

    private final GuardianModule module;
    private final String label;
    private final String configPath;
    private final int defaultPerSecond;
    private final int defaultPerTick;
    private final double vl;
    private final AttributeKey<Data> key;

    private volatile int maxPerSecond;
    private volatile int maxPerTick;

    public RateLimitProcessor(GuardianModule module, String label, String configPath,
                              int defaultPerSecond, int defaultPerTick, double vl) {
        this.module = module;
        this.label = label;
        this.configPath = configPath;
        this.defaultPerSecond = defaultPerSecond;
        this.defaultPerTick = defaultPerTick;
        this.vl = vl;
        this.key = AttributeKey.valueOf("guardian_ratelimit_" + label);
        reloadValues();
    }

    @Override
    public void reloadValues() {
        this.maxPerSecond = module.getConfigManager().getLimitConfig(configPath + ".per-second", defaultPerSecond);
        this.maxPerTick = module.getConfigManager().getLimitConfig(configPath + ".per-tick", defaultPerTick);
    }

    @Override
    public boolean process(Object packet, Player player, String packetName, Channel channel) {
        Data data = AttributeUtil.getOrCreate(channel, key, Data::new);
        long now = System.currentTimeMillis();
        if (now - data.secondStart >= 1000L) {
            data.secondStart = now;
            data.perSecond = 0;
        }
        if (now - data.tickStart >= 50L) {
            data.tickStart = now;
            data.perTick = 0;
        }

        int pt = ++data.perTick;
        int ps = ++data.perSecond;

        if (maxPerTick > 0 && pt > maxPerTick) {
            module.flag(player, "Flood: " + label + " (" + pt + "/tick)", vl);
            return false;
        }
        if (maxPerSecond > 0 && ps > maxPerSecond) {
            module.flag(player, "Flood: " + label + " (" + ps + "/s)", vl);
            return false;
        }
        return true;
    }

    private static final class Data {
        int perSecond;
        int perTick;
        long secondStart = System.currentTimeMillis();
        long tickStart = System.currentTimeMillis();
    }
}
