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

import com.duckduckgo.app.browser.trafficquality.Result.Allowed
import com.duckduckgo.app.browser.trafficquality.Result.NotAllowed
import com.duckduckgo.app.browser.trafficquality.remote.FeaturesRequestHeaderStore
import com.duckduckgo.app.browser.trafficquality.remote.TrafficQualityAppVersion
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject

interface CustomHeaderAllowedChecker {
    fun isAllowed(): Result<TrafficQualityAppVersion>
}

sealed class Result<out R> {
    data class Allowed(val config: TrafficQualityAppVersion) : Result<TrafficQualityAppVersion>()
    data object NotAllowed : Result<Nothing>()
}

@ContributesBinding(AppScope::class)
class RealCustomHeaderGracePeriodChecker @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val featuresRequestHeaderStore: FeaturesRequestHeaderStore,
) : CustomHeaderAllowedChecker {
    override fun isAllowed(): Result<TrafficQualityAppVersion> {
        val config = featuresRequestHeaderStore.getConfig()
        val versionConfig = config.find { it.appVersion == appBuildConfig.versionCode }
        return if (versionConfig != null) {
            if (shouldSendHeader(versionConfig)) {
                Allowed(versionConfig)
            } else {
                NotAllowed
            }
        } else {
            NotAllowed
        }
    }

    private fun shouldSendHeader(versionConfig: TrafficQualityAppVersion): Boolean {
        val appBuildDateMillis = appBuildConfig.buildDateTimeMillis
        if (appBuildDateMillis == 0L) {
            return false
        }

        val appBuildDate = LocalDateTime.ofEpochSecond(appBuildDateMillis / 1000, 0, ZoneOffset.UTC)
        val now = LocalDateTime.now(ZoneOffset.UTC)

        val daysSinceBuild = ChronoUnit.DAYS.between(appBuildDate, now)
        val daysUntilLoggingStarts = versionConfig.daysUntilLoggingStarts
        val daysForAppVersionLogging = daysUntilLoggingStarts + versionConfig.daysLogging

        return daysSinceBuild in daysUntilLoggingStarts..daysForAppVersionLogging
    }
}
