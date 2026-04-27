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

import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the tracking and storage of player violations.
 */
public class ViolationManager {

    private static final int HISTORY_LIMIT = 50;
    private static final long ALERT_COOLDOWN_MS = 500L;

    private final Map<UUID, ViolationUser> activeUsers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAlertTime = new ConcurrentHashMap<>();
    private final ArrayDeque<ViolationLog> violationHistory = new ArrayDeque<>();
    private final Object historyLock = new Object();
    private final Guardian plugin;

    public ViolationManager(Guardian plugin) {
        this.plugin = plugin;
        startDecayTask();
    }

    public ViolationUser getUser(Player player) {
        ViolationUser user = activeUsers.get(player.getUniqueId());
        if (user != null && user.getOwner() == player) {
            return user;
        }
        return activeUsers.compute(player.getUniqueId(), (uuid, existing) -> {
            if (existing != null && existing.getOwner() == player) return existing;
            return new ViolationUser(player);
        });
    }

    public void removeUser(Player player) {
        ViolationUser user = activeUsers.get(player.getUniqueId());
        if (user != null && user.getOwner() == player) {
            activeUsers.remove(player.getUniqueId());
        }
        lastAlertTime.remove(player.getUniqueId());
    }

    public void addLog(ViolationLog log) {
        synchronized (historyLock) {
            violationHistory.addFirst(log);
            while (violationHistory.size() > HISTORY_LIMIT) {
                violationHistory.pollLast();
            }
        }
    }

    public List<ViolationLog> getHistory() {
        synchronized (historyLock) {
            return new ArrayList<>(violationHistory);
        }
    }

    public void handleViolation(Player player, String moduleName, String detail, double vlToAdd) {
        ViolationUser user = getUser(player);
        user.addVl(moduleName, vlToAdd);
        double vl = user.getVl(moduleName);

        int maxVl = plugin.getConfigManager().getModuleMaxVl(moduleName, 50);

        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().warning(player.getName() + " flagged " + moduleName + " (" + detail + ") VL: " + vl + "/" + maxVl);

            long alertNow = System.currentTimeMillis();
            Long lastAlert = lastAlertTime.get(player.getUniqueId());
            if (lastAlert == null || alertNow - lastAlert >= ALERT_COOLDOWN_MS) {
                lastAlertTime.put(player.getUniqueId(), alertNow);
                String alert = "§8[§b§l⚡§8] §bGuardian §8>> §f" + player.getName() + " §7flagged §f" + moduleName + " §8(§f" + detail + "§8) §bx" + String.format("%.1f", vl);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.hasPermission("guardian.alerts")) {
                            ChatUtil.sendMessage(p, alert);
                        }
                    }
                });
            }
        }

        if (vl >= maxVl) {
            if (user.isPendingKick()) return;

            user.setPendingKick(true);

            int triggers = user.incrementTriggerCount(moduleName, getLadderWindowMs());
            boolean crashBack = plugin.getConfigManager().isCrashBack();
            int crashAfter = plugin.getConfigManager().getConfig()
                    .getInt("settings.punishment-ladder.crash-after-kicks", 0);
            if (!crashBack && crashAfter > 0 && triggers > crashAfter) {
                crashBack = true;
            }

            addLog(new ViolationLog(player.getName(), player.getUniqueId(), moduleName, detail, crashBack));
            if (plugin.getConfigManager().isLogExploits()) {
                String logTag = crashBack ? "(CRASH)" : "[KICK]";
                plugin.getLogger().info(logTag + " " + player.getName() + " - " + moduleName + " (" + detail + ")");
            }

            user.setVl(moduleName, 0);
            plugin.getPunishmentService().executePunishment(player, crashBack, "&cDetected suspicious packets");
        }
    }

    private long getLadderWindowMs() {
        return plugin.getConfigManager().getConfig()
                .getLong("settings.punishment-ladder.window-ms", 300_000L);
    }

    private void startDecayTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            for (ViolationUser user : activeUsers.values()) {
                if (now - user.getLastViolationTime() > 5000L) {
                    for (Map.Entry<String, Double> entry : user.getAllViolations().entrySet()) {
                        if (entry.getValue() > 0) {
                            user.reduceVl(entry.getKey(), 1.0);
                        }
                    }
                }
            }
        }, 20L, 20L);
    }
}
