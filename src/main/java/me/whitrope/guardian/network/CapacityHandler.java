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
 * Manages network capacity and prevents packet flooding at the network layer.
 */
package me.whitrope.guardian.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.violation.ViolationLog;
import me.whitrope.guardian.violation.ViolationUser;
import org.bukkit.entity.Player;

public class CapacityHandler extends ChannelInboundHandlerAdapter {

    private final Guardian plugin;
    private final Player player;
    private final int maxCapacity;
    private final int maxBandwidth;
    private long lastBandwidthReset;
    private int currentBandwidth;

    public CapacityHandler(Guardian plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.maxCapacity = plugin.getConfigManager().getLimitConfig("packet-guard.max-capacity", 32768);
        this.maxBandwidth = plugin.getConfigManager().getLimitConfig("packet-guard.max-bandwidth", 1048576);
        this.lastBandwidthReset = System.currentTimeMillis();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
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

            boolean passThrough = false;
            try {
                int capacity = buf.capacity();
                int readableBytes = buf.readableBytes();

                if (capacity < 0 || readableBytes < 0 || (readableBytes == 0 && !buf.isReadable()) || buf.getClass().getSimpleName().contains("EmptyByteBuf")) {
                    return;
                }

                long now = System.currentTimeMillis();
                if (now - lastBandwidthReset >= 1000L) {
                    lastBandwidthReset = now;
                    currentBandwidth = 0;
                }

                if (maxBandwidth > 0) {
                    currentBandwidth += readableBytes;
                    if (currentBandwidth > maxBandwidth) {
                        if (plugin.getConfigManager().isDebugMode()) {
                            plugin.getLogger().warning("Dropped raw packet from " + player.getName() + " due to exceeding bandwidth limit: " + currentBandwidth + " bytes");
                        }

                        boolean isCrash = plugin.getConfigManager().isCrashBack();
                        plugin.getViolationManager().addLog(new ViolationLog(player.getName(), "PacketGuard", "Bandwidth limit exceeded: " + currentBandwidth + " bytes", isCrash));
                        plugin.getPunishmentService().punish(player, "&cConnection lost (Bandwidth limit exceeded)");
                        return;
                    }
                }

                if (maxCapacity > 0 && capacity > maxCapacity) {
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().warning("Dropped raw packet from " + player.getName() + " due to exceeding capacity: " + capacity + " > " + maxCapacity);
                    }

                    boolean isCrash = plugin.getConfigManager().isCrashBack();
                    plugin.getViolationManager().addLog(new ViolationLog(player.getName(), "PacketGuard", "Reached capacity limit: " + capacity + " > " + maxCapacity, isCrash));
                    plugin.getPunishmentService().punish(player, "&cConnection lost (Reached capacity limit)");
                    return;
                }

                passThrough = true;
            } finally {
                if (passThrough) {
                    super.channelRead(ctx, msg);
                } else {
                    buf.release();
                }
            }
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
