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

import org.bukkit.command.CommandSender;

/**
 * Utility class for message formatting and color handling.
 */
public final class ChatUtil {

    private ChatUtil() {
    }

    public static String fix(String s) {
        if (s == null) return "";
        return s.replace("&&", "\u0000")
                .replace('&', '§')
                .replace("\u0000", "&")
                .replace(">>", "»")
                .replace("<<", "«");
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (sender != null && message != null) {
            sender.sendMessage(fix(message));
        }
    }
}
