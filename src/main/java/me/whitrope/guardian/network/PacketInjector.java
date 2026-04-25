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
 * Handles the injection of custom channel handlers into player network connections.
 */
package me.whitrope.guardian.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import me.whitrope.guardian.Guardian;
import org.bukkit.entity.Player;

import java.util.logging.Level;

public class PacketInjector {

    private static final String HANDLER_NAME = "guardian_anticrash";
    private static final String CAPACITY_HANDLER_NAME = "guardian_capacity_filter";

    private final Guardian plugin;

    public PacketInjector(Guardian plugin) {
        this.plugin = plugin;
    }

    public void inject(Player player) {
        if (plugin.getNmsProvider() == null) {
            plugin.getLogger().severe("NMSProvider is null, cannot inject custom packet handler for " + player.getName());
            return;
        }

        final Channel channel;
        try {
            channel = plugin.getNmsProvider().getChannel(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to obtain Netty channel for " + player.getName(), e);
            return;
        }
        if (channel == null) {
            plugin.getLogger().severe("FAILED to fetch Netty channel for " + player.getName() + " inside PacketInjector!");
            return;
        }

        Runnable task = () -> {
            try {
                ChannelPipeline pipeline = channel.pipeline();

                if (pipeline.get(HANDLER_NAME) != null) {
                    pipeline.remove(HANDLER_NAME);
                }
                if (pipeline.get(CAPACITY_HANDLER_NAME) != null) {
                    pipeline.remove(CAPACITY_HANDLER_NAME);
                }

                pipeline.addBefore("packet_handler", HANDLER_NAME, new ChannelHandler(plugin, player));

                if (pipeline.get("via-decoder") != null) {
                    pipeline.addBefore("via-decoder", CAPACITY_HANDLER_NAME, new CapacityHandler(plugin, player));
                } else if (pipeline.get("decompress") != null) {
                    pipeline.addAfter("decompress", CAPACITY_HANDLER_NAME, new CapacityHandler(plugin, player));
                } else if (pipeline.get("decoder") != null) {
                    pipeline.addBefore("decoder", CAPACITY_HANDLER_NAME, new CapacityHandler(plugin, player));
                } else {
                    pipeline.addFirst(CAPACITY_HANDLER_NAME, new CapacityHandler(plugin, player));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to inject packet listener for " + player.getName(), e);
            }
        };

        if (channel.eventLoop().inEventLoop()) {
            task.run();
        } else {
            channel.eventLoop().execute(task);
        }
    }

    public void eject(Player player) {
        if (plugin.getNmsProvider() == null) return;

        final Channel channel;
        try {
            channel = plugin.getNmsProvider().getChannel(player);
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().warning("Failed to obtain Netty channel for " + player.getName() + ": " + e.getMessage());
            }
            return;
        }
        if (channel == null) return;

        Runnable task = () -> {
            try {
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline.get(HANDLER_NAME) != null) {
                    pipeline.remove(HANDLER_NAME);
                }
                if (pipeline.get(CAPACITY_HANDLER_NAME) != null) {
                    pipeline.remove(CAPACITY_HANDLER_NAME);
                }
            } catch (Exception e) {
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().warning("Failed to eject packet listener for " + player.getName() + ": " + e.getMessage());
                }
            }
        };

        if (channel.eventLoop().inEventLoop()) {
            task.run();
        } else {
            channel.eventLoop().execute(task);
        }
    }
}
