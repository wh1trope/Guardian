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


package me.whitrope.guardian.command;

import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.gui.LogsGUI;
import me.whitrope.guardian.gui.SettingsGUI;
import me.whitrope.guardian.module.GuardianModule;
import me.whitrope.guardian.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Main command handler for the /guardian command, including subcommands and GUI access.
 */
public class GuardianCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "help", "reload", "debug", "status", "history", "logs", "settings", "about"
    );

    private final Guardian plugin;
    private final SettingsGUI settingsGUI;
    private final LogsGUI logsGUI;

    public GuardianCommand(Guardian plugin) {
        this.plugin = plugin;
        this.settingsGUI = new SettingsGUI(plugin);
        this.logsGUI = new LogsGUI(plugin);
    }

    public SettingsGUI getSettingsGUI() {
        return settingsGUI;
    }

    public LogsGUI getLogsGUI() {
        return logsGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!sender.hasPermission("guardian.admin")) {
            sendLogo(sender);
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendAdminHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.getConfigManager().reload();
                plugin.getModuleManager().getModules().forEach(GuardianModule::reloadValues);
                ChatUtil.sendMessage(sender, "&7Configuration has been successfully reloaded!");
                break;
            case "debug":
                boolean current = plugin.getConfigManager().isDebugMode();
                plugin.getConfigManager().getConfig().set("settings.debug-mode", !current);
                plugin.getConfigManager().save();
                plugin.getConfigManager().reload();
                ChatUtil.sendMessage(sender, "&7Debug mode is now&8: " + (!current ? "&aENABLED" : "&cDISABLED"));
                break;
            case "status":
                ChatUtil.sendMessage(sender, "&7System Status:");
                ChatUtil.sendMessage(sender, "&8&l「&b&l⚡&8&l」&8>> &7Version&8: &f" + plugin.getDescription().getVersion());
                ChatUtil.sendMessage(sender, "&8&l「&b&l⚡&8&l」&8>> &7Loaded Modules&8: &f" + plugin.getModuleManager().getModules().size());
                plugin.getModuleManager().getModules().forEach(m -> ChatUtil.sendMessage(sender, "   &8- &f" + m.getModuleName() + " &8(" + (m.isEnabled() ? "&aActive" : "&cDisabled") + "&8)"));
                break;
            case "history":
            case "logs":
                if (!(sender instanceof Player playerLogs)) {
                    ChatUtil.sendMessage(sender, "&8[&b&lGuardian&8] &7Only players can open the logs GUI.");
                    return true;
                }
                logsGUI.open(playerLogs, 0);
                break;
            case "settings":
                if (!(sender instanceof Player player)) {
                    ChatUtil.sendMessage(sender, "&8[&b&lGuardian&8] &7Only players can open the settings GUI.");
                    return true;
                }
                settingsGUI.open(player, 0);
                break;
            case "about":
                sendLogo(sender);
                break;
            default:
                sendAdminHelp(sender);
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command,
                                      @NonNull String alias, String @NonNull [] args) {
        if (!sender.hasPermission("guardian.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    out.add(sub);
                }
            }
            return out;
        }

        return Collections.emptyList();
    }

    private void sendLogo(CommandSender sender) {
        ChatUtil.sendMessage(sender, "");
        ChatUtil.sendMessage(sender, "  &b&lGUARDIAN &8&l| &7Protection System");
        ChatUtil.sendMessage(sender, "  &7This server is running &bGuardian v" + plugin.getDescription().getVersion() + "&7.");
        ChatUtil.sendMessage(sender, "");
    }

    private void sendAdminHelp(CommandSender sender) {
        ChatUtil.sendMessage(sender, "");
        ChatUtil.sendMessage(sender, "  &b&lGUARDIAN &8&l| &7Admin Menu");
        ChatUtil.sendMessage(sender, "&8&l「&b&l⚡&8&l」&8>> &b/guardian about &8- &7Shows the plugin version");
        ChatUtil.sendMessage(sender, "&8&l「&b&l⚡&8&l」&8>> &b/guardian reload &8- &7Reloads config.yml");
        ChatUtil.sendMessage(sender, "&8&l「&b&l⚡&8&l」&8>> &b/guardian debug &8- &7Enables/disables console logs");
        ChatUtil.sendMessage(sender, "&8&l「&b&l⚡&8&l」&8>> &b/guardian status &8- &7Shows the status of all modules");
        ChatUtil.sendMessage(sender, "&8&l「&b&l⚡&8&l」&8>> &b/guardian history &8- &7Shows recently blocked players");
        ChatUtil.sendMessage(sender, "&8&l「&b&l⚡&8&l」&8>> &b/guardian settings &8- &7Open the settings GUI");
        ChatUtil.sendMessage(sender, "");
    }
}
