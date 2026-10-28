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

package com.duckduckgo.duckchat.impl.wideevents

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckAiSessionExitTrigger
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DuckAiResponseWaitWideEventTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val wideEventClient: WideEventClient = mock()
    private val duckChat: DuckChat = mock()
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val duckChatFeatureRepository: DuckChatFeatureRepository = mock()
    private val flowStore = FakeFlowStore()

    private lateinit var testee: DuckAiResponseWaitWideEventImpl

    @Before
    fun setup() = runTest {
        duckChatFeature.sendDuckAiResponseWaitWideEvent().setRawStoredState(Toggle.State(enable = true))
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = true))
        duckChatFeature.nativeDuckAiSidebar().setRawStoredState(Toggle.State(enable = true))
        duckChatFeature.self().setRawStoredState(Toggle.State(enable = true))
        duckChatFeature.useNativeStorageChatData().setRawStoredState(Toggle.State(enable = true))
        whenever(duckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(true)
        whenever(duckChat.isDuckChatUrl(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as Uri).toString().startsWith(DUCK_AI_URL)
        }
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(1L))
        whenever(wideEventClient.flowStep(any(), any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.flowFinish(any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.flowAbort(any())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.intervalStart(any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.intervalEnd(any(), any())).thenReturn(Result.success(Duration.ZERO))
        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(emptyList()))

        testee = createTestee()
        testee.onOpen(isFreshLaunch = true)
        testee.onLaunchLandingResolved(tabId = null, url = null)
        idle()
    }

    private fun createTestee() = DuckAiResponseWaitWideEventImpl(
        wideEventClient = wideEventClient,
        duckChat = duckChat,
        duckChatFeature = lazy { duckChatFeature },
        duckChatFeatureRepository = duckChatFeatureRepository,
        flowStore = flowStore,
        dispatchers = coroutineRule.testDispatcherProvider,
        appCoroutineScope = coroutineRule.testScope,
    )

    private suspend fun stubOpenFlowIds(vararg ids: Long) {
        whenever(wideEventClient.getFlowIds("duckai-response-wait")).thenReturn(Result.success(ids.toList()))
    }

    // Callbacks are sent before the consumer runs, so recovery must still be applied before them.
    private suspend fun restart(stored: List<PersistedResponseWaitFlow>, openIds: List<Long>, landingTabId: String?, landingUrl: String?) {
        flowStore.flows = stored
        stubOpenFlowIds(*openIds.toLongArray())
        testee = createTestee()
        testee.onOpen(isFreshLaunch = true)
        testee.onLaunchLandingResolved(tabId = landingTabId, url = landingUrl)
        idle()
    }

    private fun stored(
        flowId: Long = 7L,
        tabId: String = "t1",
        chatId: String? = "A",
        left: Boolean = true,
        returned: Boolean = false,
        done: Boolean = false,
        chatSwitchedAway: Boolean = false,
    ) = PersistedResponseWaitFlow(
        flowId = flowId,
        tabId = tabId,
        chatId = chatId,
        left = left,
        returned = returned,
        done = done,
        readyWhileAwayRecorded = false,
        readyToReturnRunning = false,
        chatSwitchedAway = chatSwitchedAway,
    )

    private fun idle() = coroutineRule.testScope.testScheduler.advanceUntilIdle()

    private fun onChat(tabId: String, chatId: String?) {
        testee.onSelectedTabChanged(tabId = tabId, url = if (chatId == null) DUCK_AI_URL else "$DUCK_AI_URL?chatID=$chatId")
    }

    private fun promptInChat(
        tabId: String = "t1",
        chatId: String? = "A",
        effort: String? = "low",
        tool: String? = null,
        streamStarted: Boolean = true,
    ) {
        onChat(tabId = tabId, chatId = chatId)
        testee.onNativePromptSubmitted(tabId = tabId, reasoningEffort = effort, selectedTool = tool)
        if (streamStarted) testee.onResponseState(tabId = tabId, status = "start_stream:new_prompt")
        idle()
    }

    private fun switchToOtherSite(tabId: String = "t2") {
        testee.onSelectedTabChanged(tabId = tabId, url = OTHER_URL)
        idle()
    }

    @Test
    fun `when native prompt submitted then flow starts with name, 24h not_returned cleanup and start metadata`() = runTest {
        promptInChat(effort = "low", tool = "WebSearch")

        verify(wideEventClient).flowStart(
            name = eq("duckai-response-wait"),
            flowEntryPoint = isNull(),
            metadata = eq(
                mapOf(
                    "reasoning_effort" to "low",
                    "used_reasoning" to "true",
                    "used_extended_reasoning" to "false",
                    "user_requested_image_generation" to "false",
                    "user_requested_web_search" to "true",
                    "status_reason" to "not_returned",
                ),
            ),
            cleanupPolicy = eq(CleanupPolicy.OnTimeout(duration = 24.hours, flowStatus = FlowStatus.Failure(reason = "not_returned"))),
            samplingProbability = eq(1.0f),
            definition = any(),
        )
        verify(wideEventClient).intervalStart(eq(1L), eq("prompt_to_leave_ms_bucketed"), isNull(), eq(PROMPT_BUCKETS))
    }

    @Test
    fun `when effort is null then reasoning_effort is absent and both reasoning flags are false`() = runTest {
        promptInChat(effort = null, tool = null)

        verify(wideEventClient).flowStart(
            name = any(),
            flowEntryPoint = anyOrNull(),
            metadata = eq(
                mapOf(
                    "used_reasoning" to "false",
                    "used_extended_reasoning" to "false",
                    "user_requested_image_generation" to "false",
                    "user_requested_web_search" to "false",
                    "status_reason" to "not_returned",
                ),
            ),
            cleanupPolicy = any(),
            samplingProbability = any(),
            definition = any(),
        )
    }

    @Test
    fun `when effort is medium then both reasoning flags are true`() = runTest {
        promptInChat(effort = "medium")

        verify(wideEventClient).flowStart(
            name = any(),
            flowEntryPoint = anyOrNull(),
            metadata = argThat {
                this["reasoning_effort"] == "medium" && this["used_reasoning"] == "true" && this["used_extended_reasoning"] == "true"
            },
            cleanupPolicy = any(),
            samplingProbability = any(),
            definition = any(),
        )
    }

    @Test
    fun `when tool is GenerateImage then image generation is true`() = runTest {
        promptInChat(tool = "GenerateImage")

        verify(wideEventClient).flowStart(
            name = any(),
            flowEntryPoint = anyOrNull(),
            metadata = argThat { this["user_requested_image_generation"] == "true" && this["user_requested_web_search"] == "false" },
            cleanupPolicy = any(),
            samplingProbability = any(),
            definition = any(),
        )
    }

    @Test
    fun `when response ready without leaving then finishes SUCCESS watched`() = runTest {
        promptInChat()

        testee.onResponseState(tabId = "t1", status = "ready")
        idle()

        verify(wideEventClient).flowFinish(
            eq(1L),
            eq(FlowStatus.Success),
            eq(mapOf("status_reason" to "watched")),
        )
    }

    @Test
    fun `when idle ready arrives before the new prompt's stream then flow finishes watched only after the stream's ready`() = runTest {
        promptInChat(streamStarted = false)

        testee.onResponseState(tabId = "t1", status = "ready")
        idle()

        verify(wideEventClient, never()).flowFinish(any(), any(), any())

        testee.onResponseState(tabId = "t1", status = "start_stream:new_prompt")
        testee.onResponseState(tabId = "t1", status = "loading")
        testee.onResponseState(tabId = "t1", status = "ready")
        idle()

        verify(wideEventClient, times(1)).flowFinish(
            eq(1L),
            eq(FlowStatus.Success),
            eq(mapOf("status_reason" to "watched")),
        )
    }

    @Test
    fun `when error arrives before the new prompt's stream then flow finishes watched`() = runTest {
        promptInChat(streamStarted = false)

        testee.onResponseState(tabId = "t1", status = "error")
        idle()

        verify(wideEventClient).flowFinish(
            eq(1L),
            eq(FlowStatus.Success),
            eq(mapOf("status_reason" to "watched")),
        )
    }

    @Test
    fun `when outside-chat prompt streams before its page is visible then ready finishes watched`() = runTest {
        testee.onSelectedTabChanged(tabId = "t1", url = OTHER_URL)
        testee.onNativePromptSubmitted(tabId = "t1", reasoningEffort = "low", selectedTool = null)
        idle()

        testee.onResponseState(tabId = "t1", status = "start_stream:new_prompt")
        testee.onResponseState(tabId = "t1", status = "streaming")
        idle()
        onChat(tabId = "t1", chatId = "A")
        testee.onResponseState(tabId = "t1", status = "ready")
        idle()

        verify(wideEventClient).flowFinish(
            eq(1L),
            eq(FlowStatus.Success),
            eq(mapOf("status_reason" to "watched")),
        )
    }

    @Test
    fun `when only an idle ready arrives before leaving then the flow records left`() = runTest {
        promptInChat(streamStarted = false)

        testee.onResponseState(tabId = "t1", status = "ready")
        switchToOtherSite()

        verify(wideEventClient).flowStep(eq(1L), eq("left"), eq(true), eq(mapOf("left_via" to "tab_switched")))
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when stop tapped without leaving then finishes SUCCESS stopped`() = runTest {
        promptInChat()

        testee.onStopTapped(tabId = "t1")
        idle()
        testee.onResponseState(tabId = "t1", status = "ready")
        idle()

        verify(wideEventClient).flowFinish(
            eq(1L),
            eq(FlowStatus.Success),
            eq(mapOf("status_reason" to "stopped")),
        )
        verify(wideEventClient, times(1)).flowFinish(any(), any(), any())
    }

    @Test
    fun `when app backgrounded while loading then left step with left_via app_backgrounded and leave interval started`() = runTest {
        promptInChat()

        testee.onClose()
        idle()

        verify(wideEventClient).flowStep(eq(1L), eq("left"), eq(true), eq(mapOf("left_via" to "app_backgrounded")))
        verify(wideEventClient).intervalEnd(eq(1L), eq("prompt_to_leave_ms_bucketed"))
        verify(wideEventClient).intervalStart(eq(1L), eq("leave_to_return_ms_bucketed"), isNull(), eq(LEAVE_BUCKETS))
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when exit intent new_tab_opened then left_via new_tab_opened`() = runTest {
        promptInChat()

        testee.onExitIntent(tabId = "t1", trigger = DuckAiSessionExitTrigger.NEW_TAB_OPENED)
        testee.onSelectedTabChanged(tabId = "t2", url = null)
        idle()

        verify(wideEventClient).flowStep(eq(1L), eq("left"), eq(true), eq(mapOf("left_via" to "new_tab_opened")))
    }

    @Test
    fun `when tab switched without intent then left_via tab_switched`() = runTest {
        promptInChat()

        switchToOtherSite(tabId = "t2")

        verify(wideEventClient).flowStep(eq(1L), eq("left"), eq(true), eq(mapOf("left_via" to "tab_switched")))
    }

    @Test
    fun `when navigated off Duck ai then left_via other_navigation`() = runTest {
        promptInChat()

        switchToOtherSite(tabId = "t1")

        verify(wideEventClient).flowStep(eq(1L), eq("left"), eq(true), eq(mapOf("left_via" to "other_navigation")))
    }

    @Test
    fun `when the prompt's own chat is created before its stream then the flow stays and finishes watched`() = runTest {
        promptInChat(chatId = null, streamStarted = false)

        testee.onResponseState(tabId = "t1", status = "ready")
        testee.onNewChatCreated(tabId = "t1")
        testee.onResponseState(tabId = "t1", status = "start_stream:new_prompt")
        testee.onResponseState(tabId = "t1", status = "loading")
        testee.onResponseState(tabId = "t1", status = "ready")
        idle()

        verify(wideEventClient, never()).flowStep(any(), eq("left"), any(), any())
        verify(wideEventClient).flowFinish(
            eq(1L),
            eq(FlowStatus.Success),
            eq(mapOf("status_reason" to "watched")),
        )
    }

    @Test
    fun `when a chat with no ID yet is left for a new tab then the flow leaves via new_tab_opened and can return`() = runTest {
        promptInChat(chatId = null)

        testee.onExitIntent(tabId = "t1", trigger = DuckAiSessionExitTrigger.NEW_TAB_OPENED)
        testee.onNewChatCreated(tabId = "t1")
        onChat(tabId = "t2", chatId = null)
        idle()

        verify(wideEventClient).flowStep(eq(1L), eq("left"), eq(true), eq(mapOf("left_via" to "new_tab_opened")))

        onChat(tabId = "t1", chatId = null)
        idle()

        verify(wideEventClient).flowStep(eq(1L), eq("returned"), eq(true), eq(emptyMap()))
    }

    @Test
    fun `when new chat opened in a new tab then left_via new_tab_opened`() = runTest {
        promptInChat(tabId = "t1", chatId = "A")

        testee.onNewChatCreated(tabId = "t1")
        testee.onExitIntent(tabId = "t1", trigger = DuckAiSessionExitTrigger.NEW_TAB_OPENED)
        onChat(tabId = "t2", chatId = null)
        idle()

        verify(wideEventClient).flowStep(eq(1L), eq("left"), eq(true), eq(mapOf("left_via" to "new_tab_opened")))
    }

    @Test
    fun `when back exits the app then left_via back_or_close`() = runTest {
        promptInChat()

        testee.onExitIntent(tabId = "t1", trigger = DuckAiSessionExitTrigger.BACK_OR_CLOSE)
        testee.onClose()
        idle()

        verify(wideEventClient).flowStep(eq(1L), eq("left"), eq(true), eq(mapOf("left_via" to "back_or_close")))
    }

    @Test
    fun `when app closes with a non-back exit intent pending then left_via app_backgrounded`() = runTest {
        promptInChat()

        testee.onExitIntent(tabId = "t1", trigger = DuckAiSessionExitTrigger.NEW_TAB_OPENED)
        testee.onClose()
        idle()

        verify(wideEventClient).flowStep(eq(1L), eq("left"), eq(true), eq(mapOf("left_via" to "app_backgrounded")))
    }

    @Test
    fun `when chat switched while loading then ready from that tab does not touch the old flow`() = runTest {
        promptInChat(chatId = "A")
        onChat(tabId = "t1", chatId = "B")
        idle()

        testee.onResponseState(tabId = "t1", status = "ready")
        idle()

        verify(wideEventClient, never()).flowFinish(any(), any(), any())
        verify(wideEventClient, never()).flowStep(any(), eq("response_ready_while_away"), any(), any())
    }

    @Test
    fun `when chat id appears after prompt then it is adopted not switched`() = runTest {
        promptInChat(chatId = null)

        onChat(tabId = "t1", chatId = "A")
        idle()

        verify(wideEventClient, never()).flowStep(any(), eq("left"), any(), any())

        onChat(tabId = "t1", chatId = "B")
        idle()

        verify(wideEventClient).flowStep(eq(1L), eq("left"), eq(true), any())
    }

    @Test
    fun `when returned while loading then returned step with loading and leave interval ended`() = runTest {
        promptInChat()
        switchToOtherSite()

        onChat(tabId = "t1", chatId = "A")
        idle()

        verify(wideEventClient).flowStep(eq(1L), eq("returned"), eq(true), eq(emptyMap()))
        verify(wideEventClient).intervalEnd(eq(1L), eq("leave_to_return_ms_bucketed"))
    }

    @Test
    fun `when chat returns in a different tab then it counts as returned`() = runTest {
        promptInChat(tabId = "t1", chatId = "A")
        switchToOtherSite(tabId = "t2")

        onChat(tabId = "t2", chatId = "A")
        idle()

        verify(wideEventClient).flowStep(eq(1L), eq("returned"), eq(true), eq(emptyMap()))
    }

    @Test
    fun `when ready while away then response_ready_while_away and ready interval, ended on return`() = runTest {
        promptInChat()
        switchToOtherSite()

        testee.onResponseState(tabId = "t1", status = "ready")
        idle()

        verify(wideEventClient).flowStep(eq(1L), eq("response_ready_while_away"), eq(true), eq(emptyMap()))
        verify(wideEventClient).intervalStart(eq(1L), eq("ready_to_return_ms_bucketed"), isNull(), eq(LEAVE_BUCKETS))
        verify(wideEventClient, never()).intervalEnd(any(), eq("ready_to_return_ms_bucketed"))

        onChat(tabId = "t1", chatId = "A")
        idle()

        verify(wideEventClient).intervalEnd(eq(1L), eq("ready_to_return_ms_bucketed"))
        verify(wideEventClient).flowStep(eq(1L), eq("returned"), eq(true), eq(emptyMap()))
    }

    @Test
    fun `when error while away then no response_ready_while_away step`() = runTest {
        promptInChat()
        switchToOtherSite()
        testee.onResponseState(tabId = "t1", status = "error")
        idle()

        onChat(tabId = "t1", chatId = "A")
        idle()

        verify(wideEventClient, never()).flowStep(any(), eq("response_ready_while_away"), any(), any())
    }

    @Test
    fun `when stop tapped after returning then flow finishes stopped`() = runTest {
        promptInChat()
        switchToOtherSite()
        onChat(tabId = "t1", chatId = "A")
        idle()

        testee.onStopTapped(tabId = "t1")
        testee.onResponseState(tabId = "t1", status = "ready")
        switchToOtherSite()

        verify(wideEventClient).flowStep(eq(1L), eq("returned"), eq(true), eq(emptyMap()))
        verify(wideEventClient, times(1)).flowFinish(
            eq(1L),
            eq(FlowStatus.Success),
            eq(mapOf("status_reason" to "stopped")),
        )
    }

    @Test
    fun `appendix timeline`() = runTest {
        promptInChat()
        switchToOtherSite()
        onChat(tabId = "t1", chatId = "A")
        idle()
        switchToOtherSite()
        testee.onResponseState(tabId = "t1", status = "ready")
        idle()
        onChat(tabId = "t1", chatId = "A")
        idle()

        verify(wideEventClient, times(1)).flowStep(any(), eq("left"), any(), any())
        verify(wideEventClient, times(1)).flowStep(any(), eq("returned"), any(), any())
        verify(wideEventClient, times(1)).flowStep(any(), eq("response_ready_while_away"), any(), any())
        verify(wideEventClient).intervalEnd(eq(1L), eq("ready_to_return_ms_bucketed"))
        verify(wideEventClient, never()).flowFinish(any(), any(), any())

        switchToOtherSite()

        verify(wideEventClient).flowFinish(
            eq(1L),
            eq(FlowStatus.Success),
            eq(mapOf("status_reason" to "returned")),
        )
    }

    @Test
    fun `when returned and done then next native prompt records prompt_after_return, finishes returned and starts a new flow`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(1L), Result.success(2L))
        promptInChat()
        switchToOtherSite()
        testee.onResponseState(tabId = "t1", status = "ready")
        idle()
        onChat(tabId = "t1", chatId = "A")
        idle()

        testee.onNativePromptSubmitted(tabId = "t1", reasoningEffort = "low", selectedTool = null)
        idle()

        verify(wideEventClient).flowStep(eq(1L), eq("prompt_after_return"), eq(true), any())
        verify(wideEventClient).flowFinish(
            eq(1L),
            eq(FlowStatus.Success),
            eq(mapOf("status_reason" to "returned")),
        )
        verify(wideEventClient, times(2)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
        verify(wideEventClient).intervalStart(eq(2L), eq("prompt_to_leave_ms_bucketed"), isNull(), eq(PROMPT_BUCKETS))
    }

    @Test
    fun `when returned and done then leaving finishes returned without prompt_after_return`() = runTest {
        promptInChat()
        switchToOtherSite()
        testee.onResponseState(tabId = "t1", status = "ready")
        idle()
        onChat(tabId = "t1", chatId = "A")
        idle()

        switchToOtherSite()

        verify(wideEventClient).flowFinish(
            eq(1L),
            eq(FlowStatus.Success),
            eq(mapOf("status_reason" to "returned")),
        )
        verify(wideEventClient, never()).flowStep(any(), eq("prompt_after_return"), any(), any())
    }

    @Test
    fun `when returned and done then a stream started by the page records prompt_after_return and finishes returned`() = runTest {
        promptInChat()
        switchToOtherSite()
        testee.onResponseState(tabId = "t1", status = "ready")
        idle()
        onChat(tabId = "t1", chatId = "A")
        idle()

        testee.onResponseState(tabId = "t1", status = "start_stream:regenerate")
        testee.onResponseState(tabId = "t1", status = "error")
        idle()

        verify(wideEventClient).flowStep(eq(1L), eq("prompt_after_return"), eq(true), any())
        verify(wideEventClient).flowFinish(
            eq(1L),
            eq(FlowStatus.Success),
            eq(mapOf("status_reason" to "returned")),
        )
        assertEquals(emptyList<Long>(), flowStore.flows.map { it.flowId })
    }

    @Test
    fun `when tab closed while away then flow stays open`() = runTest {
        promptInChat()

        testee.onExitIntent(tabId = "t1", trigger = DuckAiSessionExitTrigger.BACK_OR_CLOSE)
        switchToOtherSite(tabId = "t2")
        switchToOtherSite(tabId = "t3")

        verify(wideEventClient).flowStep(eq(1L), eq("left"), eq(true), eq(mapOf("left_via" to "back_or_close")))
        verify(wideEventClient, times(1)).flowStep(any(), any(), any(), any())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when blocked then flow is aborted`() = runTest {
        promptInChat()

        testee.onResponseState(tabId = "t1", status = "blocked")
        idle()

        verify(wideEventClient).flowAbort(1L)
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when two chats load in two tabs then each gets its own flow`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(1L), Result.success(2L))
        promptInChat(tabId = "t1", chatId = "A")
        promptInChat(tabId = "t2", chatId = "B")

        testee.onResponseState(tabId = "t2", status = "ready")
        idle()

        verify(wideEventClient, times(2)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
        verify(wideEventClient).flowFinish(
            eq(2L),
            eq(FlowStatus.Success),
            eq(mapOf("status_reason" to "watched")),
        )
        verify(wideEventClient, never()).flowFinish(eq(1L), any(), any())
    }

    @Test
    fun `when native prompt submitted twice for the same tab before any response state then only one flow starts`() = runTest {
        promptInChat(streamStarted = false)

        testee.onNativePromptSubmitted(tabId = "t1", reasoningEffort = "low", selectedTool = null)
        idle()

        verify(wideEventClient, times(1)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `when page visible fires while app closed then no returned step`() = runTest {
        promptInChat()
        testee.onClose()
        idle()

        testee.onDuckAiPageVisible(tabId = "t1", url = "$DUCK_AI_URL?chatID=A")
        idle()

        verify(wideEventClient, never()).flowStep(any(), eq("returned"), any(), any())
    }

    @Test
    fun `when feature disabled then no flow starts`() = runTest {
        duckChatFeature.sendDuckAiResponseWaitWideEvent().setRawStoredState(Toggle.State(enable = false))

        promptInChat()

        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `when feature disabled mid-flow then flow is aborted`() = runTest {
        promptInChat()
        duckChatFeature.sendDuckAiResponseWaitWideEvent().setRawStoredState(Toggle.State(enable = false))

        testee.onResponseState(tabId = "t1", status = "ready")
        idle()

        verify(wideEventClient).flowAbort(1L)
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when native input disabled then no flow starts`() = runTest {
        duckChatFeature.nativeChatInput().setRawStoredState(Toggle.State(enable = false))

        promptInChat()

        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `when native sidebar disabled then no flow starts`() = runTest {
        duckChatFeature.nativeDuckAiSidebar().setRawStoredState(Toggle.State(enable = false))

        promptInChat()

        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `when native chat history disabled then no flow starts`() = runTest {
        duckChatFeature.useNativeStorageChatData().setRawStoredState(Toggle.State(enable = false))

        promptInChat()

        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `when duck ai disabled by the user then no flow starts`() = runTest {
        whenever(duckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(false)

        promptInChat()

        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `when duckai session onPromptSubmitted arrives then no flow starts`() = runTest {
        onChat(tabId = "t1", chatId = "A")
        testee.onPromptSubmitted(tabId = "t1")
        idle()

        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `when restarted with a stored flow that is no longer open then it is dropped`() = runTest {
        restart(stored = listOf(stored()), openIds = emptyList(), landingTabId = "t1", landingUrl = "$DUCK_AI_URL?chatID=A")

        assertEquals(emptyList<PersistedResponseWaitFlow>(), flowStore.flows)
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
        verify(wideEventClient, never()).flowStep(eq(7L), any(), any(), any())
    }

    @Test
    fun `when restarted with a returned and done flow then it finishes SUCCESS returned`() = runTest {
        restart(
            stored = listOf(stored(returned = true, done = true)),
            openIds = listOf(7L),
            landingTabId = null,
            landingUrl = null,
        )

        verify(wideEventClient).flowFinish(
            eq(7L),
            eq(FlowStatus.Success),
            eq(mapOf("status_reason" to "returned")),
        )
        assertEquals(emptyList<PersistedResponseWaitFlow>(), flowStore.flows)
    }

    @Test
    fun `when restored flow returns then returned step is recorded`() = runTest {
        restart(stored = listOf(stored()), openIds = listOf(7L), landingTabId = "t1", landingUrl = "$DUCK_AI_URL?chatID=A")

        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())
        verify(wideEventClient).flowStep(eq(7L), eq("returned"), eq(true), eq(emptyMap()))
        verify(wideEventClient).intervalEnd(eq(7L), eq("leave_to_return_ms_bucketed"))
    }

    @Test
    fun `when restored flow's chat opens in a different tab then it counts as returned`() = runTest {
        restart(stored = listOf(stored(tabId = "t1")), openIds = listOf(7L), landingTabId = "t2", landingUrl = "$DUCK_AI_URL?chatID=A")

        verify(wideEventClient).flowStep(eq(7L), eq("returned"), eq(true), eq(emptyMap()))
    }

    @Test
    fun `when callback is sent right after restart then it is processed after recovery`() = runTest {
        flowStore.flows = listOf(stored())
        stubOpenFlowIds(7L)

        testee = createTestee()
        testee.onOpen(isFreshLaunch = true)
        testee.onLaunchLandingResolved(tabId = "t1", url = "$DUCK_AI_URL?chatID=A")
        testee.onResponseState(tabId = "t1", status = "ready")
        testee.onClose()
        idle()

        verify(wideEventClient).flowStep(eq(7L), eq("returned"), eq(true), any())
        verify(wideEventClient).flowFinish(
            eq(7L),
            eq(FlowStatus.Success),
            eq(mapOf("status_reason" to "returned")),
        )
    }

    @Test
    fun `when restored flow returns and page sends ready then restart_stream then user leaves then flow stays open`() = runTest {
        restart(stored = listOf(stored()), openIds = listOf(7L), landingTabId = "t1", landingUrl = "$DUCK_AI_URL?chatID=A")

        testee.onResponseState(tabId = "t1", status = "ready")
        testee.onResponseState(tabId = "t1", status = "start_stream:restart_stream")
        testee.onResponseState(tabId = "t1", status = "loading")
        testee.onClose()
        idle()

        verify(wideEventClient, never()).flowFinish(any(), any(), any())
        assertEquals(listOf(7L), flowStore.flows.map { it.flowId })
    }

    @Test
    fun `when flow starts then store contains it`() = runTest {
        promptInChat(tabId = "t1", chatId = "A")

        assertEquals(listOf(stored(flowId = 1L, left = false)), flowStore.flows)
    }

    @Test
    fun `when flow finishes then store no longer contains it`() = runTest {
        promptInChat()

        testee.onResponseState(tabId = "t1", status = "ready")
        idle()

        assertEquals(emptyList<PersistedResponseWaitFlow>(), flowStore.flows)
    }

    @Test
    fun `when flow is closed by cleanup then it is dropped on next open and gets no further steps`() = runTest {
        promptInChat()
        testee.onClose()
        idle()
        stubOpenFlowIds()

        testee.onOpen(isFreshLaunch = false)
        testee.onLaunchLandingResolved(tabId = "t1", url = "$DUCK_AI_URL?chatID=A")
        testee.onResponseState(tabId = "t1", status = "ready")
        testee.onClose()
        idle()

        verify(wideEventClient, never()).flowStep(any(), eq("returned"), any(), any())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
        assertEquals(emptyList<PersistedResponseWaitFlow>(), flowStore.flows)
    }

    @Test
    fun `when feature disabled with open flows then all are aborted and store is cleared`() = runTest {
        restart(
            stored = listOf(stored(tabId = "t9", chatId = "Z")),
            openIds = listOf(7L),
            landingTabId = null,
            landingUrl = null,
        )
        promptInChat(tabId = "t1", chatId = "A")
        assertEquals(listOf(7L, 1L), flowStore.flows.map { it.flowId })

        duckChatFeature.sendDuckAiResponseWaitWideEvent().setRawStoredState(Toggle.State(enable = false))
        testee.onClose()
        idle()

        verify(wideEventClient).flowAbort(7L)
        verify(wideEventClient).flowAbort(1L)
        assertEquals(emptyList<PersistedResponseWaitFlow>(), flowStore.flows)
    }

    @Test
    fun `when restored tab-keyed flow was chat switched away then its tab showing Duck ai is not a return`() = runTest {
        restart(
            stored = listOf(stored(chatId = null, chatSwitchedAway = true)),
            openIds = listOf(7L),
            landingTabId = "t1",
            landingUrl = DUCK_AI_URL,
        )

        verify(wideEventClient, never()).flowStep(any(), eq("returned"), any(), any())
        assertEquals(listOf(stored(chatId = null, chatSwitchedAway = true)), flowStore.flows)
    }

    @Test
    fun `when restored flow never left then it is aborted and does not block a new flow on its tab`() = runTest {
        restart(stored = listOf(stored(left = false)), openIds = listOf(7L), landingTabId = "t1", landingUrl = "$DUCK_AI_URL?chatID=A")

        verify(wideEventClient).flowAbort(7L)
        assertEquals(emptyList<PersistedResponseWaitFlow>(), flowStore.flows)

        testee.onResponseState(tabId = "t1", status = "ready")
        testee.onNativePromptSubmitted(tabId = "t1", reasoningEffort = "low", selectedTool = null)
        idle()

        verify(wideEventClient, never()).flowFinish(eq(7L), any(), any())
        verify(wideEventClient).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `when restarted with the feature off then restored flows are aborted and store is cleared`() = runTest {
        duckChatFeature.sendDuckAiResponseWaitWideEvent().setRawStoredState(Toggle.State(enable = false))

        restart(
            stored = listOf(stored(), stored(flowId = 8L, tabId = "t2", chatId = "B", returned = true, done = true)),
            openIds = listOf(7L, 8L),
            landingTabId = null,
            landingUrl = null,
        )

        verify(wideEventClient).flowAbort(7L)
        verify(wideEventClient).flowAbort(8L)
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
        assertEquals(emptyList<PersistedResponseWaitFlow>(), flowStore.flows)
    }

    @Test
    fun `when getFlowIds fails at recovery then restored flows are kept`() = runTest {
        flowStore.flows = listOf(stored())
        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.failure(Exception("unavailable")))
        testee = createTestee()
        testee.onOpen(isFreshLaunch = true)
        testee.onLaunchLandingResolved(tabId = "t1", url = "$DUCK_AI_URL?chatID=A")
        idle()

        verify(wideEventClient).flowStep(eq(7L), eq("returned"), eq(true), eq(emptyMap()))
        assertEquals(listOf(7L), flowStore.flows.map { it.flowId })
    }

    @Test
    fun `when saving fails then it is retried after the next action`() = runTest {
        flowStore.failNextSave = true
        promptInChat(streamStarted = false)
        assertEquals(emptyList<PersistedResponseWaitFlow>(), flowStore.flows)

        testee.onSelectedTabChanged(tabId = "t1", url = "$DUCK_AI_URL?chatID=A")
        idle()

        assertEquals(listOf(1L), flowStore.flows.map { it.flowId })
    }

    private class FakeFlowStore : DuckAiResponseWaitFlowStore {
        var flows: List<PersistedResponseWaitFlow> = emptyList()
        var failNextSave = false

        override suspend fun load(): List<PersistedResponseWaitFlow> = flows

        override suspend fun save(flows: List<PersistedResponseWaitFlow>) {
            if (failNextSave) {
                failNextSave = false
                throw java.io.IOException("disk full")
            }
            this.flows = flows
        }
    }

    private companion object {
        const val DUCK_AI_URL = "https://duck.ai/"
        const val OTHER_URL = "https://example.com/"

        val PROMPT_BUCKETS = setOf(0.seconds, 2.seconds, 5.seconds, 10.seconds, 20.seconds, 30.seconds)
        val LEAVE_BUCKETS = setOf(0.seconds, 5.seconds, 10.seconds, 30.seconds, 60.seconds, 5.minutes, 15.minutes, 60.minutes)
    }
}
