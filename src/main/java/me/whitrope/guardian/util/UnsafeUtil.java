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
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.logging.Level;

/**
 * This class is used to get the offset of a field in an object.
 */
public final class UnsafeUtil {

    private static Unsafe unsafe;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Guardian] Failed to initialize Unsafe", e);
        }
    }

    private UnsafeUtil() {}

    public static Unsafe getUnsafe() {
        return unsafe;
    }

    public static long objectFieldOffset(Field field) {
        if (unsafe == null) return -1;
        return unsafe.objectFieldOffset(field);
    }

    public static double getDouble(Object o, long offset) {
        return unsafe.getDouble(o, offset);
    }

    public static float getFloat(Object o, long offset) {
        return unsafe.getFloat(o, offset);
    }

    public static int getInt(Object o, long offset) {
        return unsafe.getInt(o, offset);
    }

    public static byte getByte(Object o, long offset) {
        return unsafe.getByte(o, offset);
    }

    public static Object getObject(Object o, long offset) {
        return unsafe.getObject(o, offset);
    }

    public static short getShort(Object o, long offset) {
        return unsafe.getShort(o, offset);
    }
}
