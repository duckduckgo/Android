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
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.plugins.headers.CustomHeadersProvider.CustomHeadersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(scope = AppScope::class)
class AndroidFeaturesHeaderPlugin @Inject constructor(
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val androidFeaturesHeaderProvider: AndroidFeaturesHeaderProvider,
) : CustomHeadersPlugin {

    override fun getHeaders(url: String): Map<String, String> {
        if (androidBrowserConfigFeature.self().isEnabled() &&
            androidBrowserConfigFeature.featuresRequestHeader().isEnabled() &&
            duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)
        ) {
            androidFeaturesHeaderProvider.provide()?.let { headerValue ->
                return mapOf(X_DUCKDUCKGO_ANDROID_HEADER to headerValue)
            }
        }
        return emptyMap()
    }

    companion object {
        internal const val X_DUCKDUCKGO_ANDROID_HEADER = "x-duckduckgo-android"
    }
}
