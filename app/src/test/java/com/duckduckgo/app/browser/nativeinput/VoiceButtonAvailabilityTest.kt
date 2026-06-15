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

package com.duckduckgo.app.browser.nativeinput

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceButtonAvailabilityTest {

    @Test
    fun whenDeviceVoiceSearchUnavailableThenVoiceSearchUnavailableEverywhere() {
        val searchTab = compute(deviceAvailable = false, isDuckAiTabSelected = false)
        val chatTab = compute(deviceAvailable = false, isDuckAiTabSelected = true)
        val activeDuckChat = compute(deviceAvailable = false, isOnActiveDuckChat = true)

        assertEquals(false, searchTab.voiceSearchAvailable)
        assertEquals(false, chatTab.voiceSearchAvailable)
        assertEquals(false, activeDuckChat.voiceSearchAvailable)
    }

    @Test
    fun whenOnSearchTabThenVoiceSearchGatedByDeviceOnlyAndVoiceChatUnavailable() {
        val withFlags = compute(isDuckAiTabSelected = false, duckAiVoiceSearch = true, voiceChatEntry = true)
        val withoutFlags = compute(isDuckAiTabSelected = false, duckAiVoiceSearch = false, voiceChatEntry = false)

        assertEquals(true, withFlags.voiceSearchAvailable)
        assertEquals(false, withFlags.voiceChatAvailable)
        assertEquals(true, withoutFlags.voiceSearchAvailable)
        assertEquals(false, withoutFlags.voiceChatAvailable)
    }

    @Test
    fun whenOnDuckAiTabAndBothFlagsEnabledThenBothControlsAvailable() {
        val state = compute(isDuckAiTabSelected = true, duckAiVoiceSearch = true, voiceChatEntry = true)

        assertEquals(true, state.voiceSearchAvailable)
        assertEquals(true, state.voiceChatAvailable)
    }

    @Test
    fun whenOnDuckAiTabAndOnlyVoiceChatFlagEnabledThenOnlyVoiceChatAvailable() {
        val state = compute(isDuckAiTabSelected = true, duckAiVoiceSearch = false, voiceChatEntry = true)

        assertEquals(false, state.voiceSearchAvailable)
        assertEquals(true, state.voiceChatAvailable)
    }

    @Test
    fun whenOnDuckAiTabAndOnlyVoiceSearchFlagEnabledThenOnlyVoiceSearchAvailable() {
        val state = compute(isDuckAiTabSelected = true, duckAiVoiceSearch = true, voiceChatEntry = false)

        assertEquals(true, state.voiceSearchAvailable)
        assertEquals(false, state.voiceChatAvailable)
    }

    @Test
    fun whenOnDuckAiTabAndBothFlagsDisabledThenNeitherAvailable() {
        val state = compute(isDuckAiTabSelected = true, duckAiVoiceSearch = false, voiceChatEntry = false)

        assertEquals(false, state.voiceSearchAvailable)
        assertEquals(false, state.voiceChatAvailable)
    }

    @Test
    fun whenOnActiveDuckChatThenVoiceChatSuppressedRegardlessOfFlag() {
        val withFlag = compute(isOnActiveDuckChat = true, voiceChatEntry = true)
        val withoutFlag = compute(isOnActiveDuckChat = true, voiceChatEntry = false)

        assertEquals(false, withFlag.voiceChatAvailable)
        assertEquals(false, withoutFlag.voiceChatAvailable)
    }

    @Test
    fun whenOnActiveDuckChatThenVoiceSearchGatedByDuckAiFlag() {
        val enabled = compute(isOnActiveDuckChat = true, duckAiVoiceSearch = true)
        val disabled = compute(isOnActiveDuckChat = true, duckAiVoiceSearch = false)

        assertEquals(true, enabled.voiceSearchAvailable)
        assertEquals(false, disabled.voiceSearchAvailable)
    }

    @Test
    fun whenOnActiveDuckChatThenTabSelectionIsIgnored() {
        val searchTabSelected = compute(isOnActiveDuckChat = true, isDuckAiTabSelected = false, duckAiVoiceSearch = true)
        val chatTabSelected = compute(isOnActiveDuckChat = true, isDuckAiTabSelected = true, duckAiVoiceSearch = true)

        assertEquals(searchTabSelected, chatTabSelected)
    }

    private fun compute(
        isOnActiveDuckChat: Boolean = false,
        deviceAvailable: Boolean = true,
        duckAiVoiceSearch: Boolean = true,
        voiceChatEntry: Boolean = true,
        isDuckAiTabSelected: Boolean = false,
    ): VoiceButtonAvailability = computeVoiceButtonAvailability(
        isOnActiveDuckChat = isOnActiveDuckChat,
        isVoiceSearchDeviceAvailable = deviceAvailable,
        isVoiceSearchDuckAiEnabled = duckAiVoiceSearch,
        isVoiceChatEntryEnabled = voiceChatEntry,
        isDuckAiTabSelected = isDuckAiTabSelected,
    )
}
