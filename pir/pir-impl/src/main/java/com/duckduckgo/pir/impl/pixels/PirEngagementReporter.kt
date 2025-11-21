/*
 * Copyright (c) 2025 DuckDuckGo
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
import com.duckduckgo.pir.impl.checker.PirWorkHandler
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

interface PirEngagementReporter {
    suspend fun attemptFirePixel()
}

@ContributesBinding(AppScope::class)
class RealPirEngagementReporter @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val pirWorkHandler: PirWorkHandler,
    private val pirRepository: PirRepository,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pirPixelSender: PirPixelSender,
) : PirEngagementReporter {
    override suspend fun attemptFirePixel() {
        withContext(dispatcherProvider.io()) {
            if (!isActiveUser()) return@withContext

            val nowMs = currentTimeProvider.currentTimeMillis()
            attemptToFireDauPixel(nowMs)
            attemptToFireWauPixel(nowMs)
            attemptToFireMauPixel(nowMs)
        }
    }

    private suspend fun attemptToFireDauPixel(nowMs: Long) {
        val lastPixelMs = pirRepository.getLastPirDauPixelTimeMs()

        if (!hasDateElapsed(DIFF_DATE_DAU, lastPixelMs, nowMs)) return

        pirPixelSender.reportDAU()
        pirRepository.setLastPirDauPixelTimeMs(nowMs)
    }

    private suspend fun attemptToFireWauPixel(nowMs: Long) {
        val lastPixelMs = pirRepository.getLastPirWauPixelTimeMs()

        if (!hasDateElapsed(DIFF_DATE_WAU, lastPixelMs, nowMs)) return

        pirPixelSender.reportWAU()
        pirRepository.setLastPirWauPixelTimeMs(nowMs)
    }

    private suspend fun attemptToFireMauPixel(nowMs: Long) {
        val lastPixelMs = pirRepository.getLastPirMauPixelTimeMs()

        if (!hasDateElapsed(DIFF_DATE_MAU, lastPixelMs, nowMs)) return

        pirPixelSender.reportMAU()
        pirRepository.setLastPirMauPixelTimeMs(nowMs)
    }

    /**
     * A user is considered active if they:
     * - Have a valid subscription
     * - Has PIR enabled
     * - Has a userprofile set up for scanning
     */
    private suspend fun isActiveUser(): Boolean {
        return pirWorkHandler.canRunPir().firstOrNull() == true && pirRepository.getValidUserProfileQueries().isNotEmpty()
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
