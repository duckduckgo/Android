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

package com.duckduckgo.duckchat.impl.attention

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.attention.ResponseAttentionEvent.LeftWhileGenerating
import com.duckduckgo.duckchat.impl.attention.ResponseAttentionEvent.Returned
import com.duckduckgo.duckchat.impl.attention.ResponseAttentionEvent.StatusWhileAway
import com.duckduckgo.duckchat.impl.attention.ResponseAttentionEvent.TrackingAborted
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DuckAiResponseAttentionTrackerImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val duckChat: DuckChat = mock()
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val duckChatFeatureRepository: DuckChatFeatureRepository = mock()
    private val listener = RecordingListener()

    private lateinit var testee: DuckAiResponseAttentionTrackerImpl

    @Before
    fun setup() = runTest {
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = true))
        duckChatFeature.nativeDuckAiSidebar().setRawStoredState(Toggle.State(enable = true))
        duckChatFeature.self().setRawStoredState(Toggle.State(enable = true))
        duckChatFeature.useNativeStorageChatData().setRawStoredState(Toggle.State(enable = true))
        whenever(duckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(true)
        whenever(duckChat.isDuckChatUrl(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as Uri).toString().startsWith(DUCK_AI_URL)
        }
        testee = createTestee(setOf(listener))
    }

    private fun createTestee(listeners: Set<ResponseAttentionListener>) = DuckAiResponseAttentionTrackerImpl(
        listeners = listeners,
        duckChat = duckChat,
        duckChatFeature = lazy { duckChatFeature },
        duckChatFeatureRepository = duckChatFeatureRepository,
        dispatchers = coroutineRule.testDispatcherProvider,
        appCoroutineScope = coroutineRule.testScope,
    ).also {
        it.onOpen(isFreshLaunch = true)
        it.onLaunchLandingResolved(tabId = null, url = null)
        idle()
    }

    private fun idle() = coroutineRule.testScope.testScheduler.advanceUntilIdle()

    private fun onChat(tabId: String, chatId: String?) {
        testee.onSelectedTabChanged(tabId = tabId, url = if (chatId == null) DUCK_AI_URL else "$DUCK_AI_URL?chatID=$chatId")
        idle()
    }

    private fun promptInChat(
        tabId: String = "t1",
        chatId: String? = "A",
        streamStarted: Boolean = true,
        browserMode: BrowserMode = BrowserMode.REGULAR,
    ) {
        onChat(tabId = tabId, chatId = chatId)
        testee.onNativePromptSubmitted(tabId = tabId, browserMode = browserMode)
        if (streamStarted) testee.onResponseState(tabId = tabId, status = "start_stream:new_prompt")
        idle()
    }

    private fun status(status: String, tabId: String = "t1") {
        testee.onResponseState(tabId = tabId, status = status)
        idle()
    }

    private fun switchToOtherSite(tabId: String = "t2") {
        testee.onSelectedTabChanged(tabId = tabId, url = OTHER_URL)
        idle()
    }

    private fun r(id: Long = 1L, tabId: String = "t1", chatId: String? = "A", mode: BrowserMode = BrowserMode.REGULAR) =
        TrackedResponse(id = id, tabId = tabId, chatId = chatId, browserMode = mode)

    private val regularA get() = r()
    private val fireB get() = r(id = 2L, tabId = "t2", chatId = "B", mode = BrowserMode.FIRE)

    private fun leaveRegularAAndFireB() {
        promptInChat(tabId = "t1", chatId = "A")
        promptInChat(tabId = "t2", chatId = "B", browserMode = BrowserMode.FIRE)
        switchToOtherSite(tabId = "t3")
        listener.events.clear()
    }

    // Returns the events as they were when onClearData returned, so tests can check delivery happened before then.
    private fun clear(vararg types: ClearableData): List<ResponseAttentionEvent> {
        var eventsOnReturn: List<ResponseAttentionEvent>? = null
        coroutineRule.testScope.launch {
            testee.onClearData(types.toSet())
            eventsOnReturn = listener.events.toList()
        }
        idle()
        return checkNotNull(eventsOnReturn)
    }

    private fun assertNothingTrackedWhen(disable: () -> Unit) {
        disable()
        promptInChat()
        status("loading")
        switchToOtherSite()

        assertEquals(emptyList<ResponseAttentionEvent>(), listener.events)
    }

    @Test
    fun `when app backgrounded while loading then LeftWhileGenerating`() {
        promptInChat()
        status("loading")

        testee.onClose()
        idle()

        assertEquals(listOf(LeftWhileGenerating(r())), listener.events)
    }

    @Test
    fun `when user returns while loading and leaves again then LeftWhileGenerating twice`() {
        promptInChat()
        status("loading")
        switchToOtherSite()
        onChat("t1", "A")
        switchToOtherSite()

        assertEquals(listOf(LeftWhileGenerating(r()), Returned(r()), LeftWhileGenerating(r())), listener.events)
    }

    @Test
    fun `when user returns while loading then later statuses are not reported`() {
        promptInChat()
        status("loading")
        switchToOtherSite()
        onChat("t1", "A")
        status("streaming")

        assertEquals(listOf(LeftWhileGenerating(r()), Returned(r())), listener.events)
    }

    @Test
    fun `when ready on screen then leaving emits nothing`() {
        promptInChat()
        status("ready")
        switchToOtherSite()

        assertEquals(emptyList<ResponseAttentionEvent>(), listener.events)
    }

    @Test
    fun `when idle ready arrives before the stream then the response is still generating`() {
        promptInChat(streamStarted = false)
        status("ready")
        switchToOtherSite()

        assertEquals(listOf(LeftWhileGenerating(r())), listener.events)
    }

    @Test
    fun `when stale ready arrives while away then it is not reported`() {
        promptInChat(streamStarted = false)
        switchToOtherSite()
        status("ready")

        assertEquals(listOf(LeftWhileGenerating(r())), listener.events)
    }

    @Test
    fun `when away then statuses are reported raw`() {
        promptInChat()
        switchToOtherSite()
        status("streaming")
        status("start_stream:auto_retry_last_prompt")
        status("error")
        status("ready")

        assertEquals(
            listOf(
                LeftWhileGenerating(r()),
                StatusWhileAway(r(), "streaming"),
                StatusWhileAway(r(), "start_stream:auto_retry_last_prompt"),
                StatusWhileAway(r(), "error"),
                StatusWhileAway(r(), "ready"),
            ),
            listener.events,
        )
    }

    @Test
    fun `when error on screen and the page retries then leaving emits LeftWhileGenerating`() {
        promptInChat()
        status("error")
        status("start_stream:auto_retry_last_prompt")
        switchToOtherSite()

        assertEquals(listOf(LeftWhileGenerating(r())), listener.events)
    }

    @Test
    fun `when returned after ready while away then a later leave emits nothing`() {
        promptInChat()
        status("loading")
        switchToOtherSite()
        status("ready")
        onChat("t1", "A")
        switchToOtherSite()

        assertEquals(listOf(LeftWhileGenerating(r()), StatusWhileAway(r(), "ready"), Returned(r())), listener.events)
    }

    @Test
    fun `when the tab moves to another chat then the old response is left and the new chat's statuses do not reach it`() {
        promptInChat()
        status("loading")
        onChat("t1", "B")
        status("ready")

        assertEquals(listOf(LeftWhileGenerating(r())), listener.events)
    }

    @Test
    fun `when the chat reopens in another tab then Returned carries the new tab`() {
        promptInChat()
        status("loading")
        switchToOtherSite()
        onChat("t3", "A")

        assertEquals(Returned(r(tabId = "t3")), listener.events.last())
    }

    @Test
    fun `when the chat id appears after the prompt then later events carry it`() {
        promptInChat(chatId = null)
        onChat("t1", "A")
        switchToOtherSite()

        assertEquals(listOf(LeftWhileGenerating(r(chatId = "A"))), listener.events)
    }

    @Test
    fun `when stop tapped then leaving emits nothing`() {
        promptInChat()
        status("loading")
        testee.onStopTapped("t1")
        idle()
        switchToOtherSite()

        assertEquals(emptyList<ResponseAttentionEvent>(), listener.events)
    }

    @Test
    fun `when blocked then leaving emits nothing`() {
        promptInChat()
        status("blocked")
        switchToOtherSite()

        assertEquals(emptyList<ResponseAttentionEvent>(), listener.events)
    }

    @Test
    fun `when the next prompt is sent in the same tab then the new response gets a new id`() {
        promptInChat()
        status("ready")
        promptInChat()
        switchToOtherSite()

        assertEquals(listOf(LeftWhileGenerating(r(id = 2L))), listener.events)
    }

    @Test
    fun `when a new prompt is sent while the previous one is generating then only the new one is reported on leave`() {
        promptInChat()
        status("loading")
        promptInChat()
        switchToOtherSite()

        assertEquals(listOf(LeftWhileGenerating(r(id = 2L))), listener.events)
    }

    @Test
    fun `when prompt is in fire mode then events carry FIRE`() {
        promptInChat(browserMode = BrowserMode.FIRE)
        switchToOtherSite()

        assertEquals(listOf(LeftWhileGenerating(r(mode = BrowserMode.FIRE))), listener.events)
    }

    @Test
    fun `when native input is disabled then nothing is tracked`() = assertNothingTrackedWhen {
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = false))
    }

    @Test
    fun `when native sidebar is disabled then nothing is tracked`() = assertNothingTrackedWhen {
        duckChatFeature.nativeDuckAiSidebar().setRawStoredState(Toggle.State(enable = false))
    }

    @Test
    fun `when native storage is disabled then nothing is tracked`() = assertNothingTrackedWhen {
        duckChatFeature.useNativeStorageChatData().setRawStoredState(Toggle.State(enable = false))
    }

    @Test
    fun `when Duck ai feature is disabled then nothing is tracked`() = assertNothingTrackedWhen {
        duckChatFeature.self().setRawStoredState(Toggle.State(enable = false))
    }

    @Test
    fun `when user disabled Duck ai then nothing is tracked`() = assertNothingTrackedWhen {
        runTest { whenever(duckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(false) }
    }

    @Test
    fun `when the response wait wide event toggle is off then tracking continues`() {
        duckChatFeature.sendDuckAiResponseWaitWideEvent().setRawStoredState(Toggle.State(enable = false))
        promptInChat()
        switchToOtherSite()

        assertEquals(listOf(LeftWhileGenerating(r())), listener.events)
    }

    @Test
    fun `when the gate turns off mid-response then the response is dropped`() {
        promptInChat()
        status("loading")
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = false))
        switchToOtherSite()
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = true))
        onChat("t1", "A")

        assertEquals(emptyList<ResponseAttentionEvent>(), listener.events)
    }

    @Test
    fun `when the gate turns off while away then the response gets TrackingAborted and is no longer tracked`() {
        promptInChat()
        status("loading")
        switchToOtherSite()
        listener.events.clear()

        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = false))
        status("streaming")
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = true))
        onChat("t1", "A")

        assertEquals(listOf(TrackingAborted(r())), listener.events)
    }

    @Test
    fun `when the chat comes on screen while the gate is off then a prompt after re-enabling starts on screen`() {
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = false))
        onChat("t1", "A")
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = true))
        testee.onNativePromptSubmitted(tabId = "t1", browserMode = BrowserMode.REGULAR)
        status("start_stream:new_prompt")
        switchToOtherSite()

        assertEquals(listOf(LeftWhileGenerating(r())), listener.events)
    }

    @Test
    fun `when a listener throws then the other listener still gets this and later events`() {
        val throwing = RecordingListener().apply { failure = IllegalStateException("listener failure") }
        testee = createTestee(linkedSetOf(throwing, listener))

        promptInChat()
        switchToOtherSite()
        onChat("t1", "A")

        assertEquals(listOf(LeftWhileGenerating(r()), Returned(r())), listener.events)
    }

    @Test
    fun `when a listener throws CancellationException then later events and clears still go through`() {
        val cancelling = RecordingListener().apply { failure = CancellationException("listener timeout") }
        testee = createTestee(linkedSetOf(cancelling, listener))

        promptInChat()
        switchToOtherSite()
        val eventsOnReturn = clear(ClearableData.DuckChats.All)

        assertEquals(listOf(LeftWhileGenerating(r()), TrackingAborted(r())), eventsOnReturn)
    }

    @Test
    fun `when the prompt reaches the page after the app was backgrounded then the user is away`() {
        onChat(tabId = "t1", chatId = null)
        testee.onClose()
        testee.onNativePromptSubmitted(tabId = "t1", browserMode = BrowserMode.REGULAR)
        status("start_stream:new_prompt")
        testee.onOpen(isFreshLaunch = false)
        testee.onLaunchLandingResolved(tabId = "t1", url = "$DUCK_AI_URL?chatID=A")
        idle()

        assertEquals(
            listOf(
                LeftWhileGenerating(r(chatId = null)),
                StatusWhileAway(r(chatId = null), "start_stream:new_prompt"),
                Returned(r()),
            ),
            listener.events,
        )
    }

    @Test
    fun `when the prompt reaches the page after the tab was switched then the user is away`() {
        onChat(tabId = "t1", chatId = null)
        switchToOtherSite()
        testee.onNativePromptSubmitted(tabId = "t1", browserMode = BrowserMode.REGULAR)
        status("start_stream:new_prompt")
        onChat(tabId = "t1", chatId = "A")

        assertEquals(
            listOf(
                LeftWhileGenerating(r(chatId = null)),
                StatusWhileAway(r(chatId = null), "start_stream:new_prompt"),
                Returned(r()),
            ),
            listener.events,
        )
    }

    @Test
    fun `when the app reopens on the chat then Returned`() {
        promptInChat()
        status("loading")
        testee.onClose()
        testee.onOpen(isFreshLaunch = false)
        testee.onLaunchLandingResolved(tabId = "t1", url = "$DUCK_AI_URL?chatID=A")
        idle()

        assertEquals(listOf(LeftWhileGenerating(r()), Returned(r())), listener.events)
    }

    @Test
    fun `when fire chats are cleared then fire responses get TrackingAborted and regular ones nothing`() {
        leaveRegularAAndFireB()

        clear(ClearableData.DuckChats.AllForMode(BrowserMode.FIRE))

        assertEquals(listOf(TrackingAborted(fireB)), listener.events)
    }

    @Test
    fun `when selected chats are cleared then only that chat in that mode gets TrackingAborted`() {
        leaveRegularAAndFireB()

        clear(ClearableData.DuckChats.SelectedForMode(setOf("$DUCK_AI_URL?chatID=A", "$DUCK_AI_URL?chatID=B"), BrowserMode.REGULAR))

        assertEquals(listOf(TrackingAborted(regularA)), listener.events)
    }

    @Test
    fun `when all chats are cleared then responses in every mode get TrackingAborted`() {
        leaveRegularAAndFireB()

        clear(ClearableData.DuckChats.All)

        assertEquals(listOf(TrackingAborted(regularA), TrackingAborted(fireB)), listener.events)
    }

    @Test
    fun `when tabs and browser data are cleared then nothing is emitted`() {
        leaveRegularAAndFireB()

        clear(ClearableData.Tabs.AllForMode(BrowserMode.FIRE), ClearableData.BrowserData.AllForMode(BrowserMode.FIRE))

        assertEquals(emptyList<ResponseAttentionEvent>(), listener.events)
    }

    @Test
    fun `when onClearData returns then the listener has already received TrackingAborted`() {
        leaveRegularAAndFireB()

        val eventsOnReturn = clear(ClearableData.DuckChats.AllForMode(BrowserMode.FIRE))

        assertEquals(listOf(TrackingAborted(fireB)), eventsOnReturn)
    }

    @Test
    fun `when the chat on screen is deleted then nothing is emitted and a later leave emits nothing`() {
        promptInChat()
        status("loading")
        testee.onChatDeleted("A", BrowserMode.REGULAR)
        idle()
        switchToOtherSite()

        assertEquals(emptyList<ResponseAttentionEvent>(), listener.events)
    }

    @Test
    fun `when the page deletes a chat then its response gets TrackingAborted and is no longer tracked`() {
        promptInChat()
        status("loading")
        switchToOtherSite()
        listener.events.clear()

        testee.onChatDeleted("A", BrowserMode.REGULAR)
        idle()
        onChat("t1", "A")

        assertEquals(listOf(TrackingAborted(r())), listener.events)
    }

    @Test
    fun `when the page deletes a chat in fire mode then only the fire response gets TrackingAborted`() {
        // Both responses start without a chat ID, so each keeps its own tab until chat A shows up in t2 and they both match it.
        promptInChat(tabId = "t1", chatId = null)
        switchToOtherSite(tabId = "t3")
        promptInChat(tabId = "t2", chatId = null, browserMode = BrowserMode.FIRE)
        onChat("t1", "A")
        onChat("t2", "A")
        switchToOtherSite(tabId = "t3")
        listener.events.clear()

        testee.onChatDeleted("A", BrowserMode.FIRE)
        idle()
        onChat("t1", "A")

        assertEquals(listOf(TrackingAborted(r(id = 2L, tabId = "t2", mode = BrowserMode.FIRE)), Returned(r())), listener.events)
    }

    @Test
    fun `when the page deletes all chats then every response in that mode gets TrackingAborted`() {
        leaveRegularAAndFireB()

        testee.onAllChatsDeleted(BrowserMode.FIRE)
        idle()

        assertEquals(listOf(TrackingAborted(fireB)), listener.events)
    }

    private class RecordingListener : ResponseAttentionListener {
        val events = mutableListOf<ResponseAttentionEvent>()
        var failure: Exception? = null

        override suspend fun onEvent(event: ResponseAttentionEvent) {
            // Suspends like a real listener would, so a test fails if onClearData returns before delivery.
            yield()
            failure?.let { throw it }
            events += event
        }
    }

    private companion object {
        const val DUCK_AI_URL = "https://duck.ai/"
        const val OTHER_URL = "https://example.com/"
    }
}
