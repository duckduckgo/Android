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
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.browser.ui.omnibar.OmnibarType
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

/**
 * Observes app lifecycle to ensure that if the Split Omnibar feature is disabled via remote config,
 * and the user had selected the Split Omnibar, we revert to Single Top Omnibar.
 * If the feature is re-enabled, we restore the user's choice.
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class SplitOmnibarFallbackObserver @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val browserFeatures: AndroidBrowserConfigFeature,
) : MainProcessLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        if (settingsDataStore.omnibarType == OmnibarType.SPLIT &&
            (!browserFeatures.useUnifiedOmnibarLayout().isEnabled() || !browserFeatures.splitOmnibar().isEnabled())
        ) {
            settingsDataStore.isSplitOmnibarSelected = true
            settingsDataStore.omnibarType = OmnibarType.SINGLE_TOP
        } else if (settingsDataStore.isSplitOmnibarSelected &&
            browserFeatures.useUnifiedOmnibarLayout().isEnabled() &&
            browserFeatures.splitOmnibar().isEnabled()
        ) {
            // Restore user's choice if the feature is re-enabled
            settingsDataStore.omnibarType = OmnibarType.SPLIT
            settingsDataStore.isSplitOmnibarSelected = false
        }
    }
}
