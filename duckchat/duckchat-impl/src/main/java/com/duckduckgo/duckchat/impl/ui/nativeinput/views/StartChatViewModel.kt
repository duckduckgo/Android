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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.impl.DuckChatInternal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@ContributesViewModel(ViewScope::class)
class StartChatViewModel @Inject constructor(
    duckAiFeatureState: DuckAiFeatureState,
    private val duckChatInternal: DuckChatInternal,
) : ViewModel() {

    /**
     * Show the start-chat icon only when Duck.ai is available (feature enabled +
     * user setting on) but the input-screen toggle is off — i.e. the user has Duck.ai
     * but is in `SEARCH_ONLY` mode. Mapping `inputMode == SEARCH_ONLY` directly would
     * also match the case where Duck.ai is entirely disabled, which would let the icon
     * navigate to a Duck.ai URL the user has opted out of.
     */
    val isVisible: Flow<Boolean> = combine(
        duckAiFeatureState.showSettings,
        duckChatInternal.observeEnableDuckChatUserSetting(),
        duckChatInternal.observeInputScreenUserSettingEnabled(),
    ) { isFeatureEnabled, isUserEnabled, isInputScreenUserSettingEnabled ->
        isFeatureEnabled && isUserEnabled && !isInputScreenUserSettingEnabled
    }

    fun openNewChat() {
        duckChatInternal.openNewDuckChatSession()
    }
}
