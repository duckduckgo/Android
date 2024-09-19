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

package com.duckduckgo.experiments.impl.loadingbarexperiment

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.loadingbarexperiment.LoadingBarExperimentManager
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class DuckDuckGoLoadingBarExperimentManager @Inject constructor(
    private val loadingBarExperimentDataStore: LoadingBarExperimentDataStore,
    private val loadingBarExperimentFeature: LoadingBarExperimentFeature,
    private val uriLoadedPixelFeature: UriLoadedPixelFeature,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    @IsMainProcess isMainProcess: Boolean,
) : LoadingBarExperimentManager {

    private var cachedShouldSendUriLoadedPixel: Boolean = false
    private var cachedVariant: Boolean = false
    private var hasVariant: Boolean = false
    private var enabled: Boolean = false

    override val variant: Boolean
        get() = cachedVariant

    override val shouldSendUriLoadedPixel: Boolean
        get() = cachedShouldSendUriLoadedPixel

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                Timber.d("Loading bar experiment: Experimental variables initialized")
                loadToMemory()
            }
        }
    }

    override fun isExperimentEnabled(): Boolean {
        Timber.d("Loading bar experiment: Retrieving experiment status")
        return hasVariant && enabled
    }

    override suspend fun update() {
        Timber.d("Loading bar experiment: Experimental variables updated")
        loadToMemory()
    }

    private fun loadToMemory() {
        cachedVariant = loadingBarExperimentDataStore.variant
        hasVariant = loadingBarExperimentDataStore.hasVariant
        enabled = loadingBarExperimentFeature.self().isEnabled()
        cachedShouldSendUriLoadedPixel = uriLoadedPixelFeature.self().isEnabled()
    }
}
