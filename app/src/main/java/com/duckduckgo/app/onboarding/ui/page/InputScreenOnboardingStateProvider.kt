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

package com.duckduckgo.app.onboarding.ui.page

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

interface InputScreenOnboardingStateProvider {
    val isEnabled: StateFlow<Boolean>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = InputScreenOnboardingStateProvider::class)
@ContributesMultibinding(AppScope::class, boundType = PrivacyConfigCallbackPlugin::class)
class InputScreenOnboardingStateProviderImpl @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
) : InputScreenOnboardingStateProvider, PrivacyConfigCallbackPlugin {

    private val _isEnabled = MutableStateFlow(false)
    override val isEnabled = _isEnabled.asStateFlow()

    init {
        cacheState()
    }

    override fun onPrivacyConfigDownloaded() {
        cacheState()
    }

    private fun cacheState() {
        coroutineScope.launch(context = dispatcherProvider.io()) {
            _isEnabled.value = androidBrowserConfigFeature.showInputScreenOnboarding().isEnabled()
        }
    }
}
