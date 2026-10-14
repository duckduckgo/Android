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

import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.ToggleSelection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInputControlStateTest {

    private fun state(
        toggle: ToggleSelection = ToggleSelection.DUCK_AI,
        context: InputContext = InputContext.BROWSER,
        hasText: Boolean = false,
        hasAttachments: Boolean = false,
        attachmentLimitExceeded: Boolean = false,
        submitEnabled: Boolean = true,
        streaming: Boolean = false,
    ) = NativeInputState.zero().copy(
        toggleSelection = toggle,
        inputContext = context,
        hasText = hasText,
        hasAttachments = hasAttachments,
        attachmentLimitExceeded = attachmentLimitExceeded,
        submitEnabled = submitEnabled,
        isChatStreaming = streaming,
    )

    @Test
    fun whenSearchTabThenSubmitHidden() {
        assertFalse(state(toggle = ToggleSelection.SEARCH, hasText = true).toSubmitButtonState().visible)
    }

    @Test
    fun whenDuckAiTabAndNoContentThenSubmitHidden() {
        assertFalse(state(hasText = false, hasAttachments = false).toSubmitButtonState().visible)
    }

    @Test
    fun whenDuckAiTabWithTextThenSubmitVisibleAndEnabled() {
        val s = state(hasText = true).toSubmitButtonState()
        assertTrue(s.visible)
        assertTrue(s.enabled)
    }

    @Test
    fun whenAttachmentLimitExceededThenSubmitDisabled() {
        assertFalse(state(hasText = true, attachmentLimitExceeded = true).toSubmitButtonState().enabled)
    }

    @Test
    fun whenSubmitDisabledByFrontEndThenDisabled() {
        assertFalse(state(hasText = true, submitEnabled = false).toSubmitButtonState().enabled)
    }

    @Test
    fun whenStreamingThenSubmitVisibleAndEnabled() {
        val s = state(hasText = false, streaming = true).toSubmitButtonState()
        assertTrue(s.visible)
        assertTrue(s.enabled)
    }

    @Test
    fun whenDuckAiPageContextThenArrowUpIconElseArrowRight() {
        assertTrue(state(context = InputContext.DUCK_AI, hasText = true).toSubmitButtonState().duckAiIcon)
        assertTrue(state(context = InputContext.DUCK_AI_CONTEXTUAL, hasText = true).toSubmitButtonState().duckAiIcon)
        assertFalse(state(context = InputContext.BROWSER, hasText = true).toSubmitButtonState().duckAiIcon)
    }

    @Test
    fun whenVoiceAvailableAndNotEditThenVoiceControlsShow() {
        assertTrue(shouldShowVoiceSearch(availableWhenBlank = true, isEditMode = false))
        assertTrue(shouldShowVoiceChat(availableWhenBlankAndNotStreaming = true, isEditMode = false))
    }

    @Test
    fun whenEditModeThenVoiceControlsHidden() {
        assertFalse(shouldShowVoiceSearch(availableWhenBlank = true, isEditMode = true))
        assertFalse(shouldShowVoiceChat(availableWhenBlankAndNotStreaming = true, isEditMode = true))
    }
}
