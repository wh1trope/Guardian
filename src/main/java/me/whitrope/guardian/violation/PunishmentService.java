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


package me.whitrope.guardian.violation;

import io.netty.channel.Channel;
import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Date;

/**
 * Handles the execution of punishments (kick, ban, etc.) for violations.
 */
public class PunishmentService {

    private final Guardian plugin;

    public PunishmentService(Guardian plugin) {
        this.plugin = plugin;
    }

    public void punish(Player player, String kickReason) {
        ViolationUser user = plugin.getViolationManager().getUser(player);
        if (user.isPendingKick()) return;
        user.setPendingKick(true);

        boolean crashBack = plugin.getConfigManager().isCrashBack();
        if (plugin.getConfigManager().isLogExploits()) {
            plugin.getLogger().info("(PUNISH) " + player.getName() + " - Kick Reason: " + kickReason);
        }
        executePunishment(player, crashBack, kickReason);
    }

    public void executePunishment(Player player, boolean crashBack, String kickReason) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                closeChannelSilently(player);
                return;
            }
            applyTempBan(player);
            if (crashBack) {
                crashClient(player);
            } else {
                String kickMsg = ChatUtil.fix("§8§l<< §b§lGuardian §8§l>>\n \n" + kickReason);
                player.kickPlayer(kickMsg);
            }
        }, 5L);
    }

    private void closeChannelSilently(Player player) {
        try {
            Channel channel = plugin.getNmsProvider().getChannel(player);
            if (channel != null) channel.close();
        } catch (Exception ignored) {
        }
    }

    private void crashClient(Player player) {
        try {
            player.spawnParticle(Particle.EXPLOSION_HUGE, player.getLocation(),
                    Integer.MAX_VALUE, 1000, 1000, 1000, 1000);
        } catch (Throwable ignored) {
        }

        closeChannelSilently(player);
    }

    private void applyTempBan(Player player) {
        if (plugin.getConfigManager().getConfig().getBoolean("settings.temp-ban.enabled", false)) {
            int duration = plugin.getConfigManager().getConfig().getInt("settings.temp-ban.duration-minutes", 5);
            Date expires = new Date(System.currentTimeMillis() + (duration * 60000L));
            String banReason = ChatUtil.fix("\n\n§8§l<< §b§lGuardian §8§l>>\n \n§cBlocked by security system.\n§7Tempbanned for §f" + duration + " §7minutes.\n");
            player.banIp(banReason, expires, "Guardian", true);
        }
    }
}
