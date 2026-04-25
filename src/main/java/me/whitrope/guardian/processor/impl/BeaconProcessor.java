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
import java.util.Optional;

/**
 * Validates Beacon set effect packets to prevent crashes from invalid effect IDs.
 */
public class BeaconProcessor implements PacketProcessor {

    private final GuardianModule module;
    private long primaryOffset = -1;
    private long secondaryOffset = -1;
    private boolean fieldsMapped = false;
    private boolean usesOptional = false;

    public BeaconProcessor(GuardianModule module) {
        this.module = module;
    }

    @Override
    public boolean process(Object packet, Player player, String packetName, Channel channel) {
        if (!fieldsMapped) {
            mapFields(packet.getClass());
        }

        if (primaryOffset != -1) {
            if (usesOptional) {
                Object primary = UnsafeUtil.getObject(packet, primaryOffset);
                if (primary instanceof Integer val && val > 32) {
                    module.flag(player, "Exploit: Invalid Beacon Effect ID (" + val + ")", 10.0);
                    return false;
                } else if (primary instanceof Optional<?> opt) {
                    if (opt.isPresent() && opt.get().toString().contains("minecraft:") == false && opt.get().toString().length() > 50) {
                         module.flag(player, "Exploit: Malformed Beacon Effect", 5.0);
                         return false;
                    }
                }
            } else {
                int primary = UnsafeUtil.getInt(packet, primaryOffset);
                if (primary < -1 || primary > 32) { // 32 is the max vanilla potion effect ID roughly, -1 is none
                     module.flag(player, "Exploit: Invalid Beacon Effect ID (" + primary + ")", 10.0);
                     return false;
                }
            }
        }

        if (secondaryOffset != -1 && !usesOptional) {
             int secondary = UnsafeUtil.getInt(packet, secondaryOffset);
             if (secondary < -1 || secondary > 32) {
                  module.flag(player, "Exploit: Invalid Beacon Effect ID (" + secondary + ")", 10.0);
                  return false;
             }
        }

        return true;
    }

    private void mapFields(Class<?> clazz) {
        for (Field f : ReflectionUtil.getCachedFields(clazz)) {
            if (f.getType() == int.class) {
                long offset = UnsafeUtil.objectFieldOffset(f);
                if (primaryOffset == -1) {
                    primaryOffset = offset;
                } else if (secondaryOffset == -1) {
                    secondaryOffset = offset;
                }
            } else if (f.getType() == Optional.class) {
                long offset = UnsafeUtil.objectFieldOffset(f);
                if (primaryOffset == -1) {
                    primaryOffset = offset;
                    usesOptional = true;
                } else if (secondaryOffset == -1) {
                    secondaryOffset = offset;
                }
            }
        }
        fieldsMapped = true;
    }
}
