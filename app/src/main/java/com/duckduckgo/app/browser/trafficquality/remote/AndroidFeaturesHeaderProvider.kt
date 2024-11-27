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

package com.duckduckgo.app.browser.trafficquality.remote

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.privacy.config.api.Gpc
import com.squareup.anvil.annotations.ContributesBinding
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

interface AndroidFeaturesHeaderProvider {
    fun provide(): String?
}

@ContributesBinding(AppScope::class)
class RealAndroidFeaturesHeaderProvider @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val featuresRequestHeaderStore: FeaturesRequestHeaderStore,
    private val autoconsent: Autoconsent,
    private val gpc: Gpc,
    private val appTrackingProtection: AppTrackingProtection,
    private val networkProtectionState: NetworkProtectionState,
) : AndroidFeaturesHeaderProvider {
    override fun provide(): String? {
        val versionConfig = featuresRequestHeaderStore.getConfig(appBuildConfig.versionCode)
        if (versionConfig != null) {
            if (shouldLogValue(versionConfig)) {
                return mapFeatures(versionConfig)
            }
        } else {
            return null
        }

        return null
    }

    private fun shouldLogValue(versionConfig: TrafficQualityAppVersion): Boolean {
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

    private fun mapFeatures(versionConfig: TrafficQualityAppVersion): String? {
        return runBlocking {
            val params = mutableMapOf<String, String>()
            if (versionConfig.features.cpm) {
                params[CPM_HEADER] = autoconsent.isAutoconsentEnabled().toString()
            }
            if (versionConfig.features.gpc) {
                params[GPC_HEADER] = gpc.isEnabled().toString()
            }
            if (versionConfig.features.appTP) {
                params[APP_TP_HEADER] = appTrackingProtection.isEnabled().toString()
            }

            if (versionConfig.features.netP) {
                params[NET_P_HEADER] = networkProtectionState.isEnabled().toString()
            }

            if (params.isEmpty()) {
                null
            } else {
                val randomIndex = (0 until params.size).random()
                params.keys.toList()[randomIndex].plus("=").plus(params.values.toList()[randomIndex])
            }
        }
    }

    companion object {
        private const val CPM_HEADER = "cpm_enabled"
        private const val GPC_HEADER = "gpc_enabled"
        private const val APP_TP_HEADER = "atp_enabled"
        private const val NET_P_HEADER = "vpn_enabled"
    }
}
