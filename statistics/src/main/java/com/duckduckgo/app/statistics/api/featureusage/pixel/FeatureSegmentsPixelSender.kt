/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.statistics.api.featureusage.pixel

import com.duckduckgo.app.statistics.api.RefreshRetentionAtbPlugin
import com.duckduckgo.app.statistics.api.featureusage.FeatureSegmentsManager
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class FeatureSegmentsPixelSender @Inject constructor(
    private val browserProperties: UserBrowserProperties,
    private val featureSegmentsManager: FeatureSegmentsManager,
) : RefreshRetentionAtbPlugin {

    override fun onSearchRetentionAtbRefreshed() {
        // we are not interested in search metrics for feature segments
    }

    override fun onAppRetentionAtbRefreshed() {
        tryToFireDailyPixel()
    }

    private fun tryToFireDailyPixel() {
        val shouldFireSegmentPixel = browserProperties.daysSinceInstalled() > 0 &&
            browserProperties.daysSinceInstalled() - 1 > featureSegmentsManager.lastRetentionDayPixelSent() &&
            featureSegmentsManager.isSendPixelEnabled()
        if (shouldFireSegmentPixel) {
            featureSegmentsManager.fireFeatureSegmentsPixel()
            val retentionDayPixelSent = browserProperties.daysSinceInstalled() - 1
            featureSegmentsManager.updateLastRetentionDayPixelSent(retentionDayPixelSent.toInt())
            featureSegmentsManager.restartDailySearchCount()
        }
    }
}
