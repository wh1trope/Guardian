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

import io.netty.util.AttributeKey;
import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.module.GuardianModule;
import me.whitrope.guardian.util.AttributeUtil;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides low-level packet filtering and validation.
 */
public class PacketGuardModule extends GuardianModule {

    private static final int BUCKETS = 10;
    private static final long BUCKET_MS = 100L;
    private static final AttributeKey<PacketData> PACKET_DATA_KEY = AttributeKey.valueOf("packet_guard_data");

    private static final Set<Class<?>> FLYING_CLASSES = ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> ANIMATION_CLASSES = ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> CLASSIFIED = ConcurrentHashMap.newKeySet();

    private volatile int maxGlobalPps;
    private volatile int maxFlyingPps;
    private volatile int maxAnimationPps;

    public PacketGuardModule(Guardian plugin) {
        super(plugin, "PacketGuard");
    }

    private static void classifyAndCache(Class<?> cls, String packetName) {
        if (packetName.contains("Flying") || packetName.contains("Position")
                || packetName.contains("Look") || packetName.contains("MovePlayer")
                || packetName.contains("MoveVehicle")) {
            FLYING_CLASSES.add(cls);
        } else if (packetName.contains("ArmAnimation") || packetName.contains("Swing")) {
            ANIMATION_CLASSES.add(cls);
        }
        CLASSIFIED.add(cls);
    }

    @Override
    protected void onEnable() {
        reloadValues();

        addGlobalProcessor((packet, player, packetName, channel) -> {
            PacketData data = AttributeUtil.getOrCreate(channel, PACKET_DATA_KEY, PacketData::new);

            long now = System.currentTimeMillis();

            int total = data.addAndSumGlobal(now);
            if (maxGlobalPps > 0 && total > maxGlobalPps) {
                flag(player, "Global Packet Flood (" + total + " pps)", 10.0);
                return false;
            }

            Class<?> cls = packet.getClass();
            if (!CLASSIFIED.contains(cls)) {
                classifyAndCache(cls, packetName);
            }

            if (FLYING_CLASSES.contains(cls)) {
                int flying = data.addAndSumFlying(now);
                if (maxFlyingPps > 0 && flying > maxFlyingPps) {
                    flag(player, "Movement Flood (" + flying + " pps)", 10.0);
                    return false;
                }
            } else if (ANIMATION_CLASSES.contains(cls)) {
                int anim = data.addAndSumAnimation(now);
                if (maxAnimationPps > 0 && anim > maxAnimationPps) {
                    flag(player, "Animation Flood (" + anim + " pps)", 5.0);
                    return false;
                }
            }

            return true;
        });
    }

    @Override
    public void reloadValues() {
        super.reloadValues();
        maxGlobalPps = getConfigManager().getLimitConfig("packet-guard.global-pps", 800);
        maxFlyingPps = getConfigManager().getLimitConfig("packet-guard.flying-pps", 100);
        maxAnimationPps = getConfigManager().getLimitConfig("packet-guard.animation-pps", 60);
    }

    private static final class PacketData {
        private final int[] globalBuckets = new int[BUCKETS];
        private final int[] flyingBuckets = new int[BUCKETS];
        private final int[] animationBuckets = new int[BUCKETS];
        private final long[] bucketTimes = new long[BUCKETS];

        int addAndSumGlobal(long now) {
            return addAndSum(globalBuckets, now);
        }

        int addAndSumFlying(long now) {
            return addAndSum(flyingBuckets, now);
        }

        int addAndSumAnimation(long now) {
            return addAndSum(animationBuckets, now);
        }

        private int addAndSum(int[] array, long now) {
            long bucketId = now / BUCKET_MS;
            int idx = (int) (bucketId % BUCKETS);
            if (bucketTimes[idx] != bucketId) {
                bucketTimes[idx] = bucketId;
                globalBuckets[idx] = 0;
                flyingBuckets[idx] = 0;
                animationBuckets[idx] = 0;
            }
            array[idx]++;
            int sum = 0;
            long minId = bucketId - (BUCKETS - 1);
            for (int i = 0; i < BUCKETS; i++) {
                if (bucketTimes[i] >= minId) {
                    sum += array[i];
                }
            }
            return sum;
        }
    }
}
