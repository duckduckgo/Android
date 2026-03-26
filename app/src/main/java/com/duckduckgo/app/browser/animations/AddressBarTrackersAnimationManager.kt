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
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    /**
     * Determines if the tracker animation should be shown based on the current URL
     * and the URL where the animation was last shown.
     *
     * Uses Public Suffix List (PSL) and eTLD+1 for accurate domain matching, handling
     * user-controlled subdomains like *.github.io as separate sites.
     *
     * @param currentUrl The current page URL (null if no site loaded)
     * @param lastAnimatedUrl The URL where animation was last shown (null if never shown)
     * @return true if animation should be shown, false otherwise
     */
    fun shouldShowAnimation(currentUrl: String?, lastAnimatedUrl: String?): Boolean
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

    override fun shouldShowAnimation(currentUrl: String?, lastAnimatedUrl: String?): Boolean {
        if (currentUrl == null) {
            return false
        }

        val currentHost = currentUrl.toHttpUrlOrNull()?.host ?: return false
        val currentETldPlusOne = currentHost.toTldPlusOne()

        if (lastAnimatedUrl == null) {
            return true
        }

        val lastHost = lastAnimatedUrl.toHttpUrlOrNull()?.host ?: return true
        val lastETldPlusOne = lastHost.toTldPlusOne()

        return currentETldPlusOne != lastETldPlusOne
    }
}
