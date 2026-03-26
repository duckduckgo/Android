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
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.brokensite.api.BrokenSitePrompt
import com.duckduckgo.brokensite.api.RefreshPattern
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat
import javax.inject.Inject

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

    override fun getUserRefreshPatterns(): Set<RefreshPattern> {
        return brokenSiteReportRepository.getRefreshPatterns(currentTimeProvider.localDateTimeNow())
    }

    override suspend fun shouldShowBrokenSitePrompt(url: String, refreshPatterns: Set<RefreshPattern>): Boolean {
        if (!isFeatureEnabled() || duckGoUrlDetector.isDuckDuckGoUrl(url)) {
            return false
        }

        if (refreshPatterns.none { it == RefreshPattern.THRICE_IN_20_SECONDS }) {
            return false
        }

        val currentTimestamp = currentTimeProvider.localDateTimeNow()

        // Check if we're still in a cooldown period
        brokenSiteReportRepository.getNextShownDate()?.let { nextDate ->
            if (currentTimestamp.isBefore(nextDate)) {
                logcat { "BrokenSitePrompt should NOT show bc cooldown: NextDate= $nextDate" }
                return false
            }
        }

        // Check if we've reached max dismissals
        val dismissStreakResetDays = brokenSiteReportRepository.getDismissStreakResetDays().toLong()
        val dismissalCount = brokenSiteReportRepository.getDismissalCountBetween(
            currentTimestamp.minusDays(dismissStreakResetDays),
            currentTimestamp,
        )
        logcat { "BrokenSitePrompt final check: dismissCount($dismissalCount) < maxDismissStreak?" }

        return dismissalCount < brokenSiteReportRepository.getMaxDismissStreak()
    }

    override suspend fun ctaShown() {
        val currentTimestamp = currentTimeProvider.localDateTimeNow()
        val newNextShownDate = currentTimestamp.plusDays(brokenSiteReportRepository.getCoolDownDays())
        brokenSiteReportRepository.setNextShownDate(newNextShownDate)
    }
}
