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

package com.duckduckgo.app.browser.omnibar

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.browser.ui.omnibar.OmnibarType
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
open class OmnibarFeatureRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val browserFeatures: AndroidBrowserConfigFeature,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver {
    private var isSplitOmnibarFlagEnabled: Boolean = false

    var isUnifiedOmnibarFlagEnabled: Boolean = false
        private set

    val isSplitOmnibarEnabled: Boolean
        get() = isSplitOmnibarAvailable && settingsDataStore.omnibarType == OmnibarType.SPLIT

    val isSplitOmnibarAvailable: Boolean
        get() = isSplitOmnibarFlagEnabled && isUnifiedOmnibarFlagEnabled

    override fun onStart(owner: LifecycleOwner) {
        updateFeatureFlags()
    }

    fun updateFeatureFlags() {
        coroutineScope.launch(dispatcherProvider.io()) {
            isUnifiedOmnibarFlagEnabled = browserFeatures.useUnifiedOmnibarLayout().isEnabled()
            isSplitOmnibarFlagEnabled = browserFeatures.splitOmnibar().isEnabled()

            resetOmnibarTypeIfNecessary()
        }
    }

    private fun resetOmnibarTypeIfNecessary() {
        if (settingsDataStore.omnibarType == OmnibarType.SPLIT && !isSplitOmnibarAvailable) {
            settingsDataStore.isSplitOmnibarSelected = true
            settingsDataStore.omnibarType = OmnibarType.SINGLE_TOP
        } else if (settingsDataStore.isSplitOmnibarSelected && isSplitOmnibarAvailable) {
            // Restore user's choice if the feature is re-enabled
            settingsDataStore.omnibarType = OmnibarType.SPLIT
            settingsDataStore.isSplitOmnibarSelected = false
        }
    }
}
