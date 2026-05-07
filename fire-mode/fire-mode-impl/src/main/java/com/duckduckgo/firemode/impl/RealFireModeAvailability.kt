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

package com.duckduckgo.firemode.impl

import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.MultiProfile
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.firemode.api.FireModeAvailability
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

    private var multiProfileSupported: Boolean? = null

    override suspend fun isAvailable(): Boolean = withContext(dispatchers.io()) {
        if (!fireModeFeature.fireTabs().isEnabled()) return@withContext false
        multiProfileSupported ?: webViewCapabilityChecker.isSupported(MultiProfile).also {
            multiProfileSupported = it
        }
    }
}
