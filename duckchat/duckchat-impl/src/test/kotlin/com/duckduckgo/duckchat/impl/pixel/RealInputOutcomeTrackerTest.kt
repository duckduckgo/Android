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

package com.duckduckgo.duckchat.impl.pixel

import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class RealInputOutcomeTrackerTest {

    private val pixels: DuckChatPixels = mock()
    private val feature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private lateinit var testee: RealInputOutcomeTracker

    @Before
    fun setUp() {
        feature.nativeToSConsent().setRawStoredState(Toggle.State(enable = true))
        testee = RealInputOutcomeTracker(pixels, feature)
    }

    @Test
    fun whenPromptSubmittedThenFiresWithMethod() {
        testee.sessionStarted(DuckChatPixelSurface.DUCK_AI)
        testee.promptSubmitted(InputSubmitMethod.ENTER)

        verify(pixels).fireInputOutcome(DuckChatPixelSurface.DUCK_AI, InputOutcome.PROMPT_SUBMITTED, InputSubmitMethod.ENTER, false)
    }

    @Test
    fun whenVoiceStartedThenFiresWithoutMethod() {
        testee.sessionStarted(DuckChatPixelSurface.ADDRESS_BAR)
        testee.voiceStarted()

        verify(pixels).fireInputOutcome(DuckChatPixelSurface.ADDRESS_BAR, InputOutcome.VOICE_STARTED, null, false)
    }

    @Test
    fun whenSuggestedPromptThenMethodIsOther() {
        testee.sessionStarted(DuckChatPixelSurface.CONTEXTUAL_CHAT)
        testee.suggestedPrompt()

        verify(pixels).fireInputOutcome(DuckChatPixelSurface.CONTEXTUAL_CHAT, InputOutcome.SUGGESTED_PROMPT, InputSubmitMethod.OTHER, false)
    }

    @Test
    fun whenSessionEndsWithoutOutcomeThenAbandoned() {
        testee.sessionStarted(DuckChatPixelSurface.DUCK_AI)
        testee.sessionEnded()

        verify(pixels).fireInputOutcome(DuckChatPixelSurface.DUCK_AI, InputOutcome.ABANDONED, null, false)
    }

    @Test
    fun whenDisclaimerDisplayedThenFlagIsTrue() {
        testee.sessionStarted(DuckChatPixelSurface.DUCK_AI)
        testee.tosDisclaimerDisplayed()
        testee.promptSubmitted(InputSubmitMethod.BUTTON)

        verify(pixels).fireInputOutcome(DuckChatPixelSurface.DUCK_AI, InputOutcome.PROMPT_SUBMITTED, InputSubmitMethod.BUTTON, true)
    }

    @Test
    fun whenOutcomeFiredThenLaterEndDoesNotFireAgain() {
        testee.sessionStarted(DuckChatPixelSurface.DUCK_AI)
        testee.promptSubmitted(InputSubmitMethod.BUTTON)
        testee.sessionEnded()

        verify(pixels).fireInputOutcome(DuckChatPixelSurface.DUCK_AI, InputOutcome.PROMPT_SUBMITTED, InputSubmitMethod.BUTTON, false)
        verify(pixels, never()).fireInputOutcome(DuckChatPixelSurface.DUCK_AI, InputOutcome.ABANDONED, null, false)
    }

    @Test
    fun whenNoSessionThenNothingFires() {
        testee.promptSubmitted(InputSubmitMethod.BUTTON)
        testee.sessionEnded()

        verifyNoInteractions(pixels)
    }

    @Test
    fun whenNewSessionStartsThenPreviousIsAbandoned() {
        testee.sessionStarted(DuckChatPixelSurface.ADDRESS_BAR)
        testee.sessionStarted(DuckChatPixelSurface.DUCK_AI)

        verify(pixels).fireInputOutcome(DuckChatPixelSurface.ADDRESS_BAR, InputOutcome.ABANDONED, null, false)
    }

    @Test
    fun whenToSConsentFlagOffThenNothingFires() {
        feature.nativeToSConsent().setRawStoredState(Toggle.State(enable = false))
        testee.sessionStarted(DuckChatPixelSurface.DUCK_AI)
        testee.promptSubmitted(InputSubmitMethod.BUTTON)

        verifyNoInteractions(pixels)
    }
}
