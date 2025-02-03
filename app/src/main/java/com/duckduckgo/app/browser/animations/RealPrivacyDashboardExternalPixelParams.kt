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

package com.duckduckgo.app.browser.animations

import com.duckduckgo.app.browser.apppersonality.AppPersonalityFeature
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.dashboard.api.PrivacyDashboardExternalPixelParams
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPrivacyDashboardExternalPixelParams @Inject constructor(
    private val appPersonalityFeature: AppPersonalityFeature,
) : PrivacyDashboardExternalPixelParams {

    private val pixelParams = mutableMapOf<String, String>()

    @Synchronized
    override fun getPixelParams(): Map<String, String> {
        if (pixelParams.isEmpty()) {
            val key = if (appPersonalityFeature.self().isEnabled() && appPersonalityFeature.trackersBlockedAnimation().isEnabled()) {
                PixelParameter.AFTER_BURST_ANIMATION
            } else {
                PixelParameter.AFTER_CIRCLES_ANIMATION
            }
            setPixelParams(key, "false")
        }
        return pixelParams.toMap()
    }

    @Synchronized
    override fun setPixelParams(key: String, value: String) {
        pixelParams[key] = value
    }

    @Synchronized
    override fun clearPixelParams() {
        pixelParams.clear()
    }
}
