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

package com.duckduckgo.app.browser.trafficquality

import com.duckduckgo.app.browser.trafficquality.remote.TrafficQualityAppVersion
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject

interface CustomHeaderGracePeriodChecker {
    fun shouldSendValue(versionConfig: TrafficQualityAppVersion): Boolean
}

@ContributesBinding(AppScope::class)
class RealCustomHeaderGracePeriodChecker @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : CustomHeaderGracePeriodChecker {
    override fun shouldSendValue(versionConfig: TrafficQualityAppVersion): Boolean {
        val appBuildDateMillis = appBuildConfig.buildDateTimeMillis
        if (appBuildDateMillis == 0L) {
            return false
        }

        val appBuildDate = LocalDateTime.ofEpochSecond(appBuildDateMillis / 1000, 0, ZoneOffset.UTC)
        val now = LocalDateTime.now(ZoneOffset.UTC)

        val daysSinceBuild = ChronoUnit.DAYS.between(appBuildDate, now)
        val daysUntilLoggingStarts = versionConfig.daysUntilLoggingStarts
        val daysForAppVersionLogging = versionConfig.daysUntilLoggingStarts + versionConfig.daysLogging

        return daysSinceBuild in daysUntilLoggingStarts..daysForAppVersionLogging
    }
}
