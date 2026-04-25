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


package me.whitrope.guardian.processor.impl;

import io.netty.channel.Channel;
import me.whitrope.guardian.module.GuardianModule;
import me.whitrope.guardian.processor.PacketProcessor;
import me.whitrope.guardian.util.ReflectionUtil;
import me.whitrope.guardian.util.UnsafeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Validates player movement packets to prevent movement-based exploits.
 */
public class MovementGuardProcessor implements PacketProcessor, Listener {

    private static final double WORLD_BORDER = 30_000_000.0D;
    private static final double INSANE_COORD = 1_000_000_000.0D;
    private static final float MAX_ROTATION = 100_000.0F;

    private final GuardianModule module;
    private final Map<Class<?>, PacketFieldMap> fieldCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, long[]> blockPosFieldsMap = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicReference<CachedLocation>> safeLocationCache = new ConcurrentHashMap<>();
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
            safeLocationCache.computeIfAbsent(event.getPlayer().getUniqueId(), k -> new AtomicReference<>(new CachedLocation(to))).set(new CachedLocation(to));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to != null) {
            safeLocationCache.computeIfAbsent(event.getPlayer().getUniqueId(), k -> new AtomicReference<>(new CachedLocation(to))).set(new CachedLocation(to));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        safeLocationCache.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
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
        boolean isVehicleMove = packetName.contains("Vehicle");
        if (requireVehicle && isVehicleMove && player.getVehicle() == null) {
            module.flag(player, "Exploit: VehicleMove without vehicle", 10.0);
            return false;
        }

        PacketFieldMap fMap = fieldCache.computeIfAbsent(packet.getClass(), this::mapFields);

        if (packetName.contains("InteractPacket") || packetName.contains("UseEntity")) {
            if (fMap.entityIdOffset != -1) {
                int entityId = UnsafeUtil.getInt(packet, fMap.entityIdOffset);
                if (entityId < 0 || entityId > 2000000) {
                    module.flag(player, "Exploit: Invalid Entity ID (" + entityId + ")", 10.0);
                    return false;
                }
            }
        }

        for (long offset : fMap.doubleOffsets) {
            double d = UnsafeUtil.getDouble(packet, offset);
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

        for (long offset : fMap.floatOffsets) {
            float fl = UnsafeUtil.getFloat(packet, offset);
            if (Float.isNaN(fl) || Float.isInfinite(fl)) {
                module.flag(player, "Exploit: Invalid Float Rotation", 10.0);
                return false;
            }
            if (Math.abs(fl) > MAX_ROTATION) {
                module.flag(player, "Exploit: Extreme rotation (" + fl + ")", 10.0);
                return false;
            }
        }

        AtomicReference<CachedLocation> cachedRef = safeLocationCache.get(player.getUniqueId());
        CachedLocation cachedLoc = cachedRef != null ? cachedRef.get() : null;

        for (long offset : fMap.blockPosOffsets) {
            Object val = UnsafeUtil.getObject(packet, offset);
            if (val == null) continue;

            long[] bpOffsets = blockPosFieldsMap.computeIfAbsent(val.getClass(), this::mapBlockPosFields);

            int bX = 0, bY = 0, bZ = 0;

            for (int i = 0; i < bpOffsets.length; i++) {
                int coord = UnsafeUtil.getInt(val, bpOffsets[i]);
                if (Math.abs(coord) > maxAbsoluteCoord) {
                    module.flag(player, "Exploit: Invalid BlockPos (" + coord + ")", 10.0);
                    return false;
                }
                if (i == 0) bX = coord;
                else if (i == 1) bY = coord;
                else if (i == 2) bZ = coord;
            }

            if (cachedLoc != null && maxInteractDistanceSq > 0 && bpOffsets.length >= 3) {
                double distSq = distanceSquared(cachedLoc.x, cachedLoc.y, cachedLoc.z, bX, bY, bZ);
                if (distSq > maxInteractDistanceSq) {
                    module.flag(player, "Exploit: Out of Range Interaction", 5.0);
                    return false;
                }
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
        List<Long> dList = new ArrayList<>();
        List<Long> fList = new ArrayList<>();
        List<Long> bList = new ArrayList<>();

        for (Field f : ReflectionUtil.getCachedFields(clazz)) {
            long offset = UnsafeUtil.objectFieldOffset(f);
            if (offset == -1) continue;

            Class<?> type = f.getType();
            String name = f.getName().toLowerCase();
            
            if (type == double.class) {
                if (name.length() <= 2 || name.contains("x") || name.contains("y") || name.contains("z")) {
                    dList.add(offset);
                }
            } else if (type == float.class) {
                if (name.length() <= 2 || name.contains("yaw") || name.contains("pitch") || name.contains("y") || name.contains("p") || name.contains("e") || name.contains("f")) {
                    fList.add(offset);
                }
            } else if (type.getSimpleName().equals("BlockPosition") || type.getSimpleName().equals("BlockPos")) {
                bList.add(offset);
            } else if (type == int.class && (name.contains("entity") || name.equals("a"))) {
                if (map.entityIdOffset == -1) {
                    map.entityIdOffset = offset;
                }
            }
        }

        map.doubleOffsets = dList.stream().mapToLong(l -> l).toArray();
        map.floatOffsets = fList.stream().mapToLong(l -> l).toArray();
        map.blockPosOffsets = bList.stream().mapToLong(l -> l).toArray();

        return map;
    }

    private long[] mapBlockPosFields(Class<?> clazz) {
        List<Long> offsets = new ArrayList<>();
        for (Field bf : ReflectionUtil.getAllCachedFields(clazz)) {
            if (Modifier.isStatic(bf.getModifiers())) continue;
            if (bf.getType() == int.class) {
                long offset = UnsafeUtil.objectFieldOffset(bf);
                if (offset != -1) {
                    offsets.add(offset);
                }
            }
        }
        return offsets.stream().mapToLong(l -> l).toArray();
    }

    private record CachedLocation(double x, double y, double z) {
        CachedLocation(Location loc) {
            this(loc.getX(), loc.getY(), loc.getZ());
        }
    }

    private static class PacketFieldMap {
        long[] doubleOffsets = new long[0];
        long[] floatOffsets = new long[0];
        long[] blockPosOffsets = new long[0];
        long entityIdOffset = -1;
    }
}
