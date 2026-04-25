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
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;

/**
 * Validates creative mode slot interactions to prevent item-based exploits.
 */
public class CreativeSlotProcessor implements PacketProcessor {

    private final GuardianModule module;
    private boolean requireCreative;

    public CreativeSlotProcessor(GuardianModule module) {
        this.module = module;
        reloadValues();
    }

    @Override
    public void reloadValues() {
        this.requireCreative = module.getConfigManager().getConfig()
                .getBoolean("limits.crash-shield.creative-slot-require-creative", true);
    }

    @Override
    public boolean process(Object packet, Player player, String packetName, Channel channel) {
        try {
            if (requireCreative && player.getGameMode() != GameMode.CREATIVE) {
                module.flag(player, "Exploit: CreativeSlot outside creative", 10.0);
                return false;
            }

            for (Field f : ReflectionUtil.getCachedFields(packet.getClass())) {
                MethodHandle mh = ReflectionUtil.getGetter(f);
                if (mh == null) continue;
                Object val = mh.invoke(packet);
                if (val == null) continue;

                if (val instanceof Integer || val instanceof Short) {
                    int slot = ((Number) val).intValue();
                    if (!(slot >= 0 && slot <= 45) && slot != -999 && slot != -1 && slot != 999) {
                        module.flag(player, "Exploit: Invalid Creative Slot (" + slot + ")", 10.0);
                        return false;
                    }
                } else if (val instanceof ItemStack stack) {
                    if (stack.getType().getMaxStackSize() > 0 && stack.getAmount() > stack.getType().getMaxStackSize() * 4) {
                        module.flag(player, "Exploit: Oversized Creative Stack (" + stack.getAmount() + ")", 10.0);
                        return false;
                    }
                    if (stack.getAmount() < 0 || stack.getAmount() > 127) {
                        module.flag(player, "Exploit: Illegal Creative Stack size", 10.0);
                        return false;
                    }
                }
            }
        } catch (Throwable e) {
            if (module.getConfigManager().isDebugMode()) e.printStackTrace();
        }
        return true;
    }
}
