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
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.ToggleSelection
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class SubmitButtonViewState(
    val visible: Boolean,
    val enabled: Boolean,
    val duckAiIcon: Boolean,
)

@ContributesViewModel(ViewScope::class)
class SubmitButtonViewModel @Inject constructor(
    private val nativeInputStateProvider: NativeInputStateProvider,
) : ViewModel() {

    // Follows the active browser tab via [state] on the omnibar/contextual surfaces (where the host's
    // tab is the active tab). The edit surface uses a synthetic session id that [state] never selects,
    // so it passes that id here to read its own state via [stateForTab].
    fun viewState(editTabId: String?): Flow<SubmitButtonViewState> =
        (if (editTabId != null) nativeInputStateProvider.stateForTab(editTabId) else nativeInputStateProvider.state)
            .map { it.toSubmitButtonState() }
            .distinctUntilChanged()
}

// Mirrors the widget's former updateSendButtonVisibility/Icon: shown on the Duck.ai tab when there is
// something to send (or a stream to reflect); enabled while not streaming and within the attachment
// limit; the arrow points up on a Duck.ai page and right otherwise.
internal fun NativeInputState.toSubmitButtonState(): SubmitButtonViewState {
    val hasContent = isChatStreaming || hasText || hasAttachments
    return SubmitButtonViewState(
        visible = toggleSelection == ToggleSelection.DUCK_AI && hasContent,
        enabled = isChatStreaming || (submitEnabled && (hasText || hasAttachments) && !attachmentLimitExceeded),
        duckAiIcon = inputContext == InputContext.DUCK_AI || inputContext == InputContext.DUCK_AI_CONTEXTUAL,
    )
}
