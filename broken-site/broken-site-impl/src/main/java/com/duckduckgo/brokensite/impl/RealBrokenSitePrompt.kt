/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.brokensite.impl

import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.brokensite.api.BrokenSitePrompt
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal const val REFRESH_COUNT_WINDOW = 20L

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal const val REFRESH_COUNT_LIMIT = 3

@ContributesBinding(AppScope::class)
class RealBrokenSitePrompt @Inject constructor(
    private val brokenSiteReportRepository: BrokenSiteReportRepository,
    private val brokenSitePromptRCFeature: BrokenSitePromptRCFeature,
    private val currentTimeProvider: CurrentTimeProvider,
    private val duckGoUrlDetector: DuckDuckGoUrlDetector,
) : BrokenSitePrompt {

    private val _featureEnabled by lazy { brokenSitePromptRCFeature.self().isEnabled() }

    override suspend fun userDismissedPrompt() {
        if (!_featureEnabled) return

        val currentTimestamp = currentTimeProvider.localDateTimeNow()

        brokenSiteReportRepository.addDismissal(currentTimestamp)
    }

    override suspend fun userAcceptedPrompt() {
        if (!_featureEnabled) return

        brokenSiteReportRepository.clearAllDismissals()
    }

    override suspend fun isFeatureEnabled(): Boolean {
        return _featureEnabled
    }

    override fun pageRefreshed(
        url: Uri,
    ) {
        brokenSiteReportRepository.addRefresh(url, currentTimeProvider.localDateTimeNow())
    }

    override fun resetRefreshCount() {
        brokenSiteReportRepository.resetRefreshCount()
    }

    override fun getUserRefreshesCount(): Int {
        return brokenSiteReportRepository.getAndUpdateUserRefreshesBetween(
            currentTimeProvider.localDateTimeNow().minusSeconds(REFRESH_COUNT_WINDOW),
            currentTimeProvider.localDateTimeNow(),
        ).also {
            if (it >= REFRESH_COUNT_LIMIT) {
                brokenSiteReportRepository.resetRefreshCount()
            }
        }
    }

    override suspend fun shouldShowBrokenSitePrompt(url: String): Boolean {
        if (!isFeatureEnabled() || getUserRefreshesCount() < REFRESH_COUNT_LIMIT || duckGoUrlDetector.isDuckDuckGoUrl(url)) {
            return false
        }

        val currentTimestamp = currentTimeProvider.localDateTimeNow()

        // Check if we're still in a cooldown period
        brokenSiteReportRepository.getNextShownDate()?.let { nextDate ->
            if (currentTimestamp.isBefore(nextDate)) {
                return false
            }
        }

        val dismissStreakResetDays = brokenSiteReportRepository.getDismissStreakResetDays().toLong()
        val dismissalCount = brokenSiteReportRepository.getDismissalCountBetween(
            currentTimestamp.minusDays(dismissStreakResetDays),
            currentTimestamp,
        )

        return dismissalCount < brokenSiteReportRepository.getMaxDismissStreak()
    }

    override suspend fun ctaShown() {
        val currentTimestamp = currentTimeProvider.localDateTimeNow()
        val newNextShownDate = currentTimestamp.plusDays(brokenSiteReportRepository.getCoolDownDays())
        brokenSiteReportRepository.setNextShownDate(newNextShownDate)
    }
}
