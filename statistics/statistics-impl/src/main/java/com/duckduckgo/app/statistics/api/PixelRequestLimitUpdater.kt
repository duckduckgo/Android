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

package com.duckduckgo.app.statistics.api

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.lifecycle.PirProcessLifecycleObserver
import com.duckduckgo.app.lifecycle.VpnProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import javax.inject.Inject

/**
 * Owns the pixel Dispatcher and keeps its per-host limit in sync with remote config.
 * The limit is applied in the background on app start and whenever a new privacy config arrives, so building the pixel client never reads
 * feature flags and a new remote value takes effect without a restart.
 * The VPN and PIR processes send pixels too but never download the config, so they only read the limit when the process starts.
 */
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<MainProcessLifecycleObserver>(),
)
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<VpnProcessLifecycleObserver>(),
)
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<PirProcessLifecycleObserver>(),
)
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<PrivacyConfigCallbackPlugin>(),
)
@SingleIn(AppScope::class)
class PixelRequestLimitUpdater @Inject constructor(
    private val concurrencyLimitFeature: PixelConcurrencyLimitFeature,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver, VpnProcessLifecycleObserver, PirProcessLifecycleObserver, PrivacyConfigCallbackPlugin {

    val dispatcher = Dispatcher().apply { maxRequestsPerHost = PixelRequestLimit.DEFAULT_MAX_REQUESTS_PER_HOST }

    override fun onCreate(owner: LifecycleOwner) {
        updateLimit()
    }

    override fun onVpnProcessCreated() {
        updateLimit()
    }

    override fun onPirProcessCreated() {
        updateLimit()
    }

    override fun onPrivacyConfigDownloaded() {
        updateLimit()
    }

    private fun updateLimit() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            val settings = concurrencyLimitFeature.self().getSettings()
            dispatcher.maxRequestsPerHost = PixelRequestLimit.maxRequestsPerHost(settings)
        }
    }
}
