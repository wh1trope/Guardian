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

package me.whitrope.guardian.module;

import io.netty.channel.Channel;
import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.config.ConfigManager;
import me.whitrope.guardian.processor.OutgoingPacketProcessor;
import me.whitrope.guardian.processor.PacketProcessor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for all plugin modules, defining standard enable/disable behavior.
 */
public abstract class GuardianModule {

    private static final List<PacketProcessor> NONE = Collections.emptyList();

    protected final String moduleName;
    protected final List<PacketProcessor> globalIncomingProcessors = new ArrayList<>();
    protected final Map<String, List<PacketProcessor>> specificIncomingProcessors = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<PacketProcessor>> processorsByClass = new ConcurrentHashMap<>();
    private final Guardian plugin;
    private final boolean hasOutgoingHandler;
    private boolean enabled;

    public GuardianModule(Guardian plugin, String moduleName) {
        this.plugin = plugin;
        this.moduleName = moduleName;
        this.hasOutgoingHandler = this instanceof OutgoingPacketProcessor;
    }

    public void initialize() {
        this.enabled = getConfigManager().isModuleEnabled(moduleName);
        if (this.enabled) {
            onEnable();
        }
    }

    protected void onEnable() {
    }

    public void reloadValues() {
        for (PacketProcessor p : globalIncomingProcessors) {
            p.reloadValues();
        }
        for (List<PacketProcessor> list : specificIncomingProcessors.values()) {
            for (PacketProcessor p : list) {
                p.reloadValues();
            }
        }
    }

    protected void onDisable() {
    }

    protected void addGlobalProcessor(PacketProcessor processor) {
        globalIncomingProcessors.add(processor);
        processorsByClass.clear();
    }

    protected void addSpecificProcessor(String packetName, PacketProcessor processor) {
        specificIncomingProcessors.computeIfAbsent(packetName, k -> new ArrayList<>()).add(processor);
        processorsByClass.clear();
    }

    public void flag(Player player, String reason, double vl) {
        plugin.getViolationManager().handleViolation(player, moduleName, reason, vl);
    }

    public String getModuleName() {
        return moduleName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) onEnable();
        else onDisable();

        if (plugin.getModuleManager() != null) {
            plugin.getModuleManager().rebuildEnabledCaches();
        }
    }

    public Guardian getPlugin() {
        return plugin;
    }

    public ConfigManager getConfigManager() {
        return plugin.getConfigManager();
    }

    public boolean hasOutgoingHandler() {
        return hasOutgoingHandler;
    }

    public boolean onPacketReceive(Object packet, Player player, Channel channel) {
        if (packet == null) return true;
        Class<?> cls = packet.getClass();

        List<PacketProcessor> specifics = processorsByClass.get(cls);
        String packetName = null;
        if (specifics == null) {
            packetName = cls.getSimpleName();
            List<PacketProcessor> registered = specificIncomingProcessors.get(packetName);
            specifics = registered != null ? registered : NONE;
            processorsByClass.put(cls, specifics);
        }

        if (!globalIncomingProcessors.isEmpty()) {
            if (packetName == null) packetName = cls.getSimpleName();
            for (PacketProcessor p : globalIncomingProcessors) {
                if (!p.process(packet, player, packetName, channel)) return false;
            }
        }

        if (specifics != NONE) {
            if (packetName == null) packetName = cls.getSimpleName();
            for (PacketProcessor p : specifics) {
                if (!p.process(packet, player, packetName, channel)) return false;
            }
        }
        return true;
    }
}
