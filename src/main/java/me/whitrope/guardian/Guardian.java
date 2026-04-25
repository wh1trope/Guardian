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
 * Main plugin class responsible for initializing configuration, managers, modules, and listeners.
 */
package me.whitrope.guardian;

import me.whitrope.guardian.command.GuardianCommand;
import me.whitrope.guardian.config.ConfigManager;
import me.whitrope.guardian.listeners.PlayerConnectionListener;
import me.whitrope.guardian.module.ModuleManager;
import me.whitrope.guardian.network.PacketInjector;
import me.whitrope.guardian.nms.NMSManager;
import me.whitrope.guardian.nms.NMSProvider;
import me.whitrope.guardian.util.ReflectionUtil;
import me.whitrope.guardian.violation.PunishmentService;
import me.whitrope.guardian.violation.ViolationManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class Guardian extends JavaPlugin {

    private ConfigManager configManager;
    private PunishmentService punishmentService;
    private ViolationManager violationManager;
    private ModuleManager moduleManager;
    private PacketInjector packetInjector;
    private NMSProvider nmsProvider;

    @Override
    public void onEnable() {

        this.configManager = new ConfigManager(this);
        this.configManager.init();

        this.nmsProvider = NMSManager.resolveAdapter(this);
        this.punishmentService = new PunishmentService(this);
        this.violationManager = new ViolationManager(this);
        this.packetInjector = new PacketInjector(this);
        this.moduleManager = new ModuleManager(this);

        this.moduleManager.registerModules();
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        GuardianCommand guardianCommand = new GuardianCommand(this);
        Objects.requireNonNull(getCommand("guardian")).setExecutor(guardianCommand);
        Objects.requireNonNull(getCommand("guardian")).setTabCompleter(guardianCommand);
        Bukkit.getPluginManager().registerEvents(guardianCommand.getSettingsGUI(), this);
        Bukkit.getPluginManager().registerEvents(guardianCommand.getLogsGUI(), this);

        getLogger().info("Guardian Enabled!");
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            packetInjector.eject(player);
        }
        ReflectionUtil.clearCache();
        getLogger().info("Guardian Disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ViolationManager getViolationManager() {
        return violationManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public PacketInjector getPacketInjector() {
        return packetInjector;
    }

    public NMSProvider getNmsProvider() {
        return nmsProvider;
    }

    public PunishmentService getPunishmentService() {
        return punishmentService;
    }
}
