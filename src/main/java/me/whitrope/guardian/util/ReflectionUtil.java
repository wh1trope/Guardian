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

package me.whitrope.guardian.util;

import org.bukkit.Bukkit;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Provides reflection-based access to NMS and CraftBukkit classes.
 */
public final class ReflectionUtil {

    private static final Field[] EMPTY = new Field[0];

    private static final Map<Class<?>, Field[]> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field[]> DEEP_FIELD_CACHE = new ConcurrentHashMap<>();

    private static final Map<Field, MethodHandle> GETTER_CACHE = new ConcurrentHashMap<>();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private ReflectionUtil() {
    }

    public static Field[] getCachedFields(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, k -> {
            Field[] fields = k.getDeclaredFields();
            for (Field f : fields) {
                f.setAccessible(true);
            }
            return fields;
        });
    }

    public static Field[] getAllCachedFields(Class<?> clazz) {
        return DEEP_FIELD_CACHE.computeIfAbsent(clazz, k -> {
            List<Field> collected = new ArrayList<>();
            Class<?> cur = k;
            while (cur != null && cur != Object.class) {
                Collections.addAll(collected, getCachedFields(cur));
                cur = cur.getSuperclass();
            }
            return collected.isEmpty() ? EMPTY : collected.toArray(new Field[0]);
        });
    }

    public static MethodHandle getGetter(Field field) {
        return GETTER_CACHE.computeIfAbsent(field, f -> {
            try {
                if (!f.isAccessible()) {
                    f.setAccessible(true);
                }
                return LOOKUP.unreflectGetter(f);
            } catch (IllegalAccessException e) {
                Bukkit.getLogger().log(Level.SEVERE, "Failed to unreflect getter for field " + f.getName(), e);
                return null;
            }
        });
    }

    public static void clearCache() {
        FIELD_CACHE.clear();
        DEEP_FIELD_CACHE.clear();
        GETTER_CACHE.clear();
    }
}
