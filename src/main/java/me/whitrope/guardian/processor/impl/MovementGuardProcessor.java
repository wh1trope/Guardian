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
 * Validates player movement packets to prevent movement-based exploits.
 */
package me.whitrope.guardian.processor.impl;

import io.netty.channel.Channel;
import me.whitrope.guardian.module.GuardianModule;
import me.whitrope.guardian.processor.PacketProcessor;
import me.whitrope.guardian.util.ReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MovementGuardProcessor implements PacketProcessor, Listener {

    private static final double WORLD_BORDER = 30_000_000.0D;
    private static final double INSANE_COORD = 1_000_000_000.0D;

    private final GuardianModule module;
    private final Map<Class<?>, PacketFieldMap> fieldCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Field>> blockPosFieldsMap = new ConcurrentHashMap<>();
    private final Map<UUID, double[]> safeLocationCache = new ConcurrentHashMap<>();
    private double maxAbsoluteCoord;
    private double maxInteractDistanceSq;
    private boolean requireVehicle;

    public MovementGuardProcessor(GuardianModule module) {
        this.module = module;

        Bukkit.getPluginManager().registerEvents(this, module.getPlugin());

        reloadValues();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to != null) {
            safeLocationCache.put(event.getPlayer().getUniqueId(), new double[]{to.getX(), to.getY(), to.getZ()});
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to != null) {
            safeLocationCache.put(event.getPlayer().getUniqueId(), new double[]{to.getX(), to.getY(), to.getZ()});
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        safeLocationCache.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void reloadValues() {
        int cfg = module.getConfigManager().getLimitConfig("crash-shield.movement.max-absolute-coord", 30_000_000);
        this.maxAbsoluteCoord = cfg > 0 ? cfg : WORLD_BORDER;
        this.maxInteractDistanceSq = module.getConfigManager().getLimitConfig("crash-shield.movement.max-interact-distance", 225);
        this.requireVehicle = module.getConfigManager().getConfig()
                .getBoolean("limits.crash-shield.movement.require-vehicle", true);
        this.fieldCache.clear();
        this.blockPosFieldsMap.clear();
    }

    @Override
    public boolean process(Object packet, Player player, String packetName, Channel channel) {
        try {
            boolean isVehicleMove = packetName.contains("Vehicle");
            if (requireVehicle && isVehicleMove && player.getVehicle() == null) {
                module.flag(player, "Exploit: VehicleMove without vehicle", 10.0);
                return false;
            }

            PacketFieldMap fMap = fieldCache.computeIfAbsent(packet.getClass(), this::mapFields);

            for (MethodHandle mh : fMap.doubleFields) {
                Double d = (Double) mh.invoke(packet);
                if (d == null) continue;
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    module.flag(player, "Exploit: Invalid Double Position", 10.0);
                    return false;
                }
                if (Math.abs(d) > INSANE_COORD) {
                    module.flag(player, "Exploit: Insane coordinate (" + d + ")", 10.0);
                    return false;
                }
                if (Math.abs(d) > maxAbsoluteCoord) {
                    module.flag(player, "Exploit: Beyond world border (" + d + ")", 10.0);
                    return false;
                }
            }

            for (MethodHandle mh : fMap.floatFields) {
                Float fl = (Float) mh.invoke(packet);
                if (fl == null) continue;
                if (Float.isNaN(fl) || Float.isInfinite(fl)) {
                    module.flag(player, "Exploit: Invalid Float Rotation", 10.0);
                    return false;
                }
                if (Math.abs(fl) > 100_000.0F) {
                    module.flag(player, "Exploit: Extreme rotation (" + fl + ")", 10.0);
                    return false;
                }
            }

            double[] cachedLoc = safeLocationCache.get(player.getUniqueId());

            for (MethodHandle mh : fMap.blockPosFields) {
                Object val = mh.invoke(packet);
                if (val == null) continue;

                List<Field> bpFields = blockPosFieldsMap.computeIfAbsent(val.getClass(), this::mapBlockPosFields);
                int[] xyz = new int[3];
                int idx = 0;

                for (Field bf : bpFields) {
                    MethodHandle bmh = ReflectionUtil.getGetter(bf);
                    if (bmh == null) continue;
                    int coord = (int) bmh.invoke(val);
                    if (Math.abs(coord) > maxAbsoluteCoord) {
                        module.flag(player, "Exploit: Invalid BlockPos (" + coord + ")", 10.0);
                        return false;
                    }
                    if (idx < 3) xyz[idx++] = coord;
                }

                if (cachedLoc != null && maxInteractDistanceSq > 0) {
                    double distSq = distanceSquared(cachedLoc[0], cachedLoc[1], cachedLoc[2], xyz[0], xyz[1], xyz[2]);
                    if (distSq > maxInteractDistanceSq) {

                        module.flag(player, "Exploit: Out of Range Interaction", 5.0);
                        return false;
                    }
                }
            }

        } catch (Throwable e) {
            if (module.getConfigManager().isDebugMode()) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private double distanceSquared(double x1, double y1, double z1, int x2, int y2, int z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    private PacketFieldMap mapFields(Class<?> clazz) {
        PacketFieldMap map = new PacketFieldMap();
        for (Field f : ReflectionUtil.getCachedFields(clazz)) {
            MethodHandle mh = ReflectionUtil.getGetter(f);
            if (mh == null) continue;

            Class<?> type = f.getType();
            if (type == double.class || type == Double.class) {
                map.doubleFields.add(mh);
            } else if (type == float.class || type == Float.class) {
                map.floatFields.add(mh);
            } else if (type.getSimpleName().equals("BlockPosition") || type.getSimpleName().equals("BlockPos")) {
                map.blockPosFields.add(mh);
            }
        }
        return map;
    }

    private List<Field> mapBlockPosFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Field bf : ReflectionUtil.getAllCachedFields(clazz)) {
            if (Modifier.isStatic(bf.getModifiers())) continue;
            if (bf.getType() == int.class || bf.getType() == Integer.class) {
                fields.add(bf);
            }
        }
        return fields;
    }

    private static class PacketFieldMap {
        final List<MethodHandle> doubleFields = new ArrayList<>();
        final List<MethodHandle> floatFields = new ArrayList<>();
        final List<MethodHandle> blockPosFields = new ArrayList<>();
    }
}
