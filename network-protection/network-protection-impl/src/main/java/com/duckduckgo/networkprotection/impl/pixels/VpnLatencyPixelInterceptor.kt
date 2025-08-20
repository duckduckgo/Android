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

package com.duckduckgo.networkprotection.impl.pixels

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_REPORT_EXCELLENT_LATENCY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_REPORT_GOOD_LATENCY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_REPORT_MODERATE_LATENCY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_REPORT_POOR_LATENCY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_REPORT_TERRIBLE_LATENCY
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class,
)
class VpnLatencyPixelInterceptor @Inject constructor(
    private val netPGeoswitchingRepository: NetPGeoswitchingRepository,
    private val appBuildConfig: AppBuildConfig,
) : PixelInterceptorPlugin, Interceptor {
    override fun getInterceptor(): Interceptor = this

    override fun intercept(chain: Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()
        val url = if (LATENCY_PIXELS.any { pixel.startsWith(it) }) {
            chain.request().url.newBuilder()
                .addQueryParameter(PARAM_LOCATION, getLocationParamValue())
                .addQueryParameter(PARAM_OSABOVE15, isOsAbove15().toString())
                .build()
        } else {
            chain.request().url
        }

        return chain.proceed(request.url(url).build())
    }

    private fun getLocationParamValue(): String {
        return runBlocking {
            if (netPGeoswitchingRepository.getUserPreferredLocation().countryCode != null) {
                VALUE_LOCATION_CUSTOM
            } else {
                VALUE_LOCATION_NEAREST
            }
        }
    }

    private fun isOsAbove15(): Boolean {
        return appBuildConfig.sdkInt >= 35 // API 35 (Android 15 / VANILLA_ICE_CREAM)
    }

    companion object {
        private val LATENCY_PIXELS = listOf(
            NETP_REPORT_TERRIBLE_LATENCY.pixelName,
            NETP_REPORT_POOR_LATENCY.pixelName,
            NETP_REPORT_MODERATE_LATENCY.pixelName,
            NETP_REPORT_GOOD_LATENCY.pixelName,
            NETP_REPORT_EXCELLENT_LATENCY.pixelName,
        )
        private const val PARAM_OSABOVE15 = "os15Above"
        private const val PARAM_LOCATION = "location"
        private const val VALUE_LOCATION_CUSTOM = "custom"
        private const val VALUE_LOCATION_NEAREST = "nearest"
    }
}
