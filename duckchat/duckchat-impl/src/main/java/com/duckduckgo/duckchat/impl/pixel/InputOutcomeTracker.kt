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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Follows one Duck.ai input session at a time (only one input is focused on screen) and reports how it ended with
 * the `m_aichat_input_outcome` pixel. A session ends with the first outcome, or as abandoned when it closes without one.
 */
interface InputOutcomeTracker {
    fun sessionStarted(surface: DuckChatPixelSurface)

    /** The ToS disclaimer was on screen during the current session. */
    fun tosDisclaimerDisplayed()

    fun promptSubmitted(submitMethod: InputSubmitMethod)

    fun voiceStarted()

    fun suggestedPrompt()

    /** The input closed, collapsed, switched to Search or went to the background. */
    fun sessionEnded()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealInputOutcomeTracker @Inject constructor(
    private val duckChatPixels: DuckChatPixels,
    private val duckChatFeature: DuckChatFeature,
) : InputOutcomeTracker {

    private class Session(val surface: DuckChatPixelSurface) {
        var tosDisclaimerShown = false
    }

    private var session: Session? = null

    @Synchronized
    override fun sessionStarted(surface: DuckChatPixelSurface) {
        sessionEnded()
        session = Session(surface)
    }

    @Synchronized
    override fun tosDisclaimerDisplayed() {
        session?.tosDisclaimerShown = true
    }

    override fun promptSubmitted(submitMethod: InputSubmitMethod) = finish(InputOutcome.PROMPT_SUBMITTED, submitMethod)

    override fun voiceStarted() = finish(InputOutcome.VOICE_STARTED, submitMethod = null)

    override fun suggestedPrompt() = finish(InputOutcome.SUGGESTED_PROMPT, InputSubmitMethod.OTHER)

    override fun sessionEnded() = finish(InputOutcome.ABANDONED, submitMethod = null)

    @Synchronized
    private fun finish(
        outcome: InputOutcome,
        submitMethod: InputSubmitMethod?,
    ) {
        val finished = session ?: return
        session = null
        if (!duckChatFeature.nativeToSConsent().isEnabled()) return
        duckChatPixels.fireInputOutcome(finished.surface, outcome, submitMethod, finished.tosDisclaimerShown)
    }
}
