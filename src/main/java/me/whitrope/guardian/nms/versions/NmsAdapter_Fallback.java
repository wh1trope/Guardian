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
 * Fallback NMS implementation for unsupported or older server versions.
 */
package me.whitrope.guardian.nms.versions;

import io.netty.channel.Channel;
import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.nms.NMSProvider;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class NmsAdapter_Fallback implements NMSProvider {
    private final Guardian plugin;

    private Method getHandleMethod;
    private Field playerConnectionField;
    private Field networkManagerField;
    private Field channelField;

    public NmsAdapter_Fallback(Guardian plugin) {
        this.plugin = plugin;
        try {
            setupReflection();
        } catch (Exception e) {
            plugin.getLogger().severe("Fallback NMS Adapter failed to map fields!");
            e.printStackTrace();
        }
    }

    private void setupReflection() throws Exception {
        String serverPackage = plugin.getServer().getClass().getPackage().getName();
        String[] parts = serverPackage.split("\\.");
        Class<?> craftPlayerClass;

        if (parts.length >= 4 && parts[3].startsWith("v")) {
            craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + parts[3] + ".entity.CraftPlayer");
        } else {
            craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
        }

        this.getHandleMethod = craftPlayerClass.getDeclaredMethod("getHandle");
        Class<?> entityPlayerClass = getHandleMethod.getReturnType();

        this.playerConnectionField = findFieldByNestedType(entityPlayerClass, 3);
        if (this.playerConnectionField == null) {
            throw new NoSuchFieldException("Could not find playerConnection in " + entityPlayerClass.getName());
        }
        this.playerConnectionField.setAccessible(true);

        Class<?> playerConnectionClass = this.playerConnectionField.getType();

        this.networkManagerField = findFieldByNestedType(playerConnectionClass, 2);
        if (this.networkManagerField == null) {
            throw new NoSuchFieldException("Could not find networkManager in " + playerConnectionClass.getName());
        }
        this.networkManagerField.setAccessible(true);

        Class<?> networkManagerClass = this.networkManagerField.getType();

        for (Field f : networkManagerClass.getDeclaredFields()) {
            if (Channel.class.isAssignableFrom(f.getType())) {
                this.channelField = f;
                this.channelField.setAccessible(true);
                break;
            }
        }
        if (this.channelField == null) {
            throw new NoSuchFieldException("Could not find Channel in " + networkManagerClass.getName());
        }
    }

    private Field findFieldByNestedType(Class<?> searchClass, int depth) {
        if (depth <= 0) return null;
        Class<?> current = searchClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                if (Channel.class.isAssignableFrom(fieldType)) {
                    return field;
                }
                if (!fieldType.isPrimitive() && !fieldType.getName().startsWith("java.")) {
                    if (findFieldByNestedTypeInternal(fieldType, Channel.class, depth - 1)) {
                        return field;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private boolean findFieldByNestedTypeInternal(Class<?> fieldType, Class<?> targetType, int depth) {
        if (targetType.isAssignableFrom(fieldType)) return true;
        if (depth <= 0) return false;
        Class<?> current = fieldType;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                Class<?> innerType = field.getType();
                if (targetType.isAssignableFrom(innerType)) return true;
                if (!innerType.isPrimitive() && !innerType.getName().startsWith("java.")) {
                    if (findFieldByNestedTypeInternal(innerType, targetType, depth - 1)) return true;
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    @Override
    public Channel getChannel(Player player) {
        try {
            Object entityPlayer = getHandleMethod.invoke(player);
            Object playerConnection = playerConnectionField.get(entityPlayer);
            if (playerConnection == null) return null;
            Object networkManager = networkManagerField.get(playerConnection);
            if (networkManager == null) return null;
            return (Channel) channelField.get(networkManager);
        } catch (Exception e) {
            return null;
        }
    }
}
