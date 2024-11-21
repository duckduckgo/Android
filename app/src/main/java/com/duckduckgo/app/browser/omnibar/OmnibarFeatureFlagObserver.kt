/*
 * Copyright (c) 2019 DuckDuckGo
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

import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
@SingleInstanceIn(AppScope::class)
class OmnibarFeatureFlagObserver @Inject constructor(
    private val changeOmnibarPositionFeature: ChangeOmnibarPositionFeature,
    private val settingsDataStore: SettingsDataStore,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : PrivacyConfigCallbackPlugin {
    override fun onPrivacyConfigDownloaded() {
        appCoroutineScope.launch(dispatchers.io()) {
            // If the feature is not enabled, set the omnibar position to top in case it was set to bottom.
            if (!changeOmnibarPositionFeature.self().isEnabled()) {
                settingsDataStore.omnibarPosition = OmnibarPosition.TOP
            }
        }
    }
}
