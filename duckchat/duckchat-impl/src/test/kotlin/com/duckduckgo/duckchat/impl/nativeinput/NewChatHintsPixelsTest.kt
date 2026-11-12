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

package com.duckduckgo.duckchat.impl.nativeinput

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.voice.VoiceSessionStateManager
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class NewChatHintsPixelsTest {
    @get:Rule val coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java).apply {
        newChatHints().setRawStoredState(Toggle.State(enable = true))
    }
    private val duckChatPixels: DuckChatPixels = mock()
    private val selectedTab = MutableStateFlow<TabEntity?>(TabEntity("tab"))
    private val tabRepository: TabRepository = mock {
        on { flowSelectedTab } doReturn selectedTab
        on { flowTabs } doReturn MutableStateFlow(listOf(TabEntity("tab", url = NEW_CHAT_URL)))
    }
    private val repositories: BrowserModeDataProvider<TabRepository> = mock {
        on { forMode(BrowserMode.REGULAR) } doReturn tabRepository
    }
    private val mode: BrowserModeStateHolder = mock {
        on { currentMode } doReturn MutableStateFlow(BrowserMode.REGULAR)
    }
    private val chatStateStore = ChatStateStore()
    private val stateStore = RealNativeInputStateStore(lazy { repositories }, mode, mock(), chatStateStore)
    private val duckChat: DuckChatInternal = mock {
        on { isDuckChatUrl(any()) } doReturn true
    }
    private val voiceSessions: VoiceSessionStateManager = mock { on { activeVoiceSessions } doReturn MutableStateFlow(emptySet()) }
    private val chatStore: DuckAiChatStore = mock()
    private val detector =
        NewChatDetector(stateStore, chatStateStore, voiceSessions, tabRepository, duckChat, chatStore, coroutineRule.testDispatcherProvider)
    private val testee = NewChatHintsPixels(duckChatPixels, feature, coroutineRule.testDispatcherProvider)

    @Before
    fun setUp() {
        stateStore.publish("tab", NativeInputState.zero().copy(inputContext = InputContext.DUCK_AI))
        chatStateStore.update("tab", ChatState.READY)
    }

    @Test
    fun whenANewChatShowsThenItIsCountedOnceWithTheFlag() = runTest {
        track()
        stateStore.update("tab") { it.copy(hasText = true) }
        runCurrent()

        verify(duckChatPixels, times(1)).fireNewChatShown(hasNewChatHints = true)
    }

    @Test
    fun whenTheFlagIsOffThenTheNewChatIsStillCounted() = runTest {
        feature.newChatHints().setRawStoredState(Toggle.State(enable = false))

        track()

        verify(duckChatPixels).fireNewChatShown(hasNewChatHints = false)
    }

    @Test
    fun whenTheInputIsNotReadyThenTheNewChatIsNotCounted() = runTest {
        chatStateStore.update("tab", ChatState.LOADING)

        track()

        verify(duckChatPixels, never()).fireNewChatShown(any())
    }

    @Test
    fun whenAnotherTabIsSelectedThenItsNewChatIsNotCounted() = runTest {
        selectedTab.value = TabEntity("other")

        track()

        verify(duckChatPixels, never()).fireNewChatShown(any())
    }

    @Test
    fun whenTheUserLeavesAndStartsAnotherNewChatThenBothAreCounted() = runTest {
        track()
        leaveAndStartAnotherNewChat()

        verify(duckChatPixels, times(2)).fireNewChatShown(hasNewChatHints = true)
    }

    @Test
    fun whenTheUserLeftWhileTrackingWasPausedThenTheNextNewChatIsCounted() = runTest {
        track().cancel()
        stateStore.update("tab") { it.copy(chatId = "chat") }

        track()
        stateStore.update("tab") { it.copy(chatId = null) }
        runCurrent()

        verify(duckChatPixels, times(2)).fireNewChatShown(hasNewChatHints = true)
    }

    @Test
    fun whenTrackingRestartsOnTheSameNewChatThenItIsNotCountedAgain() = runTest {
        track()
        track()

        verify(duckChatPixels, times(1)).fireNewChatShown(hasNewChatHints = true)
    }

    @Test
    fun whenHintsAreShownAgainThenOnlyTheFirstRowIsReported() {
        testee.onHintsShown("tab", listOf(NewChatHint.PHOTO, NewChatHint.DOCUMENTS))
        testee.onHintsShown("tab", listOf(NewChatHint.PHOTO, NewChatHint.DOCUMENTS, NewChatHint.IMAGE_GENERATION))

        verify(duckChatPixels, times(1)).fireNewChatHintsShown(any())
        verify(duckChatPixels).fireNewChatHintsShown(listOf(NewChatHint.PHOTO, NewChatHint.DOCUMENTS))
    }

    @Test
    fun whenTheUserLeavesThenTheNextNewChatReportsItsHintsAgain() = runTest {
        track()
        testee.onHintsShown("tab", listOf(NewChatHint.PHOTO))
        leaveAndStartAnotherNewChat()
        testee.onHintsShown("tab", listOf(NewChatHint.PHOTO))

        verify(duckChatPixels, times(2)).fireNewChatHintsShown(listOf(NewChatHint.PHOTO))
    }

    @Test
    fun whenHintsShowBeforeTheNewChatIsTrackedThenItIsCountedOnceWithTheFlagOn() = runTest {
        testee.onHintsShown("tab", listOf(NewChatHint.PHOTO))
        track()

        verify(duckChatPixels, times(1)).fireNewChatShown(true)
        verify(duckChatPixels).fireNewChatHintsShown(listOf(NewChatHint.PHOTO))
    }

    @Test
    fun whenAPromptIsSentWithoutATapThenTheFirstPromptHasNoHint() = runTest {
        feature.newChatHints().setRawStoredState(Toggle.State(enable = false))
        track()

        testee.onPromptSubmitted("tab", isNewChat = true, hasImageAttachment = true, hasFileAttachment = false, selectedTool = "image_generation")

        verify(duckChatPixels).fireNewChatFirstPromptSent(
            hasNewChatHints = false,
            hintTapped = null,
            hasImageAttachment = true,
            hasFileAttachment = false,
            selectedTool = "image_generation",
        )
    }

    @Test
    fun whenASecondPromptIsSentThenOnlyTheFirstIsReported() = runTest {
        track()

        repeat(2) { submitNewChatPrompt() }

        verify(duckChatPixels, times(1)).fireNewChatFirstPromptSent(any(), anyOrNull(), any(), any(), any())
    }

    @Test
    fun whenAPromptIsSentOutsideANewChatThenNothingIsReportedAndTheNewChatIsDropped() = runTest {
        track()

        testee.onPromptSubmitted("tab", isNewChat = false, hasImageAttachment = false, hasFileAttachment = false, selectedTool = "none")
        submitNewChatPrompt()

        verify(duckChatPixels, never()).fireNewChatFirstPromptSent(any(), anyOrNull(), any(), any(), any())
    }

    @Test
    fun whenAPromptIsSentInAnUncountedNewChatThenNothingIsReported() {
        submitNewChatPrompt()

        verify(duckChatPixels, never()).fireNewChatFirstPromptSent(any(), anyOrNull(), any(), any(), any())
    }

    @Test
    fun whenTheUserLeavesAfterATapThenTheNextNewChatForgetsIt() = runTest {
        track()
        testee.onHintTapped("tab", NewChatHint.PHOTO, position = 1)
        leaveAndStartAnotherNewChat()

        submitNewChatPrompt()

        verify(duckChatPixels).fireNewChatFirstPromptSent(
            hasNewChatHints = true,
            hintTapped = null,
            hasImageAttachment = false,
            hasFileAttachment = false,
            selectedTool = "none",
        )
    }

    @Test
    fun whenTheNewChatStillShowsAfterTheFirstPromptThenNothingIsReportedAgain() = runTest {
        track()
        testee.onHintsShown("tab", listOf(NewChatHint.PHOTO))
        submitNewChatPrompt()

        track()
        testee.onHintsShown("tab", listOf(NewChatHint.PHOTO, NewChatHint.DOCUMENTS))

        verify(duckChatPixels, times(1)).fireNewChatShown(true)
        verify(duckChatPixels, times(1)).fireNewChatHintsShown(any())
    }

    @Test
    fun whenTheUserLeavesAfterTheFirstPromptThenTheNextNewChatIsCountedAgain() = runTest {
        track()
        submitNewChatPrompt()

        leaveAndStartAnotherNewChat()

        verify(duckChatPixels, times(2)).fireNewChatShown(true)
    }

    private fun TestScope.track(): Job {
        val job = backgroundScope.launch { testee.track("tab", selectedTab.map { it?.tabId == "tab" }, detector.observe("tab")) }
        runCurrent()
        return job
    }

    private fun TestScope.leaveAndStartAnotherNewChat() {
        stateStore.update("tab") { it.copy(chatId = "chat") }
        runCurrent()
        stateStore.update("tab") { it.copy(chatId = null) }
        runCurrent()
    }

    private fun submitNewChatPrompt() {
        testee.onPromptSubmitted("tab", isNewChat = true, hasImageAttachment = false, hasFileAttachment = false, selectedTool = "none")
    }

    private companion object {
        const val NEW_CHAT_URL = "https://duck.ai/chat?duckai=5"
    }
}
