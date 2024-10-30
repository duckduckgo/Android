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

import com.duckduckgo.brokensite.api.BrokenSitePrompt
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.time.LocalDate
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealBrokenSitePrompt @Inject constructor(
    private val brokenSiteReportRepository: BrokenSiteReportRepository,
    private val brokenSitePromptRCFeature: BrokenSitePromptRCFeature,
) : BrokenSitePrompt {

    private val _featureEnabled by lazy { brokenSitePromptRCFeature.self().isEnabled() }

    override suspend fun userDismissedPrompt() {
        if (!_featureEnabled) return
        brokenSiteReportRepository.getNextShownDate()?.let {
            if (brokenSiteReportRepository.getDismissStreak() == brokenSiteReportRepository.getMaxDismissStreak() - 1) {
                val nextShownDate = brokenSiteReportRepository.getNextShownDate()
                val newNextShownDate = LocalDate.now().plusDays(brokenSiteReportRepository.getCoolDownDays().toLong())

                if (newNextShownDate.isAfter(nextShownDate)) {
                    brokenSiteReportRepository.setNextShownDate(newNextShownDate)
                }
            }
        }
        brokenSiteReportRepository.incrementDismissStreak()
    }

    override suspend fun userAcceptedPrompt() {
        if (!_featureEnabled) return
        brokenSiteReportRepository.resetDismissStreak()
        // TODO (cbarreiro) Set next shown date based on limiting logic: https://app.asana.com/0/0/1208572901396846/f
        brokenSiteReportRepository.setNextShownDate(null)
    }

    override fun isFeatureEnabled(): Boolean {
        return _featureEnabled
    }
}
