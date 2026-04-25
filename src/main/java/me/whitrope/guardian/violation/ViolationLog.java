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

package me.whitrope.guardian.violation;

import java.util.UUID;

/**
 * Represents a single recorded violation event.
 */
public class ViolationLog {

    private final String playerName;
    private final UUID playerUUID;
    private final String moduleName;
    private final String detail;
    private final boolean isCrash;
    private final long timestamp;

    public ViolationLog(String playerName, UUID playerUUID, String moduleName, String detail, boolean isCrash) {
        this.playerName = playerName;
        this.playerUUID = playerUUID;
        this.moduleName = moduleName;
        this.detail = detail;
        this.isCrash = isCrash;
        this.timestamp = System.currentTimeMillis();
    }

    public ViolationLog(String playerName, String moduleName, String detail, boolean isCrash) {
        this(playerName, null, moduleName, detail, isCrash);
    }

    public String getPlayerName() {
        return playerName;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isCrash() {
        return isCrash;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
