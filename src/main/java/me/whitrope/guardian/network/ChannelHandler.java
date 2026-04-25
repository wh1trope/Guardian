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


package me.whitrope.guardian.network;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.violation.ViolationLog;
import me.whitrope.guardian.violation.ViolationUser;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Custom Netty channel handler for intercepting and processing incoming packets.
 */
public class ChannelHandler extends ChannelDuplexHandler {

    private final Guardian plugin;
    private final Player player;
    private final int hardcapPacketsPerSecond;
    private final boolean hardcapEnabled;
    private final ViolationUser cachedUser;

    private long lastHardcapReset = System.currentTimeMillis();
    private int packetCount = 0;

    public ChannelHandler(Guardian plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.hardcapPacketsPerSecond = plugin.getConfigManager().getLimitConfig("packet-guard.hardcap-pps", 500);
        this.hardcapEnabled = this.hardcapPacketsPerSecond > 0;
        this.cachedUser = plugin.getViolationManager().getUser(player);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!player.isOnline()) {
            ReferenceCountUtil.release(msg);
            return;
        }

        if (cachedUser != null && cachedUser.isPendingKick()) {
            ReferenceCountUtil.release(msg);
            return;
        }

        if (hardcapEnabled) {
            long now = System.currentTimeMillis();
            if (now - lastHardcapReset >= 1000L) {
                lastHardcapReset = now;
                packetCount = 0;
            }

            if (++packetCount > hardcapPacketsPerSecond) {

                if (cachedUser != null && !cachedUser.isPendingKick()) {
                    boolean isCrash = plugin.getConfigManager().isCrashBack();
                    plugin.getViolationManager().addLog(new ViolationLog(player.getName(), "PacketGuard", "Packet flood: " + packetCount + " pps", isCrash));
                    plugin.getPunishmentService().punish(player, "&cConnection lost (Packet flood)");
                }
                ReferenceCountUtil.release(msg);
                return;
            }
        }

        try {
            if (!plugin.getModuleManager().handleIncomingPacket(msg, player, ctx.channel())) {
                ReferenceCountUtil.release(msg);
                return;
            }
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().log(Level.SEVERE,
                        "Error handling incoming packet for " + player.getName(), e);
            }
        }

        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            if (!plugin.getModuleManager().handleOutgoingPacket(msg, player)) {
                ReferenceCountUtil.release(msg);
                promise.setSuccess();
                return;
            }
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().log(Level.SEVERE,
                        "Error handling outgoing packet for " + player.getName(), e);
            }
        }

        super.write(ctx, msg, promise);
    }
}
