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

import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.util.function.Supplier;

/**
 * Utility class for handling entity attributes and NMS conversions.
 */
public class AttributeUtil {

    public static <T> T getOrCreate(Channel channel, AttributeKey<T> key, Supplier<T> supplier) {
        Attribute<T> attr = channel.attr(key);
        T data = attr.get();
        if (data == null) {
            T fresh = supplier.get();
            T existing = attr.setIfAbsent(fresh);
            data = existing != null ? existing : fresh;
        }
        return data;
    }
}
