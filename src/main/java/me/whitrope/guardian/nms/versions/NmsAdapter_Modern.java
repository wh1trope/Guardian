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


package me.whitrope.guardian.nms.versions;

import io.netty.channel.Channel;
import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.nms.NMSProvider;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * NMS implementation for modern Minecraft versions.
 */
public class NmsAdapter_Modern implements NMSProvider {
    private final Guardian plugin;

    private Method getHandleMethod;
    private Field playerConnectionField;
    private Field networkManagerField;
    private Field channelField;

    public NmsAdapter_Modern(Guardian plugin) {
        this.plugin = plugin;
        try {
            setupReflection();
        } catch (Exception e) {
            plugin.getLogger().severe("Modern NMS Adapter failed to map fields!");
            e.printStackTrace();
        }
    }

    private void setupReflection() throws Exception {
        String cbPackage = this.plugin.getServer().getClass().getPackage().getName();
        Class<?> craftPlayerClass = Class.forName(cbPackage + ".entity.CraftPlayer");
        this.getHandleMethod = craftPlayerClass.getDeclaredMethod("getHandle");

        Class<?> serverPlayerClass = this.getHandleMethod.getReturnType();

        for (Field f : serverPlayerClass.getDeclaredFields()) {
            if (f.getType().getSimpleName().equals("ServerGamePacketListenerImpl") ||
                    f.getType().getSimpleName().equals("PlayerConnection")) {
                this.playerConnectionField = f;
                this.playerConnectionField.setAccessible(true);
                break;
            }
        }

        if (this.playerConnectionField == null) {
            throw new NoSuchFieldException("Could not find ServerGamePacketListenerImpl field in ServerPlayer");
        }

        Class<?> connectionClass = this.playerConnectionField.getType();

        for (Field f : connectionClass.getDeclaredFields()) {
            if (f.getType().getSimpleName().equals("Connection") ||
                    f.getType().getSimpleName().equals("NetworkManager")) {
                this.networkManagerField = f;
                this.networkManagerField.setAccessible(true);
                break;
            }
        }

        if (this.networkManagerField == null) {
            for (Field f : connectionClass.getSuperclass().getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("Connection") ||
                        f.getType().getSimpleName().equals("NetworkManager")) {
                    this.networkManagerField = f;
                    this.networkManagerField.setAccessible(true);
                    break;
                }
            }
        }

        if (this.networkManagerField == null) {
            throw new NoSuchFieldException("Could not find Connection field in ServerGamePacketListenerImpl");
        }

        Class<?> networkClass = this.networkManagerField.getType();

        for (Field f : networkClass.getDeclaredFields()) {
            if (Channel.class.isAssignableFrom(f.getType())) {
                this.channelField = f;
                this.channelField.setAccessible(true);
                break;
            }
        }

        if (this.channelField == null) {
            throw new NoSuchFieldException("Could not find Channel field in Connection");
        }
    }

    @Override
    public Channel getChannel(Player player) {
        try {
            Object serverPlayer = getHandleMethod.invoke(player);
            Object connection = playerConnectionField.get(serverPlayer);
            if (connection == null) return null;
            Object networkManager = networkManagerField.get(connection);
            if (networkManager == null) return null;
            return (Channel) channelField.get(networkManager);
        } catch (Exception e) {
            return null;
        }
    }
}
