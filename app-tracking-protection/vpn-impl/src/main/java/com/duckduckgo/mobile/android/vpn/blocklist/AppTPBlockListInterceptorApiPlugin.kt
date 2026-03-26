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

package com.duckduckgo.mobile.android.vpn.blocklist

import com.duckduckgo.app.global.api.ApiInterceptorPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.mobile.android.vpn.feature.AppTpRemoteFeatures.Cohorts.CONTROL
import com.duckduckgo.mobile.android.vpn.feature.AppTpRemoteFeatures.Cohorts.TREATMENT
import com.duckduckgo.mobile.android.vpn.feature.activeAppTpTdsFlag
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixelNames
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.runBlocking
import logcat.logcat
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response
import retrofit2.Invocation
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ApiInterceptorPlugin::class,
)
class AppTPBlockListInterceptorApiPlugin @Inject constructor(
    private val inventory: FeatureTogglesInventory,
    private val moshi: Moshi,
    private val pixel: DeviceShieldPixels,
    private val appTrackingProtection: AppTrackingProtection,
) : Interceptor, ApiInterceptorPlugin {

    private val jsonAdapter: JsonAdapter<Map<String, String>> by lazy {
        moshi.adapter(Types.newParameterizedType(Map::class.java, String::class.java, String::class.java))
    }
    override fun intercept(chain: Chain): Response {
        val request = chain.request().newBuilder()

        val tdsRequired = chain.request().tag(Invocation::class.java)
            ?.method()
            ?.isAnnotationPresent(AppTPTdsRequired::class.java) == true

        val shouldInterceptRequest = tdsRequired && runBlocking {
            appTrackingProtection.isEnabled()
        }

        return if (shouldInterceptRequest) {
            logcat { "[AppTP]: Intercepted AppTP TDS Request: ${chain.request()}" }
            val activeExperiment = runBlocking {
                inventory.activeAppTpTdsFlag()?.also {
                    it.enroll()
                }
            }
            logcat { "[AppTP]: Active experiment: ${activeExperiment?.featureName()}" }
            logcat { "[AppTP]: Cohort: ${runBlocking { activeExperiment?.getCohort() }}" }

            activeExperiment?.let {
                val config = activeExperiment.getSettings()?.let {
                    runCatching {
                        jsonAdapter.fromJson(it)
                    }.getOrDefault(emptyMap())
                } ?: emptyMap()
                val path = when {
                    runBlocking { activeExperiment.isEnrolledAndEnabled(TREATMENT) } -> config["treatmentUrl"]
                    runBlocking { activeExperiment.isEnrolledAndEnabled(CONTROL) } -> config["controlUrl"]
                    else -> config["nextUrl"]
                } ?: return chain.proceed(request.build())
                val newURL = "$APPTP_TDS_BASE_URL$path"
                logcat { "[AppTP]: Rewrote TDS request URL to $newURL" }
                chain.proceed(request.url(newURL).build()).also { response ->
                    if (!response.isSuccessful) {
                        pixel.appTPBlocklistExperimentDownloadFailure(
                            response.code,
                            activeExperiment.featureName().name,
                            runBlocking { activeExperiment.getCohort() }?.name.toString(),
                        )
                    }
                }
            } ?: chain.proceed(request.build())
        } else {
            chain.proceed(request.build())
        }
    }

    override fun getInterceptor(): Interceptor {
        return this
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelParamRemovalPlugin::class,
)
object MetricPixelRemovalInterceptor : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            DeviceShieldPixelNames.ATP_TDS_EXPERIMENT_DOWNLOAD_FAILED.pixelName to PixelParameter.removeAll(),
        )
    }
}
