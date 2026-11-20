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
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.duckchat.impl.responding.DuckAiRespondingServiceController
import com.duckduckgo.duckchat.impl.responseready.DuckAiResponseReadyMonitor
import com.duckduckgo.duckchat.impl.responseready.DuckAiResponseReadyNotifier
import com.duckduckgo.duckchat.impl.responseready.ResponseReadyNotificationsSettings
import com.duckduckgo.duckchat.impl.responseready.WaitResult
import com.duckduckgo.duckchat.impl.voice.VoiceSessionStateManager
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DuckAiResponseAttentionTrackerImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val duckChat: DuckChat = mock()
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val duckChatFeatureRepository: DuckChatFeatureRepository = mock()
    private val fireTabRepository: TabRepository = mock()
    private val voiceSessionStateManager: VoiceSessionStateManager = mock()
    private val pixels: DuckChatPixels = mock()

    private val log = mutableListOf<String>()
    private val readyMonitor = FakeReadyMonitor(log)
    private val notifier = FakeNotifier(log)
    private val serviceController = FakeServiceController(log)

    /** [log] without keep-alive changes. */
    private val effects get() = log.filterNot { it.startsWith("keepAlive") }

    private lateinit var testee: DuckAiResponseAttentionTrackerImpl

    @Before
    fun setup() = runTest {
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = true))
        duckChatFeature.nativeDuckAiSidebar().setRawStoredState(Toggle.State(enable = true))
        duckChatFeature.self().setRawStoredState(Toggle.State(enable = true))
        duckChatFeature.useNativeStorageChatData().setRawStoredState(Toggle.State(enable = true))
        duckChatFeature.responseReadyNotifications().setRawStoredState(Toggle.State(enable = true))
        duckChatFeature.duckAiNativeStorage().setRawStoredState(Toggle.State(enable = true))
        whenever(duckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(true)
        whenever(duckChatFeatureRepository.isAIChatHistoryEnabled()).thenReturn(true)
        whenever(duckChat.isDuckChatUrl(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as Uri).toString().startsWith(DUCK_AI_URL)
        }
        testee = DuckAiResponseAttentionTrackerImpl(
            duckChat = duckChat,
            duckChatFeature = lazy { duckChatFeature },
            duckChatFeatureRepository = duckChatFeatureRepository,
            fireTabRepository = lazy { fireTabRepository },
            voiceSessionStateManager = voiceSessionStateManager,
            readyMonitor = readyMonitor,
            notifier = notifier,
            serviceController = serviceController,
            pixels = lazy { pixels },
            dispatchers = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
        testee.onOpen(isFreshLaunch = true)
        testee.onLaunchLandingResolved(tabId = null, url = null)
        idle()
    }

    private fun idle() = coroutineRule.testScope.testScheduler.advanceUntilIdle()

    private fun runCurrent() = coroutineRule.testScope.testScheduler.runCurrent()

    private fun onChat(tabId: String, chatId: String?) {
        testee.onSelectedTabChanged(tabId = tabId, url = chatUrl(chatId))
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
        if (streamStarted) testee.onResponseState(tabId = tabId, status = "start_stream:new_prompt", browserMode = browserMode)
        idle()
    }

    private fun status(status: String, tabId: String = "t1") {
        testee.onResponseState(tabId = tabId, status = status, browserMode = BrowserMode.REGULAR)
        idle()
    }

    private fun switchToOtherSite(tabId: String = "t2") {
        testee.onSelectedTabChanged(tabId = tabId, url = OTHER_URL)
        idle()
    }

    private fun findChatId(chatId: String) {
        coroutineRule.testScope.launch { readyMonitor.findChatId(chatId) }
        idle()
    }

    private fun finishWait(result: WaitResult, index: Int = readyMonitor.waits.lastIndex) {
        readyMonitor.finish(result, index)
        idle()
    }

    private fun leaveRegularAAndFireB() {
        promptInChat(tabId = "t1", chatId = "A")
        promptInChat(tabId = "t2", chatId = "B", browserMode = BrowserMode.FIRE)
        switchToOtherSite(tabId = "t3")
        log.clear()
    }

    // Returns the log as it was when onClearData returned, so tests can check the effects happened before then.
    private fun clear(vararg types: ClearableData): List<String> {
        var logOnReturn: List<String>? = null
        coroutineRule.testScope.launch {
            testee.onClearData(types.toSet())
            logOnReturn = log.toList()
        }
        idle()
        return checkNotNull(logOnReturn)
    }

    private fun assertNothingHappensWhen(setup: () -> Unit) {
        setup()
        promptInChat()
        status("loading")
        switchToOtherSite()

        assertEquals(emptyList<String>(), log)
    }

    // --- On screen and away ---

    @Test
    fun `when app backgrounded while loading then the response is awaited`() {
        promptInChat()
        status("loading")

        testee.onClose()
        idle()

        assertEquals(listOf("await t1 A"), effects)
    }

    @Test
    fun `when user returns while loading and leaves again then it is awaited twice`() {
        promptInChat()
        status("loading")
        switchToOtherSite()
        onChat("t1", "A")
        switchToOtherSite()

        assertEquals(listOf("await t1 A", "wait cancelled t1", "cancel A", "await t1 A"), effects)
    }

    @Test
    fun `when ready on screen then leaving awaits nothing`() {
        promptInChat()
        status("ready")
        switchToOtherSite()

        assertEquals(emptyList<String>(), effects)
    }

    @Test
    fun `when idle ready arrives before the stream then the response is still generating`() {
        promptInChat(streamStarted = false)
        status("ready")
        switchToOtherSite()

        assertEquals(listOf("await t1 A"), effects)
    }

    @Test
    fun `when error on screen and the page retries then leaving awaits the response`() {
        promptInChat()
        status("error")
        status("start_stream:auto_retry_last_prompt")
        switchToOtherSite()

        assertEquals(listOf("await t1 A"), effects)
    }

    @Test
    fun `when returned after ready while away then a later leave awaits nothing`() {
        promptInChat()
        status("loading")
        switchToOtherSite()
        status("ready")
        onChat("t1", "A")
        switchToOtherSite()

        assertEquals(listOf("await t1 A", "wait cancelled t1", "cancel A"), effects)
    }

    @Test
    fun `when the tab moves to another chat then the old response is left and the new chat's statuses do not reach it`() {
        promptInChat()
        status("loading")
        onChat("t1", "B")
        status("ready")

        assertEquals(listOf("await t1 A"), effects)
    }

    @Test
    fun `when the chat reopens in another tab then a later wait uses the new tab`() {
        promptInChat()
        status("loading")
        switchToOtherSite()
        onChat("t3", "A")
        switchToOtherSite()

        assertEquals("await t3 A", effects.last())
    }

    @Test
    fun `when the chat id appears after the prompt then the wait carries it`() {
        promptInChat(chatId = null)
        onChat("t1", "A")
        switchToOtherSite()

        assertEquals(listOf("await t1 A"), effects)
    }

    @Test
    fun `when stop tapped then leaving awaits nothing`() {
        promptInChat()
        status("loading")
        testee.onStopTapped("t1")
        idle()
        switchToOtherSite()

        assertEquals(emptyList<String>(), effects)
    }

    @Test
    fun `when blocked then leaving awaits nothing`() {
        promptInChat()
        status("blocked")
        switchToOtherSite()

        assertEquals(emptyList<String>(), effects)
    }

    @Test
    fun `when the next prompt is sent in the same tab then only the new response is awaited`() {
        promptInChat()
        status("ready")
        promptInChat()
        switchToOtherSite()

        assertEquals(listOf("await t1 A"), effects)
    }

    @Test
    fun `when a new prompt is sent while the previous one is generating then only the new one is awaited on leave`() {
        promptInChat()
        status("loading")
        promptInChat()
        switchToOtherSite()

        assertEquals(listOf("await t1 A"), effects)
    }

    @Test
    fun `when prompt is in fire mode then nothing is awaited and keep alive stays off`() {
        promptInChat(browserMode = BrowserMode.FIRE)
        switchToOtherSite()

        assertEquals(emptyList<String>(), log)
    }

    // --- Feature gate ---

    @Test
    fun `when native input is disabled then nothing is tracked`() = assertNothingHappensWhen {
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = false))
    }

    @Test
    fun `when native sidebar is disabled then nothing is tracked`() = assertNothingHappensWhen {
        duckChatFeature.nativeDuckAiSidebar().setRawStoredState(Toggle.State(enable = false))
    }

    @Test
    fun `when native storage is disabled then nothing is tracked`() = assertNothingHappensWhen {
        duckChatFeature.useNativeStorageChatData().setRawStoredState(Toggle.State(enable = false))
    }

    @Test
    fun `when Duck ai feature is disabled then nothing is tracked`() = assertNothingHappensWhen {
        duckChatFeature.self().setRawStoredState(Toggle.State(enable = false))
    }

    @Test
    fun `when user disabled Duck ai then nothing is tracked`() = assertNothingHappensWhen {
        runTest { whenever(duckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(false) }
    }

    @Test
    fun `when the response wait wide event toggle is off then tracking continues`() {
        duckChatFeature.sendDuckAiResponseWaitWideEvent().setRawStoredState(Toggle.State(enable = false))
        promptInChat()
        switchToOtherSite()

        assertEquals(listOf("await t1 A"), effects)
    }

    @Test
    fun `when the gate turns off mid-response then the response is dropped`() {
        promptInChat()
        status("loading")
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = false))
        switchToOtherSite()
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = true))
        onChat("t1", "A")

        assertEquals(emptyList<String>(), effects)
    }

    @Test
    fun `when the gate turns off while away then the wait and notification are cancelled and the response is no longer tracked`() {
        promptInChat()
        status("loading")
        switchToOtherSite()
        log.clear()

        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = false))
        status("streaming")
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = true))
        onChat("t1", "A")

        assertEquals(listOf("wait cancelled t1", "cancel A", "keepAlive false"), log)
    }

    @Test
    fun `when the chat comes on screen while the gate is off then a prompt after re-enabling starts on screen`() {
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = false))
        onChat("t1", "A")
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = true))
        testee.onNativePromptSubmitted(tabId = "t1", browserMode = BrowserMode.REGULAR)
        status("start_stream:new_prompt")
        switchToOtherSite()

        assertEquals(listOf("await t1 A"), effects)
    }

    // --- Eligibility ---

    @Test
    fun `when flag off then left response is not awaited and keep alive stays off`() = assertNothingHappensWhen {
        duckChatFeature.responseReadyNotifications().setRawStoredState(Toggle.State(enable = false))
    }

    @Test
    fun `when native storage off then left response is not awaited`() = assertNothingHappensWhen {
        duckChatFeature.duckAiNativeStorage().setRawStoredState(Toggle.State(enable = false))
    }

    @Test
    fun `when history off then left response is not awaited`() = assertNothingHappensWhen {
        runTest { whenever(duckChatFeatureRepository.isAIChatHistoryEnabled()).thenReturn(false) }
    }

    @Test
    fun `when voice session active then left response is not awaited`() = assertNothingHappensWhen {
        whenever(voiceSessionStateManager.isVoiceSessionActive("t1")).thenReturn(true)
    }

    @Test
    fun `when the notifier throws then later actions still go through and keep alive still updates`() {
        notifier.cancelFailure = IllegalStateException("notifier failure")

        promptInChat()
        switchToOtherSite()
        onChat("t1", "A")
        switchToOtherSite()
        status("ready")
        onChat("t1", "A")

        assertEquals(
            listOf(
                "keepAlive true",
                "await t1 A",
                "wait cancelled t1",
                "cancel A failed",
                "await t1 A",
                "wait cancelled t1",
                "cancel A failed",
                "keepAlive false",
            ),
            log,
        )
    }

    @Test
    fun `when the notifier throws CancellationException then later actions and clears still go through`() {
        notifier.cancelFailure = CancellationException("notifier timeout")

        promptInChat()
        switchToOtherSite()
        val logOnReturn = clear(ClearableData.DuckChats.All)
        promptInChat()

        assertEquals(listOf("keepAlive true", "await t1 A", "wait cancelled t1", "cancel A failed", "keepAlive false"), logOnReturn)
        assertEquals("keepAlive true", log.last())
    }

    @Test
    fun `when the notifier throws CancellationException then a clear still aborts every response`() {
        notifier.cancelFailure = CancellationException("notifier timeout")
        promptInChat(tabId = "t1", chatId = "A")
        promptInChat(tabId = "t2", chatId = "B")
        switchToOtherSite(tabId = "t3")
        log.clear()

        val logOnReturn = clear(ClearableData.DuckChats.All)

        assertEquals(
            listOf("wait cancelled t1", "cancel A failed", "wait cancelled t2", "cancel B failed", "keepAlive false"),
            logOnReturn,
        )
    }

    @Test
    fun `when the notifier throws on ready then keep alive still turns off`() {
        notifier.notifyFailure = RuntimeException("notify failed")
        promptInChat()
        switchToOtherSite()

        finishWait(WaitResult.Ready)

        assertEquals(listOf("keepAlive true", "await t1 A", "keepAlive false"), log)
    }

    // --- Away and back ---

    @Test
    fun `when the prompt reaches the page after the app was backgrounded then the user is away`() {
        onChat(tabId = "t1", chatId = null)
        testee.onClose()
        testee.onNativePromptSubmitted(tabId = "t1", browserMode = BrowserMode.REGULAR)
        status("start_stream:new_prompt")
        testee.onOpen(isFreshLaunch = false)
        testee.onLaunchLandingResolved(tabId = "t1", url = chatUrl("A"))
        idle()

        assertEquals(listOf("await t1 null", "wait cancelled t1", "cancel A"), effects)
    }

    @Test
    fun `when the prompt reaches the page after the tab was switched then the user is away`() {
        onChat(tabId = "t1", chatId = null)
        switchToOtherSite()
        testee.onNativePromptSubmitted(tabId = "t1", browserMode = BrowserMode.REGULAR)
        status("start_stream:new_prompt")
        onChat(tabId = "t1", chatId = "A")

        assertEquals(listOf("await t1 null", "wait cancelled t1", "cancel A"), effects)
    }

    @Test
    fun `when an address bar prompt page loads and the app is backgrounded then it is awaited once`() {
        testee.onDuckAiPageVisible(tabId = "t1", url = HANDOVER_URL)
        testee.onClose()
        testee.onDuckAiPageVisible(tabId = "t1", url = HANDOVER_URL)
        testee.onNativePromptSubmitted(tabId = "t1", browserMode = BrowserMode.REGULAR)
        status("start_stream:new_prompt")
        idle()

        assertEquals(listOf("await t1 null"), effects)
    }

    @Test
    fun `when an address bar prompt page loads in a fire tab then nothing is awaited and keep alive stays off`() = runTest {
        whenever(fireTabRepository.getTab("t1")).thenReturn(TabEntity(tabId = "t1"))
        testee.onDuckAiPageVisible(tabId = "t1", url = HANDOVER_URL)
        testee.onClose()
        idle()

        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `when a handover page loads without a query then nothing is tracked`() {
        testee.onDuckAiPageVisible(tabId = "t1", url = "$DUCK_AI_URL?prompt=1")
        testee.onClose()
        idle()

        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `when the app reopens on the chat then the wait and notification are cancelled`() {
        promptInChat()
        status("loading")
        testee.onClose()
        testee.onOpen(isFreshLaunch = false)
        testee.onLaunchLandingResolved(tabId = "t1", url = chatUrl("A"))
        idle()

        assertEquals(listOf("cancel A"), effects)
    }

    @Test
    fun `when user returns still generating then keep alive stays on`() {
        promptInChat()
        status("loading")
        switchToOtherSite()
        onChat("t1", "A")

        assertEquals(listOf("keepAlive true", "await t1 A", "wait cancelled t1", "cancel A"), log)
    }

    @Test
    fun `when user returns after ready while away then keep alive turns off`() {
        promptInChat()
        status("loading")
        switchToOtherSite()
        status("ready")
        onChat("t1", "A")

        assertEquals(
            listOf("keepAlive true", "await t1 A", "wait cancelled t1", "cancel A", "keepAlive false"),
            log,
        )
    }

    @Test
    fun `when user returns while still generating then ready on screen turns keep alive off`() {
        promptInChat()
        status("loading")
        switchToOtherSite()
        onChat("t1", "A")
        status("ready")

        assertEquals(listOf("keepAlive true", "await t1 A", "wait cancelled t1", "cancel A", "keepAlive false"), log)
    }

    @Test
    fun `when the user returns after the wait timed out then the notification is still cancelled`() {
        promptInChat(chatId = null)
        switchToOtherSite()
        findChatId("A")
        finishWait(WaitResult.TimedOut)
        log.clear()

        onChat("t1", "A")

        assertEquals(listOf("cancel A"), effects)
    }

    // --- Clearing ---

    @Test
    fun `when fire chats are cleared then the regular response's wait keeps running`() {
        leaveRegularAAndFireB()

        clear(ClearableData.DuckChats.AllForMode(BrowserMode.FIRE))
        finishWait(WaitResult.Ready)

        assertEquals(listOf("notify t1 A", "keepAlive false"), log)
    }

    @Test
    fun `when selected chats are cleared then only that chat in that mode is cancelled`() {
        leaveRegularAAndFireB()

        clear(ClearableData.DuckChats.SelectedForMode(setOf(chatUrl("A"), chatUrl("B")), BrowserMode.REGULAR))

        assertEquals(listOf("wait cancelled t1", "cancel A", "keepAlive false"), log)
    }

    @Test
    fun `when all chats are cleared then the regular response's wait and notification are cancelled`() {
        leaveRegularAAndFireB()

        clear(ClearableData.DuckChats.All)

        assertEquals(listOf("wait cancelled t1", "cancel A", "keepAlive false"), log)
    }

    @Test
    fun `when tabs and browser data are cleared then nothing is cancelled`() {
        leaveRegularAAndFireB()

        clear(ClearableData.Tabs.AllForMode(BrowserMode.FIRE), ClearableData.BrowserData.AllForMode(BrowserMode.FIRE))

        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `when onClearData returns then the wait was cancelled and cancel ran`() {
        promptInChat()
        switchToOtherSite()
        log.clear()

        val logOnReturn = clear(ClearableData.DuckChats.All)

        assertEquals(listOf("wait cancelled t1", "cancel A", "keepAlive false"), logOnReturn)
    }

    @Test
    fun `when tracking aborted before chat id is known then nothing is cancelled`() {
        promptInChat(chatId = null)
        switchToOtherSite()
        log.clear()

        clear(ClearableData.DuckChats.All)

        assertEquals(listOf("wait cancelled t1", "keepAlive false"), log)
    }

    @Test
    fun `when the wait found the chat id and that chat is cleared then the wait and its notification are cancelled`() {
        promptInChat(chatId = null)
        switchToOtherSite()
        findChatId("A")
        log.clear()

        clear(ClearableData.DuckChats.SelectedForMode(setOf(chatUrl("A")), BrowserMode.REGULAR))

        assertEquals(listOf("wait cancelled t1", "cancel A", "keepAlive false"), log)
    }

    @Test
    fun `when the wait found the chat id and the page deletes that chat then the wait and its notification are cancelled`() {
        promptInChat(chatId = null)
        switchToOtherSite()
        findChatId("A")
        log.clear()

        testee.onChatDeleted("A", BrowserMode.REGULAR)
        idle()

        assertEquals(listOf("wait cancelled t1", "cancel A", "keepAlive false"), log)
    }

    @Test
    fun `when the chat on screen is deleted then nothing is awaited and a later leave awaits nothing`() {
        promptInChat()
        status("loading")
        testee.onChatDeleted("A", BrowserMode.REGULAR)
        idle()
        switchToOtherSite()

        assertEquals(emptyList<String>(), effects)
    }

    @Test
    fun `when the page deletes a chat then its wait and notification are cancelled and it is no longer tracked`() {
        promptInChat()
        status("loading")
        switchToOtherSite()
        log.clear()

        testee.onChatDeleted("A", BrowserMode.REGULAR)
        idle()
        onChat("t1", "A")

        assertEquals(listOf("wait cancelled t1", "cancel A", "keepAlive false"), log)
    }

    @Test
    fun `when the page deletes a chat in fire mode then the regular response with that chat is untouched`() {
        // Both responses start without a chat ID, so each keeps its own tab until chat A shows up in t2 and they both match it.
        promptInChat(tabId = "t1", chatId = null)
        switchToOtherSite(tabId = "t3")
        promptInChat(tabId = "t2", chatId = null, browserMode = BrowserMode.FIRE)
        onChat("t1", "A")
        onChat("t2", "A")
        switchToOtherSite(tabId = "t3")
        log.clear()

        testee.onChatDeleted("A", BrowserMode.FIRE)
        idle()
        onChat("t1", "A")

        assertEquals(listOf("wait cancelled t2", "cancel A"), log)
    }

    @Test
    fun `when the page deletes all chats in fire mode then the regular response's wait keeps running`() {
        leaveRegularAAndFireB()

        testee.onAllChatsDeleted(BrowserMode.FIRE)
        idle()
        finishWait(WaitResult.Ready)

        assertEquals(listOf("notify t1 A", "keepAlive false"), log)
    }

    // --- Keep-alive ---

    @Test
    fun `when prompt starts on screen then keep alive on`() {
        promptInChat()

        assertEquals(listOf("keepAlive true"), log)
    }

    @Test
    fun `when ready on screen then keep alive off`() {
        promptInChat()
        status("ready")

        assertEquals(listOf("keepAlive true", "keepAlive false"), log)
    }

    @Test
    fun `when stop tapped on screen then keep alive off`() {
        promptInChat()
        status("loading")
        testee.onStopTapped("t1")
        idle()
        testee.onStopTapped("t1")
        idle()

        assertEquals(listOf("keepAlive true", "keepAlive false"), log)
    }

    @Test
    fun `when user leaves while generating then await starts and keep alive never turns off`() {
        promptInChat()
        status("loading")
        switchToOtherSite()

        assertEquals(listOf("keepAlive true", "await t1 A"), log)
    }

    @Test
    fun `when the wait finishes then keep alive off`() {
        promptInChat()
        switchToOtherSite()

        finishWait(WaitResult.Stopped)

        assertEquals(listOf("keepAlive true", "await t1 A", "keepAlive false"), log)
    }

    @Test
    fun `when the page restarts a finished response on screen then keep alive on again`() {
        promptInChat()
        status("ready")
        status("start_stream:retry_last_prompt")

        assertEquals(listOf("keepAlive true", "keepAlive false", "keepAlive true"), log)
    }

    @Test
    fun `when the chat on screen is deleted while generating then keep alive off`() {
        promptInChat()
        status("loading")
        testee.onChatDeleted("A", BrowserMode.REGULAR)
        idle()

        assertEquals(listOf("keepAlive true", "keepAlive false"), log)
    }

    @Test
    fun `when the gate turns off while generating on screen then keep alive off`() {
        promptInChat()
        status("loading")
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = false))
        status("streaming")

        assertEquals(listOf("keepAlive true", "keepAlive false"), log)
    }

    @Test
    fun `when a new prompt replaces a generating response in the tab then keep alive stays on until the new one is ready`() {
        promptInChat()
        status("loading")
        promptInChat()
        status("ready")

        assertEquals(listOf("keepAlive true", "keepAlive false"), log)
    }

    @Test
    fun `when a prompt starts in an unselected tab then it is awaited`() {
        onChat(tabId = "t1", chatId = null)
        switchToOtherSite()
        testee.onNativePromptSubmitted(tabId = "t1", browserMode = BrowserMode.REGULAR)
        idle()

        assertEquals(listOf("keepAlive true", "await t1 null"), log)
    }

    // --- Streams the page starts ---

    @Test
    fun `when the page starts a stream with no tracked response then a response is tracked`() {
        onChat("t1", "A")
        status("start_stream:edit_last_prompt")

        assertEquals(listOf("keepAlive true"), log)

        switchToOtherSite()

        assertEquals(listOf("await t1 A"), effects)
    }

    @Test
    fun `when the page starts a stream in fire mode then nothing is awaited and keep alive stays off`() {
        onChat("t1", "A")
        testee.onResponseState(tabId = "t1", status = "start_stream:edit_last_prompt", browserMode = BrowserMode.FIRE)
        idle()
        switchToOtherSite()

        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `when the tab moves to another chat and the page starts a stream then a new response is tracked and the old one stays left`() {
        promptInChat()
        status("loading")
        onChat("t1", "B")
        status("start_stream:new_prompt")
        switchToOtherSite()

        assertEquals(listOf("keepAlive true", "await t1 A", "await t1 B"), log)
    }

    @Test
    fun `when the page starts a stream while the app is closed then it is awaited`() {
        onChat("t1", "A")
        testee.onClose()
        status("start_stream:auto_retry_last_prompt")

        assertEquals(listOf("keepAlive true", "await t1 null"), log)
    }

    @Test
    fun `when the page starts a stream in an unselected tab then it is awaited`() {
        onChat("t1", "A")
        switchToOtherSite()
        status("start_stream:auto_retry_last_prompt")

        assertEquals(listOf("keepAlive true", "await t1 null"), log)
    }

    @Test
    fun `when the page starts a reconnection with no tracked response then nothing is tracked`() {
        onChat("t1", "A")
        status("start_stream:restart_stream_reconnection")

        assertEquals(emptyList<String>(), log)

        switchToOtherSite()

        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `when a reconnection arrives for a tracked response then it counts as progress`() {
        promptInChat()
        status("ready")
        status("start_stream:restart_stream_reconnection")
        switchToOtherSite()

        assertEquals(listOf("await t1 A"), effects)
    }

    // --- Wait results ---

    @Test
    fun `when the wait is Ready then notify with the original tab`() {
        promptInChat()
        switchToOtherSite()

        finishWait(WaitResult.Ready)

        assertEquals(listOf("keepAlive true", "await t1 A", "notify t1 A", "keepAlive false"), log)
        verifyNoInteractions(pixels)
    }

    @Test
    fun `when the wait is Ready without a chat id then nothing notified`() {
        promptInChat(chatId = null)
        switchToOtherSite()

        finishWait(WaitResult.Ready)

        assertEquals(listOf("keepAlive true", "await t1 null", "keepAlive false"), log)
    }

    @Test
    fun `when the wait times out then pixel sent`() {
        promptInChat()
        switchToOtherSite()

        finishWait(WaitResult.TimedOut)

        verify(pixels).reportResponseReadyWaitTimedOut()
        assertEquals(listOf("keepAlive true", "await t1 A", "keepAlive false"), log)
    }

    @Test
    fun `when the wait is Stopped then no notification and no pixel`() {
        promptInChat()
        switchToOtherSite()

        finishWait(WaitResult.Stopped)

        assertEquals(listOf("keepAlive true", "await t1 A", "keepAlive false"), log)
        verifyNoInteractions(pixels)
    }

    @Test
    fun `when a Ready result arrives after the user returned then nothing is notified`() {
        finishAfterReturn(WaitResult.Ready)

        assertTrue(log.none { it.startsWith("notify") })
    }

    @Test
    fun `when a TimedOut result arrives after the user returned then no pixel is sent`() {
        finishAfterReturn(WaitResult.TimedOut)

        verify(pixels, never()).reportResponseReadyWaitTimedOut()
    }

    private fun finishAfterReturn(result: WaitResult) {
        promptInChat()
        switchToOtherSite()

        // The result is queued behind the return, so the return is handled first.
        readyMonitor.finish(result)
        testee.onSelectedTabChanged(tabId = "t1", url = chatUrl("A"))
        idle()
    }

    @Test
    fun `when the user leaves twice then only the second wait's result counts`() {
        promptInChat()
        switchToOtherSite()

        // The first result is queued behind the return and the second leave.
        readyMonitor.finish(WaitResult.Ready, index = 0)
        testee.onSelectedTabChanged(tabId = "t1", url = chatUrl("A"))
        testee.onSelectedTabChanged(tabId = "t2", url = OTHER_URL)
        idle()

        assertEquals(listOf("keepAlive true", "await t1 A", "cancel A", "await t1 A"), log)

        finishWait(WaitResult.Ready, index = 1)

        assertEquals(listOf("notify t1 A", "keepAlive false"), log.drop(4))
    }

    @Test
    fun `when the user returns while the ready notification is being posted then the notification is cancelled after it is posted`() {
        promptInChat()
        switchToOtherSite()

        readyMonitor.finish(WaitResult.Ready)
        runCurrent()
        onChat("t1", "A")
        idle()

        assertEquals(listOf("await t1 A", "notify t1 A", "cancel A"), effects)
    }

    private class FakeReadyMonitor(private val log: MutableList<String>) : DuckAiResponseReadyMonitor {
        val waits = mutableListOf<CompletableDeferred<WaitResult>>()
        private val chatIdCallbacks = mutableListOf<suspend (String) -> Unit>()

        override suspend fun await(
            tabId: String,
            chatId: String?,
            settings: ResponseReadyNotificationsSettings,
            onChatIdFound: suspend (String) -> Unit,
        ): WaitResult {
            log += "await $tabId $chatId"
            val result = CompletableDeferred<WaitResult>()
            waits += result
            chatIdCallbacks += onChatIdFound
            // Logs when the wait is cancelled, not when its coroutine next runs on the IO dispatcher.
            return suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { log += "wait cancelled $tabId" }
                result.invokeOnCompletion { continuation.resume(result.getCompleted()) }
            }
        }

        suspend fun findChatId(chatId: String, index: Int = chatIdCallbacks.lastIndex) {
            chatIdCallbacks[index](chatId)
        }

        fun finish(result: WaitResult, index: Int = waits.lastIndex) {
            waits[index].complete(result)
        }
    }

    private class FakeNotifier(private val log: MutableList<String>) : DuckAiResponseReadyNotifier {
        var notifyFailure: Exception? = null
        var cancelFailure: Exception? = null

        override suspend fun notifyReady(tabId: String, chatId: String) {
            // Suspends like posting a real notification would, so ordering bugs show.
            delay(1)
            notifyFailure?.let { throw it }
            log += "notify $tabId $chatId"
        }

        override suspend fun cancel(chatId: String) {
            // Suspends, so a test fails if onClearData returns before the cancellation finished.
            yield()
            cancelFailure?.let {
                log += "cancel $chatId failed"
                throw it
            }
            log += "cancel $chatId"
        }
    }

    private class FakeServiceController(private val log: MutableList<String>) : DuckAiRespondingServiceController {
        override fun setKeepAliveOnLeave(enabled: Boolean) {
            log += "keepAlive $enabled"
        }
    }

    private companion object {
        const val DUCK_AI_URL = "https://duck.ai/"
        const val OTHER_URL = "https://example.com/"
        const val HANDOVER_URL = "$DUCK_AI_URL?q=hi&prompt=1"

        fun chatUrl(chatId: String?) = if (chatId == null) DUCK_AI_URL else "$DUCK_AI_URL?chatID=$chatId"
    }
}
