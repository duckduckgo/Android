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

package com.duckduckgo.app.statistics.user_segments

import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.StatisticsPixelName
import com.duckduckgo.app.statistics.user_segments.SegmentCalculation.ActivityType
import com.duckduckgo.app.statistics.user_segments.SegmentCalculation.ActivityType.APP_USE
import com.duckduckgo.app.statistics.user_segments.SegmentCalculation.ActivityType.SEARCH
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class UserSegmentsPixelSender @Inject constructor(
    private val usageHistory: UsageHistory,
    private val segmentCalculation: SegmentCalculation,
    private val pixel: Pixel,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val crashLogger: CrashLogger,
) : AtbLifecyclePlugin {

    override fun onSearchRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        coroutineScope.launch(dispatcherProvider.io()) {
            val params = handleAtbRefresh(SEARCH, oldAtb, newAtb)
            if (params.isNotEmpty()) {
                pixel.fire(StatisticsPixelName.RETENTION_SEGMENTS.pixelName, params)
            }
        }
    }

    override fun onAppRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        coroutineScope.launch(dispatcherProvider.io()) {
            val params = handleAtbRefresh(APP_USE, oldAtb, newAtb)
            if (params.isNotEmpty()) {
                pixel.fire(StatisticsPixelName.RETENTION_SEGMENTS.pixelName, params)
            }
        }
    }

    // Internal for testing only
    internal suspend fun handleAtbRefresh(activityType: ActivityType, oldAtb: String, newAtb: String): Map<String, String> {
        try {
            val usageHistory = if (activityType == SEARCH) {
                usageHistory.addSearchUsage(newAtb)
                usageHistory.getSearchUsageHistory()
            } else {
                usageHistory.addAppUsage(newAtb)
                usageHistory.getAppUsageHistory()
            }

            if (oldAtb.asNumber() != newAtb.asNumber()) {
                return segmentCalculation.computeUserSegmentForActivityType(activityType, usageHistory).toPixelParams()
            }

            return emptyMap()
        } catch (t: Throwable) {
            crashLogger.logCrash(CrashLogger.Crash(shortName = "m_user_segment_crash", t = t))
            return emptyMap()
        }
    }
}
