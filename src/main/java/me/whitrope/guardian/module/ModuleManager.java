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


package me.whitrope.guardian.module;

import io.netty.channel.Channel;
import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.module.impl.*;
import me.whitrope.guardian.processor.OutgoingPacketProcessor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the lifecycle and registration of all protection modules.
 */
public class ModuleManager {

    private static final GuardianModule[] EMPTY = new GuardianModule[0];

    private final Guardian plugin;
    private final List<GuardianModule> modules = new ArrayList<>();
    private volatile GuardianModule[] enabledIncoming = EMPTY;
    private volatile GuardianModule[] enabledOutgoing = EMPTY;

    public ModuleManager(Guardian plugin) {
        this.plugin = plugin;
    }

    public void registerModules() {
        modules.add(new CrashShieldModule(plugin));
        modules.add(new PacketGuardModule(plugin));
        modules.add(new ActivityGuardModule(plugin));
        modules.add(new ExploitBlockerModule(plugin));
        modules.add(new LagControlModule(plugin));
        modules.add(new ConnectionGuardModule(plugin));

        for (GuardianModule module : modules) {
            module.initialize();
        }
        rebuildEnabledCaches();
    }

    public List<GuardianModule> getModules() {
        return modules;
    }

    public void rebuildEnabledCaches() {
        List<GuardianModule> incoming = new ArrayList<>(modules.size());
        List<GuardianModule> outgoing = new ArrayList<>(modules.size());
        for (GuardianModule m : modules) {
            if (!m.isEnabled()) continue;
            incoming.add(m);
            if (m.hasOutgoingHandler()) outgoing.add(m);
        }
        this.enabledIncoming = incoming.toArray(EMPTY);
        this.enabledOutgoing = outgoing.toArray(EMPTY);
    }

    public boolean handleIncomingPacket(Object packet, Player player, Channel channel) {
        GuardianModule[] arr = enabledIncoming;
        for (GuardianModule guardianModule : arr) {
            if (!guardianModule.onPacketReceive(packet, player, channel)) {
                return false;
            }
        }
        return true;
    }

    public boolean handleOutgoingPacket(Object packet, Player player) {
        GuardianModule[] arr = enabledOutgoing;
        for (GuardianModule guardianModule : arr) {
            if (!((OutgoingPacketProcessor) guardianModule).onPacketSend(packet, player)) {
                return false;
            }
        }
        return true;
    }
}
