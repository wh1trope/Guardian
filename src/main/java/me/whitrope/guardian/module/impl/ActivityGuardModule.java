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

import me.whitrope.guardian.Guardian;
import me.whitrope.guardian.config.ConfigManager;
import me.whitrope.guardian.module.GuardianModule;
import me.whitrope.guardian.processor.PacketProcessor;
import me.whitrope.guardian.processor.impl.KeepAliveProcessor;
import me.whitrope.guardian.processor.impl.RateLimitProcessor;
import me.whitrope.guardian.processor.impl.SignUpdateProcessor;
import me.whitrope.guardian.processor.impl.WindowClickProcessor;

/**
 * Module for monitoring player activity and preventing automated exploits.
 */
public class ActivityGuardModule extends GuardianModule {

    public ActivityGuardModule(Guardian plugin) {
        super(plugin, "ActivityGuard");
    }

    @Override
    protected void onEnable() {
        ConfigManager cfg = getConfigManager();

        if (cfg.isCheckEnabled("ActivityGuard", "window-click")) {
            register(new WindowClickProcessor(this),
                    "ServerboundContainerClickPacket", "PacketPlayInWindowClick",
                    "ServerboundContainerButtonClickPacket", "PacketPlayInEnchantItem", "PacketPlayInButtonClick");
        }

        if (cfg.isCheckEnabled("ActivityGuard", "sign-update")) {
            register(new SignUpdateProcessor(this),
                    "ServerboundSignUpdatePacket", "PacketPlayInUpdateSign");
        }

        if (cfg.isCheckEnabled("ActivityGuard", "command-suggestion")) {
            registerRate("CommandSuggestion", "activity-guard.command-suggestion",
                    10, 2, 5.0,
                    "ServerboundCommandSuggestionPacket", "PacketPlayInTabComplete");
        }

        if (cfg.isCheckEnabled("ActivityGuard", "chat")) {
            registerRate("Chat", "activity-guard.chat",
                    6, 2, 5.0,
                    "ServerboundChatPacket", "ServerboundChatCommandPacket",
                    "ServerboundChatCommandSignedPacket", "ServerboundChatSessionUpdatePacket",
                    "PacketPlayInChat");
        }

        if (cfg.isCheckEnabled("ActivityGuard", "plugin-message-flood")) {
            registerRate("PluginMessage", "activity-guard.plugin-message",
                    30, 4, 5.0,
                    "ServerboundCustomPayloadPacket", "PacketPlayInCustomPayload");
        }

        if (cfg.isCheckEnabled("ActivityGuard", "interact-flood")) {
            registerRate("Interact", "activity-guard.interact",
                    40, 6, 5.0,
                    "ServerboundUseItemPacket", "PacketPlayInUseItem",
                    "ServerboundUseItemOnPacket", "PacketPlayInBlockPlace",
                    "ServerboundPlayerActionPacket", "PacketPlayInBlockDig",
                    "ServerboundInteractPacket", "PacketPlayInUseEntity");
        }

        if (cfg.isCheckEnabled("ActivityGuard", "entity-action-flood")) {
            registerRate("EntityAction", "activity-guard.entity-action",
                    30, 5, 2.5,
                    "ServerboundPlayerCommandPacket", "PacketPlayInEntityAction",
                    "ServerboundPlayerInputPacket", "PacketPlayInSteerVehicle");
        }

        if (cfg.isCheckEnabled("ActivityGuard", "keepalive")) {
            register(new KeepAliveProcessor(this),
                    "ServerboundKeepAlivePacket", "PacketPlayInKeepAlive",
                    "ServerboundPongPacket", "ServerboundTransactionPacket",
                    "PacketPlayInTransaction");
        }

        if (cfg.isCheckEnabled("ActivityGuard", "resource-pack-status")) {
            registerRate("ResourcePackStatus", "activity-guard.resource-pack-status",
                    8, 2, 5.0,
                    "ServerboundResourcePackPacket", "PacketPlayInResourcePackStatus");
        }

        if (cfg.isCheckEnabled("ActivityGuard", "vehicle-flood")) {
            registerRate("VehicleFlood", "activity-guard.vehicle-flood",
                    100, 20, 5.0,
                    "ServerboundMoveVehiclePacket", "PacketPlayInVehicleMove",
                    "ServerboundPaddleBoatPacket", "PacketPlayInBoatMove");
        }

        if (cfg.isCheckEnabled("ActivityGuard", "container-close-flood")) {
            registerRate("ContainerClose", "activity-guard.container-close",
                    10, 3, 5.0,
                    "ServerboundContainerClosePacket", "PacketPlayInCloseWindow");
        }

        if (cfg.isCheckEnabled("ActivityGuard", "pick-item-flood")) {
            registerRate("PickItem", "activity-guard.pick-item",
                    10, 3, 5.0,
                    "ServerboundPickItemPacket", "PacketPlayInPickItem");
        }

        if (cfg.isCheckEnabled("ActivityGuard", "place-recipe-flood")) {
            registerRate("PlaceRecipe", "activity-guard.place-recipe",
                    5, 2, 5.0,
                    "ServerboundPlaceRecipePacket", "PacketPlayInAutoRecipe");
        }

        if (cfg.isCheckEnabled("ActivityGuard", "chunk-batch-flood")) {
            registerRate("ChunkBatch", "activity-guard.chunk-batch",
                    5, 2, 5.0,
                    "ServerboundChunkBatchReceivedPacket");
        }

        if (cfg.isCheckEnabled("ActivityGuard", "difficulty-change-flood")) {
            registerRate("DifficultyChange", "activity-guard.difficulty-change",
                    2, 1, 5.0,
                    "ServerboundChangeDifficultyPacket");
        }
    }

    private void register(PacketProcessor processor, String... packetNames) {
        for (String name : packetNames) {
            addSpecificProcessor(name, processor);
        }
    }

    private void registerRate(String label, String configPath,
                              int defaultPerSecond, int defaultPerTick, double vl,
                              String... packetNames) {
        RateLimitProcessor rl = new RateLimitProcessor(this, label, configPath,
                defaultPerSecond, defaultPerTick, vl);
        register(rl, packetNames);
    }
}
