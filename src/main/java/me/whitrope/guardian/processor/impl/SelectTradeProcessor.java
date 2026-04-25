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
import me.whitrope.guardian.util.ReflectionUtil;
import me.whitrope.guardian.util.UnsafeUtil;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

/**
 * Validates villager trade selection packets to prevent index out of bounds crashes.
 */
public class SelectTradeProcessor implements PacketProcessor {

    private final GuardianModule module;
    private long tradeIndexOffset = -1;
    private boolean fieldsMapped = false;

    public SelectTradeProcessor(GuardianModule module) {
        this.module = module;
    }

    @Override
    public boolean process(Object packet, Player player, String packetName, Channel channel) {
        if (!fieldsMapped) {
            mapFields(packet.getClass());
        }

        if (tradeIndexOffset != -1) {
            int tradeIndex = UnsafeUtil.getInt(packet, tradeIndexOffset);
            
            if (tradeIndex < 0 || tradeIndex > 128) {
                module.flag(player, "Exploit: Invalid Trade Index (" + tradeIndex + ")", 10.0);
                return false;
            }
        }

        return true;
    }

    private void mapFields(Class<?> clazz) {
        for (Field f : ReflectionUtil.getCachedFields(clazz)) {
            if (f.getType() == int.class) {
                tradeIndexOffset = UnsafeUtil.objectFieldOffset(f);
                break;
            }
        }
        fieldsMapped = true;
    }
}
