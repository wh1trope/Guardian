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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.violation.ViolationLog;
import me.whitrope.guardian.violation.ViolationUser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Manages network capacity and prevents packet flooding at the network layer.
 */
public class CapacityHandler extends ChannelInboundHandlerAdapter {

    private final Guardian plugin;
    private final UUID playerUUID;
    private final String playerName;
    private final int maxCapacity;
    private final int maxBandwidth;
    private long lastBandwidthReset;
    private int currentBandwidth;

    public CapacityHandler(Guardian plugin, Player player) {
        this.plugin = plugin;
        this.playerUUID = player.getUniqueId();
        this.playerName = player.getName();
        this.maxCapacity = plugin.getConfigManager().getLimitConfig("packet-guard.max-capacity", 32768);
        this.maxBandwidth = plugin.getConfigManager().getLimitConfig("packet-guard.max-bandwidth", 1048576);
        this.lastBandwidthReset = System.currentTimeMillis();
    }

    private Player getPlayer() {
        return Bukkit.getPlayer(playerUUID);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Player player = getPlayer();
        if (player == null) {
            super.channelRead(ctx, msg);
            return;
        }

        if (!player.isOnline()) {
            releaseMsg(msg);
            return;
        }

        ViolationUser user = plugin.getViolationManager().peekUser(player);
        if (user != null && user.isPendingKick()) {
            releaseMsg(msg);
            return;
        }

        if (msg instanceof ByteBuf buf) {
            int capacity = buf.capacity();
            int readableBytes = buf.readableBytes();

            if (!buf.isReadable() || capacity == 0) {
                buf.release();
                return;
            }

            currentBandwidth += readableBytes;
            if (maxBandwidth > 0 && currentBandwidth > maxBandwidth) {
                long now = System.currentTimeMillis();
                if (now - lastBandwidthReset >= 1000L) {
                    lastBandwidthReset = now;
                    currentBandwidth = readableBytes;
                } else {
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().warning("Dropped raw packet from " + playerName + " due to exceeding bandwidth limit: " + currentBandwidth + " bytes");
                    }
                    boolean isCrash = plugin.getConfigManager().isCrashBack();
                    plugin.getViolationManager().addLog(new ViolationLog(playerName, "PacketGuard", "Bandwidth limit exceeded: " + currentBandwidth + " bytes", isCrash));
                    plugin.getPunishmentService().punish(player, "&cConnection lost (Bandwidth limit exceeded)");
                    buf.release();
                    return;
                }
            } else if (maxBandwidth > 0 && currentBandwidth > (maxBandwidth / 2) && currentBandwidth % 1024 == 0) {
                long now = System.currentTimeMillis();
                if (now - lastBandwidthReset >= 1000L) {
                    lastBandwidthReset = now;
                    currentBandwidth = readableBytes;
                }
            }

            if (maxCapacity > 0 && capacity > maxCapacity) {
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().warning("Dropped raw packet from " + playerName + " due to exceeding capacity: " + capacity + " > " + maxCapacity);
                }

                boolean isCrash = plugin.getConfigManager().isCrashBack();
                plugin.getViolationManager().addLog(new ViolationLog(playerName, "PacketGuard", "Reached capacity limit: " + capacity + " > " + maxCapacity, isCrash));
                plugin.getPunishmentService().punish(player, "&cConnection lost (Reached capacity limit)");
                buf.release();
                return;
            }

            super.channelRead(ctx, msg);
            return;
        }

        super.channelRead(ctx, msg);
    }

    private void releaseMsg(Object msg) {
        if (msg instanceof ByteBuf buf) {
            buf.release();
        } else {
            ReferenceCountUtil.release(msg);
        }
    }
}
