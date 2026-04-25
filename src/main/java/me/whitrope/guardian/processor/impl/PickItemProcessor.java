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
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import me.whitrope.guardian.util.UnsafeUtil;

/**
 * Validates pick-item packets to prevent item duplication or crashes.
 */
public class PickItemProcessor implements PacketProcessor {

    private final GuardianModule module;

    public PickItemProcessor(GuardianModule module) {
        this.module = module;
    }

    @Override
    public boolean process(Object packet, Player player, String packetName, Channel channel) {
        for (Field f : ReflectionUtil.getCachedFields(packet.getClass())) {
            long offset = UnsafeUtil.objectFieldOffset(f);
            if (offset == -1) continue;
            Object val = UnsafeUtil.getObject(packet, offset);
            if (val instanceof Integer slot) {
                if (slot < 0 || slot > 45) {
                    module.flag(player, "Exploit: Invalid PickItem slot (" + slot + ")", 5.0);
                    return false;
                }
            } else if (val instanceof Short slot) {
                if (slot < 0 || slot > 45) {
                    module.flag(player, "Exploit: Invalid PickItem slot (" + slot + ")", 5.0);
                    return false;
                }
            }
        }
        return true;
    }
}
