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
class SearchInNewTabViewModel @Inject constructor(
    duckAiFeatureState: DuckAiFeatureState,
    duckChatInternal: DuckChatInternal,
) : ViewModel() {

    /**
     * The search-in-new-tab icon is only useful when the toggle is hidden — i.e. the user
     * is in `SEARCH_ONLY` mode (Duck.ai disabled at the feature level, or Duck.ai enabled
     * but the input-screen toggle is off). In toggle mode the user can already reach search
     * via the toggle, so the icon is redundant.
     */
    val isModeAllowed: Flow<Boolean> = combine(
        duckAiFeatureState.showSettings,
        duckChatInternal.observeEnableDuckChatUserSetting(),
        duckChatInternal.observeInputScreenUserSettingEnabled(),
    ) { isFeatureEnabled, isUserEnabled, isInputScreenUserSettingEnabled ->
        !(isFeatureEnabled && isUserEnabled && isInputScreenUserSettingEnabled)
    }
}
