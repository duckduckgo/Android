/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.pir.impl.pixels

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Reports user-interacted DAU/WAU/MAU pixels for PIR. Unlike [PirEngagementReporter] (which reports
 * product-active users on a scheduled tick and is gated on profile existence), this is driven by the
 * user opening the PIR dashboard. Opening the dashboard is itself the interaction, so there is no
 * active-user gate. Day/week/month deduplication is done with the same elapsed-time pattern.
 */
interface PirInteractionReporter {
    suspend fun attemptFirePixel()
}

@ContributesBinding(AppScope::class)
class RealPirInteractionReporter @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val pirRepository: PirRepository,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pirPixelSender: PirPixelSender,
) : PirInteractionReporter {
    override suspend fun attemptFirePixel() {
        withContext(dispatcherProvider.io()) {
            val nowMs = currentTimeProvider.currentTimeMillis()
            attemptToFireDauPixel(nowMs)
            attemptToFireWauPixel(nowMs)
            attemptToFireMauPixel(nowMs)
        }
    }

    private suspend fun attemptToFireDauPixel(nowMs: Long) {
        val lastPixelMs = pirRepository.getLastPirInteractionDauPixelTimeMs()

        if (!hasDateElapsed(DIFF_DATE_DAU, lastPixelMs, nowMs)) return

        pirPixelSender.reportInteractionDAU()
        pirRepository.setLastPirInteractionDauPixelTimeMs(nowMs)
    }

    private suspend fun attemptToFireWauPixel(nowMs: Long) {
        val lastPixelMs = pirRepository.getLastPirInteractionWauPixelTimeMs()

        if (!hasDateElapsed(DIFF_DATE_WAU, lastPixelMs, nowMs)) return

        pirPixelSender.reportInteractionWAU()
        pirRepository.setLastPirInteractionWauPixelTimeMs(nowMs)
    }

    private suspend fun attemptToFireMauPixel(nowMs: Long) {
        val lastPixelMs = pirRepository.getLastPirInteractionMauPixelTimeMs()

        if (!hasDateElapsed(DIFF_DATE_MAU, lastPixelMs, nowMs)) return

        pirPixelSender.reportInteractionMAU()
        pirRepository.setLastPirInteractionMauPixelTimeMs(nowMs)
    }

    private fun hasDateElapsed(
        requiredDiff: Long,
        lastPixelMs: Long,
        nowMs: Long,
    ): Boolean {
        if (lastPixelMs == 0L) return true

        val lastPixelDate = Instant.ofEpochMilli(lastPixelMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val nowDate = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate()
        return ChronoUnit.DAYS.between(lastPixelDate, nowDate) >= requiredDiff
    }

    companion object {
        private const val DIFF_DATE_DAU = 1L
        private const val DIFF_DATE_WAU = 7L
        private const val DIFF_DATE_MAU = 28L
    }
}
