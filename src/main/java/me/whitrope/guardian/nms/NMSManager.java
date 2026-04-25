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


package me.whitrope.guardian.nms;

import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.nms.versions.NmsAdapter_Fallback;
import me.whitrope.guardian.nms.versions.NmsAdapter_Modern;
import org.bukkit.Bukkit;

/**
 * Utility for detecting the server version and providing the appropriate NMS adapter.
 */
public class NMSManager {

    public static NMSProvider resolveAdapter(Guardian plugin) {
        String version = Bukkit.getServer().getClass().getPackage().getName();

        plugin.getLogger().info("Resolving NMS Adapter for version: " + version);

        try {

            if (version.equals("org.bukkit.craftbukkit") || version.contains("1_20") || version.contains("1_21")) {
                plugin.getLogger().info("Loaded Modern NMS Adapter (1.20+)");
                return new NmsAdapter_Modern(plugin);
            }

            plugin.getLogger().info("Loaded dynamically-mapped NMS Adapter (Legacy/Fallback)");
            return new NmsAdapter_Fallback(plugin);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize NMS Adapter!");
            e.printStackTrace();
            return new NmsAdapter_Fallback(plugin);
        }
    }
}
