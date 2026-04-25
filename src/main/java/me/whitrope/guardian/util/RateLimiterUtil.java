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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Universal utility for rate limiting.
 */
public class RateLimiterUtil {

    private final long windowMillis;
    private final Cache<Object, RateWindow> windows;

    public RateLimiterUtil(long windowMillis) {
        this.windowMillis = windowMillis;
        this.windows = CacheBuilder.newBuilder()
                .expireAfterAccess(Math.max(windowMillis * 3, 5000L), TimeUnit.MILLISECONDS)
                .build();
    }

    public boolean checkExceeded(Object key, int maxTokens) {
        return checkExceeded(key, 1, maxTokens);
    }

    public boolean checkExceeded(Object key, int amount, int maxTokens) {
        if (maxTokens <= 0) return false;

        long now = System.currentTimeMillis();
        RateWindow window = windows.asMap().computeIfAbsent(key, k -> new RateWindow(now));

        synchronized (window) {
            if (now - window.startTime >= windowMillis) {
                window.startTime = now;
                window.count.set(0);
            }
            return window.count.addAndGet(amount) > maxTokens;
        }
    }

    public void reset(Object key) {
        windows.invalidate(key);
    }

    private static class RateWindow {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long startTime;

        RateWindow(long startTime) {
            this.startTime = startTime;
        }
    }
}
