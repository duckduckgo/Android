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
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.ToggleSelection
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.terms.DuckAiTermsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

data class SubmitButtonViewState(
    val visible: Boolean,
    val enabled: Boolean,
    val duckAiIcon: Boolean,
    /** Show the labelled "Ask" button, whose tap is how the user accepts the Duck.ai terms. */
    val askLabel: Boolean = false,
)

@ContributesViewModel(ViewScope::class)
class SubmitButtonViewModel @Inject constructor(
    private val nativeInputStateProvider: NativeInputStateProvider,
    private val termsRepository: DuckAiTermsRepository,
    private val duckChatFeature: DuckChatFeature,
    private val browserMode: BrowserMode,
) : ViewModel() {

    // The labelled button replaces the arrow while the terms are unaccepted. The edit surface never shows it.
    private val termsRequired: Flow<Boolean> = combine(
        duckChatFeature.nativeToSConsent().enabled(),
        termsRepository.observeTermsAccepted(browserMode),
    ) { enabled, accepted -> enabled && !accepted }

    // Follows the active browser tab via [state] on the omnibar/contextual surfaces (where the host's
    // tab is the active tab). The edit surface uses a synthetic session id that [state] never selects,
    // so it passes that id here to read its own state via [stateForTab].
    fun viewState(editTabId: String?): Flow<SubmitButtonViewState> {
        val state = if (editTabId != null) nativeInputStateProvider.stateForTab(editTabId) else nativeInputStateProvider.state
        val terms = if (editTabId != null) flowOf(false) else termsRequired
        return combine(state, terms) { inputState, termsRequired ->
            inputState.toSubmitButtonState().let { it.copy(askLabel = termsRequired && it.visible) }
        }.distinctUntilChanged()
    }
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
