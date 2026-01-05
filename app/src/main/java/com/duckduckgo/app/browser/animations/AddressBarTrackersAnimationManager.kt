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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface AddressBarTrackersAnimationManager {
    /**
     * Eagerly fetches and caches the feature state from the feature toggle.
     * This should be called proactively to ensure the state is available without delay.
     */
    suspend fun fetchFeatureState()

    /**
     * Returns the cached feature state if available, otherwise fetches and caches it.
     * @return true if the feature is enabled, false otherwise
     */
    suspend fun isFeatureEnabled(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, AddressBarTrackersAnimationManager::class)
class RealAddressBarTrackersAnimationManager @Inject constructor(
    private val addressBarTrackersAnimationFeatureToggle: AddressBarTrackersAnimationFeatureToggle,
    private val dispatcherProvider: DispatcherProvider,
) : AddressBarTrackersAnimationManager {

    private var cachedFeatureState: Boolean? = null

    override suspend fun fetchFeatureState() {
        withContext(dispatcherProvider.io()) {
            cachedFeatureState = addressBarTrackersAnimationFeatureToggle.feature().isEnabled()
        }
    }

    override suspend fun isFeatureEnabled(): Boolean = withContext(dispatcherProvider.io()) {
        cachedFeatureState ?: addressBarTrackersAnimationFeatureToggle.feature().isEnabled().also {
            cachedFeatureState = it
        }
    }
}
