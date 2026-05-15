/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.browsermode.impl

import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.MultiProfile
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealFireModeAvailability @Inject constructor(
    private val fireModeFeature: FireModeFeature,
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
    private val dispatchers: DispatcherProvider,
) : FireModeAvailability {

    @Volatile
    private var cachedAvailability: Boolean? = null

    override suspend fun isAvailable(): Boolean = withContext(dispatchers.io()) {
        cachedAvailability ?: (
            fireModeFeature.fireTabs().isEnabled() &&
                webViewCapabilityChecker.isSupported(MultiProfile)
            ).also { cachedAvailability = it }
    }
}
