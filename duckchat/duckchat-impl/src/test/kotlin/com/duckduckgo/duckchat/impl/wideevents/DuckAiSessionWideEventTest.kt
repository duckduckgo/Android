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
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DuckAiSessionWideEventTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val wideEventClient: WideEventClient = mock()
    private val duckChat: DuckChat = mock()
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)

    private lateinit var testee: RealDuckAiSessionWideEvent

    @Before
    fun setup() = runTest {
        duckChatFeature.sendDuckAiSessionWideEvent().setRawStoredState(Toggle.State(enable = true))
        whenever(duckChat.isDuckChatUrl(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as Uri).toString().startsWith(DUCK_AI_BASE_URL)
        }
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(1L))
        whenever(wideEventClient.flowStep(any(), any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.flowFinish(any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.flowAbort(any())).thenReturn(Result.success(Unit))

        testee = RealDuckAiSessionWideEvent(
            wideEventClient = wideEventClient,
            duckChat = duckChat,
            duckChatFeature = { duckChatFeature },
            dispatchers = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
        // Most tests are exercising behaviour once the app is already open; the app-closed gate itself
        // is covered by its own tests below.
        testee.onOpen(isFreshLaunch = true)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
    }

    private fun idle() = coroutineRule.testScope.testScheduler.advanceUntilIdle()

    private suspend fun startFlow(returnedFlowId: Long = 1L, tabId: String = "tab-1", url: String = DUCKAI_URL_A) {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(returnedFlowId))
        testee.onLaunchLandingResolved(tabId, url)
        idle()
    }

    @Test
    fun `when landing resolved then flow starts with duckai_session name and process-start cleanup`() = runTest {
        testee.onLaunchLandingResolved("tab-1", DUCKAI_URL_A)
        idle()

        verify(wideEventClient).flowStart(
            name = eq("duckai_session"),
            flowEntryPoint = isNull(),
            metadata = eq(mapOf("status_reason" to "app_terminated")),
            cleanupPolicy = eq(CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false, flowStatus = FlowStatus.Unknown)),
            samplingProbability = any(),
            definition = any(),
        )
    }

    @Test
    fun `when landing resolved again for the same tab then no new flow starts`() = runTest {
        startFlow(tabId = "tab-1")

        testee.onLaunchLandingResolved("tab-1", DUCKAI_URL_A)
        idle()

        verify(wideEventClient, times(1)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `when landing resolved for a different tab while another session is active then it is ignored`() = runTest {
        startFlow(tabId = "tab-1")

        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(2L))
        testee.onLaunchLandingResolved("tab-2", DUCKAI_URL_B)
        idle()

        verify(wideEventClient, never()).flowFinish(any(), any(), any())
        verify(wideEventClient, times(1)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `onLaunchLandingResolved establishes the url's chat id as the baseline`() = runTest {
        startFlow(tabId = "tab-1", url = DUCKAI_URL_A)

        // Same chat id observed again via a same-tab, still-duckai transition: no change.
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        idle()

        verify(wideEventClient, never()).flowStep(any(), eq("chat_id_changed"), any(), any())
    }

    @Test
    fun `a persisted Duck ai tab reported before any landing is only a baseline`() = runTest {
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        idle()

        verify(wideEventClient, never()).flowStart(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `when selected tab changes away from Duck ai with no pending exit then other_navigation is used`() = runTest {
        startFlow(tabId = "tab-1")
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A) // baseline for the observer's own tracking
        idle()

        testee.onSelectedTabChanged("tab-1", "https://example.com")
        idle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(1L),
            status = eq(FlowStatus.Success),
            metadata = eq(mapOf("status_reason" to "left_duckai", "exit_trigger" to "other_navigation")),
        )
    }

    @Test
    fun `a matching exit intent is used instead of other_navigation for a same-tab exit`() = runTest {
        startFlow(tabId = "tab-1")
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        idle()

        testee.onExitIntent("tab-1", DuckAiSessionExitTrigger.BACK_OR_CLOSE)
        testee.onSelectedTabChanged("tab-1", "https://example.com")
        idle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(1L),
            status = eq(FlowStatus.Success),
            metadata = eq(mapOf("status_reason" to "left_duckai", "exit_trigger" to "back_or_close")),
        )
    }

    @Test
    fun `when the selected tab changes to a different tab then tab_switched is used by default`() = runTest {
        startFlow(tabId = "tab-1")
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        idle()

        testee.onSelectedTabChanged("tab-2", "https://example.com")
        idle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(1L),
            status = eq(FlowStatus.Success),
            metadata = eq(mapOf("status_reason" to "left_duckai", "exit_trigger" to "tab_switched")),
        )
    }

    @Test
    fun `a matching new_tab_opened exit intent is used instead of tab_switched`() = runTest {
        startFlow(tabId = "tab-1")
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        idle()

        testee.onExitIntent("tab-1", DuckAiSessionExitTrigger.NEW_TAB_OPENED)
        testee.onSelectedTabChanged("tab-2", null)
        idle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(1L),
            status = eq(FlowStatus.Success),
            metadata = eq(mapOf("status_reason" to "left_duckai", "exit_trigger" to "new_tab_opened")),
        )
    }

    @Test
    fun `a matching fire_tab_opened exit intent is used instead of tab_switched`() = runTest {
        startFlow(tabId = "tab-1")
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        idle()

        testee.onExitIntent("tab-1", DuckAiSessionExitTrigger.FIRE_TAB_OPENED)
        testee.onSelectedTabChanged("tab-2", null)
        idle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(1L),
            status = eq(FlowStatus.Success),
            metadata = eq(mapOf("status_reason" to "left_duckai", "exit_trigger" to "fire_tab_opened")),
        )
    }

    @Test
    fun `a generic new tab intent does not overwrite an earlier fire tab intent`() = runTest {
        startFlow(tabId = "tab-1")
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        idle()

        testee.onExitIntent("tab-1", DuckAiSessionExitTrigger.FIRE_TAB_OPENED)
        testee.onExitIntent("tab-1", DuckAiSessionExitTrigger.NEW_TAB_OPENED)
        testee.onSelectedTabChanged("tab-2", null)
        idle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(1L),
            status = eq(FlowStatus.Success),
            metadata = eq(mapOf("status_reason" to "left_duckai", "exit_trigger" to "fire_tab_opened")),
        )
    }

    @Test
    fun `when switching between two Duck ai tabs then the old session ends and the new one starts`() = runTest {
        startFlow(tabId = "tab-1")
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        idle()

        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(2L))
        testee.onSelectedTabChanged("tab-2", DUCKAI_URL_B)
        idle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(1L),
            status = eq(FlowStatus.Success),
            metadata = eq(mapOf("status_reason" to "left_duckai", "exit_trigger" to "tab_switched")),
        )
        verify(wideEventClient, times(2)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `Back staying on Duck ai in the same tab does not end the session and clears the pending exit`() = runTest {
        startFlow(tabId = "tab-1")
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        idle()
        testee.onExitIntent("tab-1", DuckAiSessionExitTrigger.BACK_OR_CLOSE)

        // Back landed on another Duck.ai page in the same tab.
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_B)
        idle()

        verify(wideEventClient, never()).flowFinish(any(), any(), any())

        // The stale pending exit must not resurface on the next, unrelated real exit.
        testee.onSelectedTabChanged("tab-1", "https://example.com")
        idle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(1L),
            status = eq(FlowStatus.Success),
            metadata = eq(mapOf("status_reason" to "left_duckai", "exit_trigger" to "other_navigation")),
        )
    }

    @Test
    fun `when app backgrounded with no pending exit then flow finishes cancelled with app_backgrounded`() = runTest {
        startFlow(tabId = "tab-1")

        testee.onClose()
        idle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(1L),
            status = eq(FlowStatus.Cancelled),
            metadata = eq(mapOf("status_reason" to "app_backgrounded")),
        )
    }

    @Test
    fun `when app backgrounded with a matching pending back_or_close then flow finishes success with left_duckai`() = runTest {
        startFlow(tabId = "tab-1")
        testee.onExitIntent("tab-1", DuckAiSessionExitTrigger.BACK_OR_CLOSE)

        testee.onClose()
        idle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(1L),
            status = eq(FlowStatus.Success),
            metadata = eq(mapOf("status_reason" to "left_duckai", "exit_trigger" to "back_or_close")),
        )
    }

    @Test
    fun `when app backgrounded with a pending exit for another tab then it is discarded and app_backgrounded is used`() = runTest {
        startFlow(tabId = "tab-1")
        // A pending exit can only ever be recorded for the active tab, but guard the discard path too.
        testee.onExitIntent("tab-1", DuckAiSessionExitTrigger.TAB_SWITCHED)

        testee.onClose()
        idle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(1L),
            status = eq(FlowStatus.Cancelled),
            metadata = eq(mapOf("status_reason" to "app_backgrounded")),
        )
    }

    @Test
    fun `when app backgrounded with no active session then nothing is finished`() = runTest {
        testee.onClose()
        idle()

        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `onExitIntent is rejected when there is no active session`() = runTest {
        testee.onExitIntent("tab-1", DuckAiSessionExitTrigger.BACK_OR_CLOSE)
        idle()

        testee.onClose()
        idle()

        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `onExitIntent is rejected for a tab that is not the active session`() = runTest {
        startFlow(tabId = "tab-1")

        testee.onExitIntent("tab-2", DuckAiSessionExitTrigger.BACK_OR_CLOSE)
        idle()
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        idle()
        testee.onSelectedTabChanged("tab-2", "https://example.com")
        idle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(1L),
            status = eq(FlowStatus.Success),
            metadata = eq(mapOf("status_reason" to "left_duckai", "exit_trigger" to "tab_switched")),
        )
    }

    @Test
    fun `a pending exit recorded during ordinary browsing does not leak into a later Duck ai session on the same tab`() = runTest {
        // Back pressed on tab-1 while it's an ordinary (non-Duck.ai) page: no session is active yet.
        testee.onExitIntent("tab-1", DuckAiSessionExitTrigger.BACK_OR_CLOSE)

        // tab-1 later navigates to Duck.ai.
        startFlow(tabId = "tab-1")
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        idle()

        // Leaving Duck.ai through an unrelated link, with no real pending exit for this session.
        testee.onSelectedTabChanged("tab-1", "https://example.com")
        idle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(1L),
            status = eq(FlowStatus.Success),
            metadata = eq(mapOf("status_reason" to "left_duckai", "exit_trigger" to "other_navigation")),
        )
    }

    @Test
    fun `an exit intent recorded while the session is still starting is not rejected`() = runTest {
        val flowStartResult = CompletableDeferred<Result<Long>>()
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .doSuspendableAnswer { flowStartResult.await() }

        testee.onLaunchLandingResolved("tab-1", DUCKAI_URL_A)
        coroutineRule.testScope.testScheduler.runCurrent()

        // Back pressed immediately, before flowStart has resolved.
        testee.onExitIntent("tab-1", DuckAiSessionExitTrigger.BACK_OR_CLOSE)

        flowStartResult.complete(Result.success(1L))
        idle()

        testee.onClose()
        idle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(1L),
            status = eq(FlowStatus.Success),
            metadata = eq(mapOf("status_reason" to "left_duckai", "exit_trigger" to "back_or_close")),
        )
    }

    @Test
    fun `when a prompt is submitted for the active tab then prompt_submitted step is recorded once`() = runTest {
        startFlow(tabId = "tab-1")

        testee.onPromptSubmitted("tab-1")
        testee.onPromptSubmitted("tab-1")
        idle()

        verify(wideEventClient, times(1)).flowStep(wideEventId = eq(1L), stepName = eq("prompt_submitted"), success = any(), metadata = any())
    }

    @Test
    fun `when a prompt is submitted for a different tab then it is ignored`() = runTest {
        startFlow(tabId = "tab-1")

        testee.onPromptSubmitted("tab-2")
        idle()

        verify(wideEventClient, never()).flowStep(any(), eq("prompt_submitted"), any(), any())
    }

    @Test
    fun `when a new chat is created for the active tab then new_chat_created step is recorded once`() = runTest {
        startFlow(tabId = "tab-1")

        testee.onNewChatCreated("tab-1")
        testee.onNewChatCreated("tab-1")
        idle()

        verify(wideEventClient, times(1)).flowStep(wideEventId = eq(1L), stepName = eq("new_chat_created"), success = any(), metadata = any())
    }

    @Test
    fun `when a new chat is created for a different tab then it is ignored`() = runTest {
        startFlow(tabId = "tab-1")

        testee.onNewChatCreated("tab-2")
        idle()

        verify(wideEventClient, never()).flowStep(any(), eq("new_chat_created"), any(), any())
    }

    @Test
    fun `a different chat id after the baseline records chat_id_changed once without exposing the id`() = runTest {
        startFlow(tabId = "tab-1", url = DUCKAI_URL_A)
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        idle()

        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_B)
        idle()
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_C)
        idle()

        verify(wideEventClient, times(1)).flowStep(
            wideEventId = eq(1L),
            stepName = eq("chat_id_changed"),
            success = any(),
            metadata = argThat { none { (_, value) -> value.startsWith("chat-") } },
        )
    }

    @Test
    fun `chat id going from present to missing is recorded as a change`() = runTest {
        startFlow(tabId = "tab-1", url = DUCKAI_URL_A)
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        idle()

        testee.onSelectedTabChanged("tab-1", DUCK_AI_BASE_URL)
        idle()

        verify(wideEventClient, times(1)).flowStep(any(), eq("chat_id_changed"), any(), any())
    }

    @Test
    fun `no wide event call ever carries the observed chat id`() = runTest {
        startFlow(tabId = "tab-1", url = "$DUCK_AI_BASE_URL?chatID=super-secret-chat-id")
        testee.onSelectedTabChanged("tab-1", "$DUCK_AI_BASE_URL?chatID=super-secret-chat-id")
        idle()

        testee.onSelectedTabChanged("tab-1", "$DUCK_AI_BASE_URL?chatID=another-secret-id")
        idle()
        testee.onSelectedTabChanged("tab-1", "https://example.com")
        idle()

        verify(wideEventClient, never()).flowStep(any(), any(), any(), argThat { values.any { it.contains("secret") } })
        verify(wideEventClient, never()).flowFinish(any(), any(), argThat { values.any { it.contains("secret") } })
    }

    @Test
    fun `when the feature is disabled then the active session is aborted`() = runTest {
        startFlow(tabId = "tab-1")

        duckChatFeature.sendDuckAiSessionWideEvent().setRawStoredState(Toggle.State(enable = false))
        testee.onPromptSubmitted("tab-1")
        idle()

        verify(wideEventClient).flowAbort(1L)
    }

    @Test
    fun `when the feature is disabled then landing resolved does not start a flow`() = runTest {
        duckChatFeature.sendDuckAiSessionWideEvent().setRawStoredState(Toggle.State(enable = false))

        testee.onLaunchLandingResolved("tab-1", DUCKAI_URL_A)
        idle()

        verify(wideEventClient, never()).flowStart(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `a landing resolved that only reaches the actor after the app closed does not start a session`() = runTest {
        testee.onClose()
        idle()

        // A WebView page-load callback that was already in flight when the app closed.
        testee.onLaunchLandingResolved("tab-1", DUCKAI_URL_A)
        idle()

        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())

        // The next app-open's resolved landing is what re-enables starts.
        testee.onOpen(isFreshLaunch = false)
        testee.onLaunchLandingResolved("tab-1", DUCKAI_URL_A)
        idle()

        verify(wideEventClient).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `a selected-tab transition into Duck ai that only reaches the actor after the app closed does not start a session`() = runTest {
        testee.onSelectedTabChanged("tab-1", "https://example.com")
        idle()

        testee.onClose()
        idle()

        // A background tab update that was already in flight when the app closed.
        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        idle()

        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `a selected-tab transition into Duck ai before any landing has resolved this app-open does not start a session`() = runTest {
        // The app is open (per setup's onOpen), but nothing has reported a resolved landing yet.
        testee.onSelectedTabChanged("tab-1", "https://example.com")
        idle()

        testee.onSelectedTabChanged("tab-1", DUCKAI_URL_A)
        idle()

        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `a Duck ai page becoming visible before the launch has resolved does not start a session or resolve it`() = runTest {
        // A stale WebView callback racing FirstScreenHandler's still-pending async launch decision.
        testee.onDuckAiPageVisible("tab-1", DUCKAI_URL_A)
        idle()

        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())

        // Proves the call above didn't itself resolve the launch: only once the real launch landing is
        // reported (even to an unrelated, non-Duck.ai destination) can a page-visible callback start one.
        testee.onLaunchLandingResolved("tab-2", "https://example.com")
        testee.onDuckAiPageVisible("tab-1", DUCKAI_URL_A)
        idle()

        verify(wideEventClient).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `a Duck ai page becoming visible after the app closed does not start a session even once the launch had already resolved`() = runTest {
        testee.onLaunchLandingResolved("tab-2", "https://example.com")
        idle()

        testee.onClose()
        idle()

        // A WebView page-load callback that was already in flight when the app closed.
        testee.onDuckAiPageVisible("tab-1", DUCKAI_URL_A)
        idle()

        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `a launch landing resolved with no selected tab still resolves the launch without starting a session`() = runTest {
        testee.onLaunchLandingResolved(null, null)
        idle()

        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())

        // Proves the launch is resolved even with no tab: a Duck.ai tab added afterward this same
        // app-open can now start a session.
        testee.onDuckAiPageVisible("tab-1", DUCKAI_URL_A)
        idle()

        verify(wideEventClient).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    private companion object {
        const val DUCK_AI_BASE_URL = "https://duck.ai/"
        const val DUCKAI_URL_A = "https://duck.ai/?q=hi&chatID=chat-a"
        const val DUCKAI_URL_B = "https://duck.ai/?q=hi&chatID=chat-b"
        const val DUCKAI_URL_C = "https://duck.ai/?q=hi&chatID=chat-c"
    }
}
