/*
 * This file is part of TotemGuard - https://github.com/Bram1903/TotemGuard
 * Copyright (C) 2026 Bram and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.deathmotion.totemguard.common.config.schema;

import java.util.List;
import java.util.Locale;

public record TransactionTimeoutOptions(
        long timeoutMillis,
        long firstReplyTimeoutMillis,
        long freezeGapMillis,
        long maxMillis,
        boolean exemptLegacyClients,
        List<String> exemptBrands
) {

    public static TransactionTimeoutOptions of(int timeoutSeconds, int firstReplySeconds, int freezeGapMillis,
                                               int maxSeconds, boolean exemptLegacyClients, List<String> exemptBrands) {
        long timeout = Math.max(5, timeoutSeconds > 0 ? timeoutSeconds : 30) * 1000L;
        long firstReply = Math.max(timeout, (firstReplySeconds > 0 ? firstReplySeconds : 120) * 1000L);
        long gap = Math.max(500, freezeGapMillis > 0 ? freezeGapMillis : 3000);
        long max = maxSeconds > 0 ? Math.max(firstReply, maxSeconds * 1000L) : 0L;
        List<String> brands = exemptBrands.stream()
                .map(b -> b.trim().toLowerCase(Locale.ROOT))
                .filter(b -> !b.isEmpty())
                .toList();
        return new TransactionTimeoutOptions(timeout, firstReply, gap, max, exemptLegacyClients, brands);
    }

    public boolean isExemptBrand(String brand) {
        if (brand == null || exemptBrands.isEmpty()) return false;
        String lower = brand.toLowerCase(Locale.ROOT);
        for (String b : exemptBrands) {
            if (lower.contains(b)) return true;
        }
        return false;
    }
}
