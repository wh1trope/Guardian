package me.whitrope.guardian.processor;

import org.bukkit.entity.Player;

/**
 * Interface for modules that process outgoing packets.
 */
public interface OutgoingPacketProcessor {

    boolean onPacketSend(Object packet, Player player);
}
