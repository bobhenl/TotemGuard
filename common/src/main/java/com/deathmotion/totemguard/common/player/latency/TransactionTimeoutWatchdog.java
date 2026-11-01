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

package com.deathmotion.totemguard.common.player.latency;

import com.deathmotion.totemguard.common.TGPlatform;
import com.deathmotion.totemguard.common.config.schema.TransactionTimeoutOptions;
import com.deathmotion.totemguard.common.player.PlayerRepositoryImpl;
import com.deathmotion.totemguard.common.player.TGPlayer;
import com.deathmotion.totemguard.common.player.data.ping.PingData;
import com.deathmotion.totemguard.common.util.ScheduledTask;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

// Kicks a client that keeps playing but stops answering our transactions. Clients answer
// transactions on their main thread but keep-alives off it, so a client whose main thread
// stalls (terrain loading, a hidden browser tab, slow hardware) still passes the vanilla
// keep-alive check. Time it spends frozen does not count here, and web / legacy clients
// that cannot keep up are left to the vanilla keep-alive timeout entirely.
public final class TransactionTimeoutWatchdog {

    private static final long PERIOD_SECONDS = 1L;

    private final PlayerRepositoryImpl playerRepository;
    private ScheduledTask task;

    public TransactionTimeoutWatchdog(PlayerRepositoryImpl playerRepository) {
        this.playerRepository = playerRepository;
    }

    public void start() {
        task = TGPlatform.getInstance().getScheduler().runAsyncTaskAtFixedRate(
                this::tick, PERIOD_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        try {
            TransactionTimeoutOptions options = TGPlatform.getInstance().getConfigRepository().configView().transactionTimeout();
            for (TGPlayer player : playerRepository.getPlayers()) {
                check(player, options, now);
            }
        } catch (Exception ex) {
            TGPlatform.getInstance().getLogger().log(Level.WARNING, "Transaction timeout watchdog tick failed", ex);
        }
    }

    private void check(TGPlayer player, TransactionTimeoutOptions options, long now) {
        PingData pingData = player.getPingData();
        long oldest = pingData.getOldestPendingTransactionSentAt();
        if (oldest == 0L) return;

        long timeout = pingData.isObservedTransactionReply() ? options.timeoutMillis() : options.firstReplyTimeoutMillis();
        long total = now - oldest;
        if (total < timeout) return;

        String exemptReason = exemptReason(player, options);
        if (exemptReason != null) {
            if (pingData.getTimeoutNoticeFor() != oldest) {
                pingData.setTimeoutNoticeFor(oldest);
                TGPlatform.getInstance().getLogger().info("Not kicking " + player.getName() + " for an unanswered transaction ("
                        + total / 1000 + "s, " + exemptReason + "), the vanilla keep-alive timeout still applies");
            }
            return;
        }

        boolean hardCap = options.maxMillis() > 0L && total >= options.maxMillis();
        if (!hardCap) {
            long lastTick = pingData.getLastClientTickAt();
            if (lastTick == 0L || now - lastTick > options.freezeGapMillis()) return;
            long stalled = now - Math.max(oldest, pingData.getClientActiveSince());
            if (stalled < timeout) return;
        }

        player.timedOut("unanswered for " + total / 1000 + "s, brand " + player.getClientBrand()
                + ", version " + player.getClientVersion().getReleaseName() + (hardCap ? ", hard cap" : ""));
    }

    private static String exemptReason(TGPlayer player, TransactionTimeoutOptions options) {
        String brand = player.getClientBrand();
        if (options.isExemptBrand(brand)) return "brand " + brand;
        if (options.exemptLegacyClients() && player.getClientVersion().isOlderThan(ClientVersion.V_1_17)) {
            return "legacy client " + player.getClientVersion().getReleaseName();
        }
        return null;
    }
}
