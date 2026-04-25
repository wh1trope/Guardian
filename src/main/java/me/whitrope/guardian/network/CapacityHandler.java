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
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.net.InetSocketAddress;

/**
 * Manages network capacity and prevents packet flooding at the network layer.
 */
public class CapacityHandler extends ChannelInboundHandlerAdapter {

    private final Guardian plugin;
    private final Player player;
    private final ViolationUser cachedUser;
    private final String playerName;
    private final int maxCapacity;
    private final int maxBandwidth;
    private final long maxBandwidthPerIp;
    private long lastBandwidthReset;
    private int currentBandwidth;
    private String clientIp;

    private static final Map<String, IpData> ipDataMap = new ConcurrentHashMap<>();

    public CapacityHandler(Guardian plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.cachedUser = plugin.getViolationManager().getUser(player);
        this.playerName = player.getName();
        this.maxCapacity = plugin.getConfigManager().getLimitConfig("packet-guard.max-capacity", 32768);
        this.maxBandwidth = plugin.getConfigManager().getLimitConfig("packet-guard.max-bandwidth", 1048576);
        this.maxBandwidthPerIp = plugin.getConfigManager().getLimitLong("packet-guard.max-bandwidth-per-ip", 5242880);
        this.lastBandwidthReset = System.currentTimeMillis();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress address) {
            this.clientIp = address.getAddress().getHostAddress();
        } else {
            this.clientIp = "unknown";
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!player.isOnline()) {
            releaseMsg(msg);
            return;
        }

        if (cachedUser != null && cachedUser.isPendingKick()) {
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
            long now = System.currentTimeMillis();

            if (clientIp != null && !clientIp.equals("unknown") && maxBandwidthPerIp > 0) {
                IpData data = ipDataMap.computeIfAbsent(clientIp, k -> new IpData());
                synchronized (data) {
                    if (now - data.lastCheck > 1000) {
                        data.lastCheck = now;
                        data.bytes = 0;
                    }
                    data.bytes += readableBytes;
                    if (data.bytes > maxBandwidthPerIp) {
                        if (plugin.getConfigManager().isDebugMode()) {
                            plugin.getLogger().warning("Dropped raw packet from " + playerName + " (IP " + clientIp + ") due to global IP bandwidth limit: " + data.bytes + " bytes/s");
                        }
                        boolean isCrash = plugin.getConfigManager().isCrashBack();
                        plugin.getViolationManager().addLog(new ViolationLog(playerName, "PacketGuard", "Global IP Bandwidth limit exceeded: " + data.bytes + " bytes", isCrash));
                        plugin.getPunishmentService().punish(player, "&cConnection lost (Global Bandwidth limit exceeded)");
                        buf.release();
                        return;
                    }
                }
            }

            if (maxBandwidth > 0 && currentBandwidth > maxBandwidth) {
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

    private static class IpData {
        long bytes = 0;
        long lastCheck = System.currentTimeMillis();
    }
}
