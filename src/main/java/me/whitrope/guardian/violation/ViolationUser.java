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
 * Data object representing a player's violation history and current status.
 */
package me.whitrope.guardian.violation;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ViolationUser {
    private final Player owner;
    private final Map<String, Double> violations = new ConcurrentHashMap<>();
    private final Map<String, long[]> triggerWindows = new ConcurrentHashMap<>();
    private volatile long lastViolationTime = System.currentTimeMillis();
    private volatile boolean pendingKick = false;

    public ViolationUser(Player owner) {
        this.owner = owner;
    }

    public Player getOwner() {
        return owner;
    }

    public boolean isPendingKick() {
        return pendingKick;
    }

    public void setPendingKick(boolean pendingKick) {
        this.pendingKick = pendingKick;
    }

    public double getVl(String moduleName) {
        return violations.getOrDefault(moduleName, 0.0);
    }

    public void addVl(String moduleName, double amount) {
        violations.compute(moduleName, (k, v) -> v == null ? amount : v + amount);
        this.lastViolationTime = System.currentTimeMillis();
    }

    public void setVl(String moduleName, double amount) {
        violations.put(moduleName, amount);
        this.lastViolationTime = System.currentTimeMillis();
    }

    public void reduceVl(String moduleName, double amount) {
        violations.computeIfPresent(moduleName, (k, v) -> {
            double newVal = v - amount;
            return newVal > 0 ? newVal : null;
        });
    }

    public long getLastViolationTime() {
        return lastViolationTime;
    }

    public Map<String, Double> getAllViolations() {
        return violations;
    }

    public int incrementTriggerCount(String moduleName, long windowMs) {
        long now = System.currentTimeMillis();
        long[] entry = triggerWindows.computeIfAbsent(moduleName, k -> new long[]{0L, now});
        synchronized (entry) {
            if (now - entry[1] > windowMs) {
                entry[0] = 0L;
                entry[1] = now;
            }
            entry[0]++;
            return (int) entry[0];
        }
    }
}
