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

import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.trafficquality.remote.AndroidFeaturesHeaderProvider
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.utils.plugins.headers.CustomHeadersProvider.CustomHeadersPlugin
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import javax.inject.Inject

@ContributesIntoSet(scope = AppScope::class)
class AndroidFeaturesHeaderPlugin @Inject constructor(
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val androidFeaturesHeaderProvider: AndroidFeaturesHeaderProvider,
) : CustomHeadersPlugin {

    override fun getHeaders(url: String, browserMode: BrowserMode): Map<String, String> {
        if (!isFeatureEnabled() || browserMode == BrowserMode.FIRE || !duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) {
            return emptyMap()
        }
        return mapOf(X_DUCKDUCKGO_ANDROID_HEADER to androidFeaturesHeaderProvider.provide())
    }

    private fun isFeatureEnabled(): Boolean {
        return androidBrowserConfigFeature.self().isEnabled() &&
            androidBrowserConfigFeature.featuresRequestHeader().isEnabled()
    }

    companion object {
        internal const val X_DUCKDUCKGO_ANDROID_HEADER = "x-duckduckgo-android"
    }
}
