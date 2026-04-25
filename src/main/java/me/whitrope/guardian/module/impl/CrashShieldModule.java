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
import me.whitrope.guardian.module.GuardianModule;
import me.whitrope.guardian.processor.impl.*;

/**
 * General protection module against various server crash techniques.
 */
public class CrashShieldModule extends GuardianModule {

    public CrashShieldModule(Guardian plugin) {
        super(plugin, "CrashShield");
    }

    @Override
    protected void onEnable() {
        if (getConfigManager().isCheckEnabled("CrashShield", "string-exploit")) {
            StringExploitProcessor stringProcessor = new StringExploitProcessor(this);
            addSpecificProcessor("ServerboundChatPacket", stringProcessor);
            addSpecificProcessor("ServerboundChatCommandPacket", stringProcessor);
            addSpecificProcessor("ServerboundChatCommandSignedPacket", stringProcessor);
            addSpecificProcessor("PacketPlayInChat", stringProcessor);
            addSpecificProcessor("ServerboundCustomPayloadPacket", stringProcessor);
            addSpecificProcessor("PacketPlayInCustomPayload", stringProcessor);
            addSpecificProcessor("ServerboundSignUpdatePacket", stringProcessor);
            addSpecificProcessor("PacketPlayInUpdateSign", stringProcessor);
            addSpecificProcessor("ServerboundEditBookPacket", stringProcessor);
            addSpecificProcessor("PacketPlayInBEdit", stringProcessor);
            addSpecificProcessor("ServerboundCommandSuggestionPacket", stringProcessor);
            addSpecificProcessor("PacketPlayInTabComplete", stringProcessor);
            addSpecificProcessor("ServerboundRenameItemPacket", stringProcessor);
            addSpecificProcessor("PacketPlayInItemName", stringProcessor);
            addSpecificProcessor("ServerboundResourcePackPacket", stringProcessor);
            addSpecificProcessor("PacketPlayInResourcePackStatus", stringProcessor);
            addSpecificProcessor("ServerboundSetJigsawBlockPacket", stringProcessor);
            addSpecificProcessor("PacketPlayInSetJigsaw", stringProcessor);
            addSpecificProcessor("ServerboundSetCommandBlockPacket", stringProcessor);
            addSpecificProcessor("PacketPlayInSetCommandBlock", stringProcessor);
            addSpecificProcessor("ServerboundSetStructureBlockPacket", stringProcessor);
            addSpecificProcessor("PacketPlayInStruct", stringProcessor);
        }

        if (getConfigManager().isCheckEnabled("CrashShield", "payload-exploit")) {
            PayloadExploitProcessor payloadProcessor = new PayloadExploitProcessor(this);
            addSpecificProcessor("ServerboundCustomPayloadPacket", payloadProcessor);
            addSpecificProcessor("PacketPlayInCustomPayload", payloadProcessor);
        }

        if (getConfigManager().isCheckEnabled("CrashShield", "ghost-book")) {
            BookEditProcessor bookProcessor = new BookEditProcessor(this);
            addSpecificProcessor("ServerboundEditBookPacket", bookProcessor);
            addSpecificProcessor("PacketPlayInBEdit", bookProcessor);
        }

        if (getConfigManager().isCheckEnabled("CrashShield", "creative-slot")) {
            CreativeSlotProcessor creativeProcessor = new CreativeSlotProcessor(this);
            addSpecificProcessor("ServerboundSetCreativeModeSlotPacket", creativeProcessor);
            addSpecificProcessor("PacketPlayInSetCreativeSlot", creativeProcessor);
        }

        if (getConfigManager().isCheckEnabled("CrashShield", "nbt-exploit")) {
            NbtExploitProcessor nbtProcessor = new NbtExploitProcessor(this);
            addSpecificProcessor("ServerboundContainerClickPacket", nbtProcessor);
            addSpecificProcessor("PacketPlayInWindowClick", nbtProcessor);
            addSpecificProcessor("ServerboundSetCreativeModeSlotPacket", nbtProcessor);
            addSpecificProcessor("PacketPlayInSetCreativeSlot", nbtProcessor);
        }

        if (getConfigManager().isCheckEnabled("CrashShield", "invalid-position")) {
            MovementGuardProcessor movementProcessor = new MovementGuardProcessor(this);
            addSpecificProcessor("ServerboundMovePlayerPacket$Pos", movementProcessor);
            addSpecificProcessor("ServerboundMovePlayerPacket$PosRot", movementProcessor);
            addSpecificProcessor("ServerboundMovePlayerPacket$Rot", movementProcessor);
            addSpecificProcessor("ServerboundMoveVehiclePacket", movementProcessor);
            addSpecificProcessor("PacketPlayInFlying$PacketPlayInPosition", movementProcessor);
            addSpecificProcessor("PacketPlayInFlying$PacketPlayInPositionLook", movementProcessor);
            addSpecificProcessor("PacketPlayInFlying$PacketPlayInLook", movementProcessor);
            addSpecificProcessor("PacketPlayInVehicleMove", movementProcessor);

            addSpecificProcessor("ServerboundUseItemOnPacket", movementProcessor);
            addSpecificProcessor("PacketPlayInBlockPlace", movementProcessor);
            addSpecificProcessor("ServerboundPlayerActionPacket", movementProcessor);
            addSpecificProcessor("PacketPlayInBlockDig", movementProcessor);
            addSpecificProcessor("ServerboundInteractPacket", movementProcessor);
            addSpecificProcessor("PacketPlayInUseEntity", movementProcessor);
        }

        if (getConfigManager().isCheckEnabled("CrashShield", "pick-item")) {
            PickItemProcessor pickItemProcessor = new PickItemProcessor(this);
            addSpecificProcessor("ServerboundPickItemPacket", pickItemProcessor);
            addSpecificProcessor("PacketPlayInPickItem", pickItemProcessor);
        }

        if (getConfigManager().isCheckEnabled("CrashShield", "recipe-book")) {
            RecipeBookProcessor recipeBookProcessor = new RecipeBookProcessor(this);
            addSpecificProcessor("ServerboundRecipeBookSeenRecipePacket", recipeBookProcessor);
            addSpecificProcessor("ServerboundRecipeBookChangeSettingsPacket", recipeBookProcessor);
            addSpecificProcessor("PacketPlayInRecipeDisplayed", recipeBookProcessor);
            addSpecificProcessor("PacketPlayInRecipeSettings", recipeBookProcessor);
        }
    }
}
