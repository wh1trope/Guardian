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
 * Manages plugin configuration, loading settings, and providing access to config values.
 */
package me.whitrope.guardian.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    private volatile boolean debugMode;
    private volatile boolean logExploits;
    private volatile boolean crashBack;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        load();
    }

    public void reload() {
        load();
    }

    private void load() {
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        validateConfig();
        refreshCachedFlags();
    }

    private void refreshCachedFlags() {
        this.debugMode = config.getBoolean("settings.debug-mode", false);
        this.logExploits = config.getBoolean("settings.log-exploits", true);
        this.crashBack = config.getBoolean("settings.crash-back", false);
    }

    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, e);
        }
    }

    private void validateConfig() {
        boolean updated = false;
        try (InputStream defConfigStream = plugin.getResource("config.yml")) {
            if (defConfigStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));

                for (String key : defConfig.getKeys(true)) {
                    if (!config.contains(key)) {
                        config.set(key, defConfig.get(key));

                        if (!defConfig.getComments(key).isEmpty()) {
                            config.setComments(key, defConfig.getComments(key));
                        }
                        if (!defConfig.getInlineComments(key).isEmpty()) {
                            config.setInlineComments(key, defConfig.getInlineComments(key));
                        }

                        updated = true;
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read bundled default config.yml", e);
        }

        if (updated) {
            plugin.getLogger().info("Found missing keys in config.yml. Updating configuration...");
            save();
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public boolean isModuleEnabled(String moduleName) {
        return config.getBoolean("modules." + moduleName + ".enabled", true);
    }

    public int getModuleMaxVl(String moduleName, int def) {
        return config.getInt("modules." + moduleName + ".max-vl", def);
    }

    public boolean isCheckEnabled(String moduleName, String checkName) {
        return config.getBoolean("modules." + moduleName + ".checks." + checkName, true);
    }

    public int getLimitConfig(String path, int def) {
        return config.getInt("limits." + path, def);
    }

    public long getLimitLong(String path, long def) {
        return config.getLong("limits." + path, def);
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public boolean isLogExploits() {
        return logExploits;
    }

    public boolean isCrashBack() {
        return crashBack;
    }

    public int getPreLoginIpPerSecond() {
        return config.getInt("limits.connection-guard.pre-login-ip-per-second", 5);
    }

}
