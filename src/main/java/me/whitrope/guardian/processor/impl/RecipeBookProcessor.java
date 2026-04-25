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
import io.netty.util.AttributeKey;
import me.whitrope.guardian.module.GuardianModule;
import me.whitrope.guardian.processor.PacketProcessor;
import me.whitrope.guardian.util.AttributeUtil;
import me.whitrope.guardian.util.ReflectionUtil;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates recipe book interactions to prevent related exploits.
 */
public class RecipeBookProcessor implements PacketProcessor {

    private static final AttributeKey<Data> DATA_KEY = AttributeKey.valueOf("guardian_recipebook_data");
    private static final Map<Class<?>, List<MethodHandle>> TARGET_GETTERS = new ConcurrentHashMap<>();
    private final GuardianModule module;
    private volatile int maxPerSecond;
    private volatile int maxResourceLocationLength;

    public RecipeBookProcessor(GuardianModule module) {
        this.module = module;
        reloadValues();
    }

    @Override
    public void reloadValues() {
        this.maxPerSecond = module.getConfigManager().getLimitConfig("crash-shield.recipe-book.per-second", 10);
        this.maxResourceLocationLength = module.getConfigManager().getLimitConfig("crash-shield.recipe-book.max-resource-location-length", 256);
    }

    private List<MethodHandle> getTargetGetters(Class<?> clazz) {
        return TARGET_GETTERS.computeIfAbsent(clazz, k -> {
            List<MethodHandle> handles = new ArrayList<>();
            for (Field f : ReflectionUtil.getCachedFields(k)) {
                Class<?> fType = f.getType();
                if (fType.equals(String.class) ||
                        fType.getSimpleName().contains("ResourceLocation") ||
                        fType.getSimpleName().contains("MinecraftKey")) {
                    MethodHandle mh = ReflectionUtil.getGetter(f);
                    if (mh != null) handles.add(mh);
                }
            }
            return handles;
        });
    }

    @Override
    public boolean process(Object packet, Player player, String packetName, Channel channel) {
        try {

            Data data = AttributeUtil.getOrCreate(channel, DATA_KEY, Data::new);
            long now = System.currentTimeMillis();
            if (now - data.secondStart >= 1000L) {
                data.secondStart = now;
                data.perSecond = 0;
            }
            if (maxPerSecond > 0 && ++data.perSecond > maxPerSecond) {
                module.flag(player, "Flood: RecipeBook (" + data.perSecond + "/s)", 5.0);
                return false;
            }

            for (MethodHandle mh : getTargetGetters(packet.getClass())) {
                Object val = mh.invoke(packet);
                if (val instanceof String str) {
                    if (str.length() > maxResourceLocationLength) {
                        module.flag(player, "Exploit: Oversized RecipeBook ResourceLocation", 5.0);
                        return false;
                    }
                } else if (val != null) {
                    String rlStr = val.toString();
                    if (rlStr.length() > maxResourceLocationLength) {
                        module.flag(player, "Exploit: Oversized RecipeBook ResourceLocation", 5.0);
                        return false;
                    }
                }
            }
        } catch (Throwable e) {
            if (module.getConfigManager().isDebugMode()) e.printStackTrace();
        }
        return true;
    }

    private static final class Data {
        int perSecond;
        long secondStart = System.currentTimeMillis();
    }
}
