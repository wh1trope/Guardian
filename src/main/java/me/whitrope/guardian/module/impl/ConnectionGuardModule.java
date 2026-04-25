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
 * Module for protecting against connection-based attacks and botting.
 */
package me.whitrope.guardian.module.impl;

import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.config.ConfigManager;
import me.whitrope.guardian.module.GuardianModule;
import me.whitrope.guardian.violation.ViolationLog;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ConnectionGuardModule extends GuardianModule implements Listener {

    private final Map<String, Long> lastConnectionTimes = new ConcurrentHashMap<>();
    private final Map<String, RateWindow> ipRateWindow = new ConcurrentHashMap<>();
    private final List<Pattern> blacklistedPatterns = new ArrayList<>();
    private long minDelayBetweenJoins;
    private int maxPreLoginsPerSecond;
    private int minUsernameLength;
    private int maxUsernameLength;
    private Pattern allowedUsernamePattern;
    private boolean checkJoinRateLimit;
    private boolean checkUsernameLength;
    private boolean checkUsernameCharacters;
    private boolean checkUsernameBlacklist;

    public ConnectionGuardModule(Guardian plugin) {
        super(plugin, "ConnectionGuard");
    }

    @Override
    protected void onEnable() {
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, getPlugin());

        Bukkit.getScheduler().runTaskTimerAsynchronously(getPlugin(), () -> {
            long now = System.currentTimeMillis();
            lastConnectionTimes.entrySet().removeIf(entry -> now - entry.getValue() > 30000L);
            ipRateWindow.entrySet().removeIf(entry -> now - entry.getValue().startTime > 3000L);
        }, 200L, 200L);
    }

    @Override
    public void reloadValues() {
        super.reloadValues();
        loadConfig();
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
    }

    private void loadConfig() {
        ConfigManager cfg = getConfigManager();

        minDelayBetweenJoins = cfg.getLimitLong("connection-guard.min-join-delay-ms", 1000L);
        maxPreLoginsPerSecond = cfg.getPreLoginIpPerSecond();

        minUsernameLength = cfg.getLimitConfig("connection-guard.username.min-length", 3);
        maxUsernameLength = cfg.getLimitConfig("connection-guard.username.max-length", 16);

        checkJoinRateLimit = cfg.isCheckEnabled("ConnectionGuard", "join-rate-limit");
        checkUsernameLength = cfg.isCheckEnabled("ConnectionGuard", "username-length");
        checkUsernameCharacters = cfg.isCheckEnabled("ConnectionGuard", "username-characters");
        checkUsernameBlacklist = cfg.isCheckEnabled("ConnectionGuard", "username-blacklist");

        String allowedPatternStr = cfg.getConfig().getString(
                "limits.connection-guard.username.allowed-pattern", "^[a-zA-Z0-9_]+$");
        try {
            allowedUsernamePattern = Pattern.compile(allowedPatternStr);
        } catch (PatternSyntaxException e) {
            getPlugin().getLogger().warning("[ConnectionGuard] Invalid allowed-pattern '"
                    + allowedPatternStr + "', falling back to default.");
            allowedUsernamePattern = Pattern.compile("^[a-zA-Z0-9_]+$");
        }

        blacklistedPatterns.clear();
        List<String> rawPatterns = cfg.getConfig().getStringList(
                "limits.connection-guard.username.blacklisted-patterns");
        for (String raw : rawPatterns) {
            try {
                blacklistedPatterns.add(Pattern.compile(raw, Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException e) {
                getPlugin().getLogger().warning("[ConnectionGuard] Invalid blacklist pattern '"
                        + raw + "', skipping.");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        String ip = event.getAddress().getHostAddress();
        long now = System.currentTimeMillis();

        if (checkJoinRateLimit && minDelayBetweenJoins > 0) {
            long lastJoin = lastConnectionTimes.getOrDefault(ip, 0L);
            if (now - lastJoin < minDelayBetweenJoins) {
                deny(event, "§bConnection throttled! Please wait before joining again.");
                return;
            }
        }

        if (checkJoinRateLimit && maxPreLoginsPerSecond > 0) {
            RateWindow window = ipRateWindow.computeIfAbsent(ip, k -> new RateWindow(now));

            if (now - window.startTime >= 1000L) {
                window.startTime = now;
                window.count.set(0);
            }

            if (window.count.incrementAndGet() > maxPreLoginsPerSecond) {
                deny(event, "§cToo many connections from your address.");
                return;
            }
        }

        lastConnectionTimes.put(ip, now);

        if (checkUsernameLength) {
            int len = name.length();
            if (len < minUsernameLength || len > maxUsernameLength) {
                deny(event, "§cInvalid username length! Allowed: "
                        + minUsernameLength + "–" + maxUsernameLength + " characters.");
                return;
            }
        }

        if (checkUsernameCharacters) {
            if (!allowedUsernamePattern.matcher(name).matches()) {
                deny(event, "§cYour username contains illegal characters!");
                return;
            }
        }

        if (checkUsernameBlacklist) {
            for (Pattern pattern : blacklistedPatterns) {
                if (pattern.matcher(name).matches()) {
                    deny(event, "§cYour username is not permitted on this server!");
                    return;
                }
            }
        }
    }

    private void deny(AsyncPlayerPreLoginEvent event, String reason) {
        getPlugin().getViolationManager().addLog(new ViolationLog(
                event.getName(), "ConnectionGuard", "IP: " + event.getAddress().getHostAddress() + " | " + reason.replace("§c", "").replace("§b", ""), false));
        event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                reason + "\n§bGuardian"
        );
    }

    private static class RateWindow {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long startTime;

        RateWindow(long startTime) {
            this.startTime = startTime;
        }
    }
}
