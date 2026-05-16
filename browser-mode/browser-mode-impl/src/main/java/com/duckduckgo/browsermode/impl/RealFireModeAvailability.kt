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

import androidx.lifecycle.LifecycleOwner
import androidx.webkit.WebViewFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = FireModeAvailability::class)
@ContributesMultibinding(AppScope::class, boundType = MainProcessLifecycleObserver::class)
class RealFireModeAvailability @Inject constructor(
    private val fireModeFeature: FireModeFeature,
    private val dispatchers: DispatcherProvider,
    @param:AppCoroutineScope private val appScope: CoroutineScope,
) : FireModeAvailability, MainProcessLifecycleObserver {

    @Volatile
    private var cachedAvailability: Boolean? = null

    override fun onCreate(owner: LifecycleOwner) {
        appScope.launch(dispatchers.io()) { computeAndCache() }
    }

    override fun isAvailable(): Boolean = cachedAvailability ?: computeAndCache()

    private fun computeAndCache(): Boolean {
        cachedAvailability?.let { return it }
        val value = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) &&
            fireModeFeature.fireTabs().isEnabled()
        cachedAvailability = value
        return value
    }
}
