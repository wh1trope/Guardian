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


package me.whitrope.guardian.module.impl;

import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.module.GuardianModule;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

/**
 * Monitors server performance and mitigates packet-induced lag.
 */
public class LagControlModule extends GuardianModule implements Listener {

    private int redstoneCount;
    private int entitySpawnCount;

    private int maxRedstonePerTick;
    private int maxSpawnsPerTick;

    public LagControlModule(Guardian plugin) {
        super(plugin, "LagControl");
    }

    @Override
    protected void onEnable() {
        reloadValues();

        Bukkit.getPluginManager().registerEvents(this, getPlugin());

        Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            redstoneCount = 0;
            entitySpawnCount = 0;
        }, 1L, 1L);
    }

    @Override
    public void reloadValues() {
        super.reloadValues();
        maxRedstonePerTick = getConfigManager()
                .getLimitConfig("lag-control.max-piston-activations-per-tick", 300);
        maxSpawnsPerTick = getConfigManager()
                .getLimitConfig("lag-control.max-entity-spawns-per-tick", 100);
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (++redstoneCount > maxRedstonePerTick) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (++redstoneCount > maxRedstonePerTick) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntitySpawn(EntitySpawnEvent event) {
        switch (event.getEntityType()) {
            case DROPPED_ITEM:
            case EXPERIENCE_ORB:
            case AREA_EFFECT_CLOUD:
            case PRIMED_TNT:
            case FALLING_BLOCK:
                if (++entitySpawnCount > maxSpawnsPerTick) {
                    event.setCancelled(true);
                }
                break;
            default:
                break;
        }
    }
}
