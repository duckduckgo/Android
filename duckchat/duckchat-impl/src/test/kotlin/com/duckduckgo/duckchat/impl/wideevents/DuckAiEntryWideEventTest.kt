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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.app.tabs.model.DuckAiTabSessionRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckAiSessionExitTrigger
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatEntryPoint
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DuckAiEntryWideEventTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val inputMode: DuckChatInputModeState = mock()
    private val pixels: DuckChatPixels = mock()
    private val tabSessions: DuckAiTabSessionRepository = mock()
    private val client: WideEventClient = mock()
    private val duckChat: DuckChat = mock()
    private val voiceSessions = MutableStateFlow<Set<String>>(emptySet())
    private val feature = FakeFeatureToggleFactory.create(DuckAiEntryWideEventFeature::class.java)
    private val testee by lazy {
        DuckAiEntryWideEvent(
            client,
            lazy {
                duckChat
            },
            lazy { feature },
            coroutineRule.testDispatcherProvider,
            lazy { pixels },
            lazy { inputMode },
            tabSessions,
            coroutineRule.testScope,
        )
    }

    @Before
    fun setup() = runTest {
        whenever(duckChat.isEnabled()).thenReturn(true)
        whenever(duckChat.activeVoiceChatSessions).thenReturn(voiceSessions)
        whenever(duckChat.isVoiceChatSessionActive(any())).thenAnswer { it.getArgument<String>(0) in voiceSessions.value }
        whenever(inputMode.getSelectedInputMode()).thenReturn(NativeInputState.InputMode.SEARCH_AND_DUCK_AI)
        feature.self().setRawStoredState(Toggle.State(true))
        whenever(duckChat.isDuckChatUrl(any())).thenAnswer { it.getArgument<android.net.Uri>(0).host == "duck.ai" }
        whenever(client.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(1L), Result.success(2L))
        whenever(client.intervalStart(any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.success(Unit))
        whenever(client.intervalEnd(any(), any())).thenReturn(Result.success(100.milliseconds))
        whenever(client.flowStep(any(), any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(client.flowFinish(any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(client.flowAbort(any())).thenReturn(Result.success(Unit))
    }

    @Test
    fun `entry starts with bounded metadata and bucketed intervals`() = runTest {
        testee.onEntry(DuckChatEntryPoint.ADDRESS_BAR_PROMPT, hasPrompt = true)
        coroutineRule.testScope.advanceUntilIdle()

        verify(client).flowStart(
            name = eq("duckai_entry"),
            flowEntryPoint = anyOrNull(),
            metadata = eq(
                mapOf(
                    "source" to "address_bar_prompt",
                    "voice" to "false",
                    "has_prompt" to "true",
                    "chat_state" to "new",
                    "surface" to "duck_ai_tab",
                    "prompt_submitted" to "false",
                    "status_reason" to "app_terminated",
                ),
            ),
            cleanupPolicy = eq(CleanupPolicy.OnProcessStart(false, FlowStatus.Unknown)),
            samplingProbability = eq(1.0f),
            definition = any(),
        )
        verify(client).intervalStart(1L, "time_in_duckai_ms_bucketed", buckets = WideEventClient.DEFAULT_INTERVAL_BUCKETS)
        verify(client).intervalStart(1L, "time_to_first_prompt_ms_bucketed", buckets = WideEventClient.DEFAULT_INTERVAL_BUCKETS)
    }

    @Test
    fun `voice flag is scoped to the entry that started voice chat`() = runTest {
        testee.onEntry(DuckChatEntryPoint.DUCK_AI_NEW_CHAT, hasPrompt = false, voice = true)
        testee.onEntry(DuckChatEntryPoint.ADDRESS_BAR_PROMPT, hasPrompt = true)
        coroutineRule.testScope.advanceUntilIdle()

        verify(client).flowStart(
            any(),
            anyOrNull(),
            argThat { get("source") == "duck_ai_new_chat" && get("voice") == "true" && get("has_prompt") == "false" },
            any(),
            any(),
            any(),
        )
        verify(client).flowStart(
            any(),
            anyOrNull(),
            argThat { get("source") == "address_bar_prompt" && get("voice") == "false" && get("has_prompt") == "true" },
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `first visible entry URL determines existing chat state only once`() = runTest {
        testee.onEntry(DuckChatEntryPoint.CHAT_HISTORY_OPEN_CHAT, false)
        testee.onDuckAiPageVisible("tab", "https://duck.ai/?chatID=local-only")
        testee.onDuckAiPageVisible("tab", "https://duck.ai/")
        testee.onClose()
        coroutineRule.testScope.advanceUntilIdle()

        verify(client).flowStep(1L, "entry_resolved", metadata = mapOf("chat_state" to "existing"))
        verify(client, times(1)).flowStep(eq(1L), eq("entry_resolved"), any(), any())
    }

    @Test
    fun `prompt is persisted once and entry succeeds on confirmed exit`() = runTest {
        testee.onEntry(DuckChatEntryPoint.ADDRESS_BAR_ICON, false)
        testee.onDuckAiPageVisible("tab", "https://duck.ai/")
        testee.onPromptSubmitted("other")
        testee.onPromptSubmitted("tab")
        testee.onPromptSubmitted("tab")
        testee.onExitIntent("tab", DuckAiSessionExitTrigger.NEW_TAB_OPENED)
        testee.onSelectedTabChanged("other", "https://example.com")
        coroutineRule.testScope.advanceUntilIdle()

        verify(client, times(1)).intervalEnd(1L, "time_to_first_prompt_ms_bucketed")
        verify(client).flowStep(1L, "prompt_submitted", metadata = mapOf("prompt_submitted" to "true"))
        verify(client).flowFinish(1L, FlowStatus.Success, mapOf("status_reason" to "left_duckai", "exit_trigger" to "new_tab_opened"))
    }

    @Test
    fun `new entry finishes previous before starting next`() = runTest {
        testee.onEntry(DuckChatEntryPoint.ADDRESS_BAR_ICON, false)
        testee.onEntry(DuckChatEntryPoint.SERP, true)
        coroutineRule.testScope.advanceUntilIdle()

        inOrder(client) {
            verify(client).flowFinish(1L, FlowStatus.Success, mapOf("status_reason" to "new_entry"))
            verify(client).flowStart(eq("duckai_entry"), anyOrNull(), argThat { get("source") == "serp" }, any(), any(), any())
        }
    }

    @Test
    fun `background ends unengaged entry without first prompt interval`() = runTest {
        testee.onEntry(DuckChatEntryPoint.ADDRESS_BAR_ICON, false)
        testee.onClose()
        testee.onClose()
        coroutineRule.testScope.advanceUntilIdle()

        verify(client).intervalEnd(1L, "time_in_duckai_ms_bucketed")
        verify(client, never()).intervalEnd(1L, "time_to_first_prompt_ms_bucketed")
        verify(client).flowFinish(1L, FlowStatus.Cancelled, mapOf("status_reason" to "app_backgrounded"))
    }

    @Test
    fun `contextual entry carries prompt and existing chat state`() = runTest {
        testee.onContextualSheetOpened("tab", true, "https://duck.ai/?chatID=local-only", voice = true)
        testee.onPromptSubmitted("tab")
        testee.onContextualSheetClosed("tab")
        coroutineRule.testScope.advanceUntilIdle()

        verify(client).flowStart(
            eq("duckai_entry"),
            anyOrNull(),
            argThat {
                get("source") == "contextual_chat" && get("surface") == "contextual_sheet" &&
                    get("has_prompt") == "true" && get("chat_state") == "existing" && get("voice") == "true"
            },
            any(),
            any(),
            any(),
        )
        verify(client).flowFinish(1L, FlowStatus.Success, mapOf("status_reason" to "left_duckai", "exit_trigger" to "back_or_close"))
    }

    @Test
    fun `app open pixel fires once and attributes the landed tab with wide events disabled`() = runTest {
        feature.self().setRawStoredState(Toggle.State(false))
        testee.onForeground()
        testee.onLaunchLandingResolved("tab", "https://duck.ai/")
        testee.onLaunchLandingResolved("tab", "https://duck.ai/")
        coroutineRule.testScope.advanceUntilIdle()

        verify(pixels).sendDuckChatEntryPixel(DuckChatEntryPoint.APP_OPEN, false, false, true, true)
        verify(tabSessions).setPendingEntryPointSource("app_open")
        verify(tabSessions).tryClaimEntryPointSource("tab", "https://duck.ai/")
        verifyNoInteractions(client)
    }

    @Test
    fun `ordinary landing reports one pixel and one wide event`() = runTest {
        testee.onForeground()
        testee.onLaunchLandingResolved("tab", "https://duck.ai/")
        testee.onLaunchLandingResolved("tab", "https://duck.ai/")
        coroutineRule.testScope.advanceUntilIdle()

        verify(pixels).sendDuckChatEntryPixel(DuckChatEntryPoint.APP_OPEN, false, false, true, true)
        verify(client).flowStart(any(), anyOrNull(), argThat { get("source") == "app_open" }, any(), any(), any())
        verify(client, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `explicit Duck ai launch and non chat landing do not report app open pixel`() = runTest {
        testee.onForeground()
        testee.onLaunchLandingResolved("tab", "https://example.com")
        testee.onLaunchIntentReceived(opensDuckAi = true)
        testee.onLaunchLandingResolved("tab", "https://duck.ai/")
        testee.onLaunchIntentHandled()
        coroutineRule.testScope.advanceUntilIdle()

        verifyNoInteractions(pixels)
    }

    @Test
    fun `swiping onto Duck ai reports and binds the destination before a prompt`() = runTest {
        testee.onSelectedTabChanged("old", "https://example.com")
        testee.onTabSwiped("chat", "https://duck.ai/?chatID=local-only")
        testee.onSelectedTabChanged("chat", "https://duck.ai/?chatID=local-only")
        testee.onPromptSubmitted("chat")
        testee.onClose()
        coroutineRule.testScope.advanceUntilIdle()

        verify(pixels).sendDuckChatEntryPixel(DuckChatEntryPoint.TAB_SWIPE, false, false, true, true)
        verify(tabSessions).tryClaimEntryPointSource("chat", "https://duck.ai/?chatID=local-only")
        verify(
            client,
        ).flowStart(any(), anyOrNull(), argThat { get("source") == "tab_swipe" && get("chat_state") == "existing" }, any(), any(), any())
        verify(client).flowStep(1L, "prompt_submitted", metadata = mapOf("prompt_submitted" to "true"))
        verify(client).flowFinish(1L, FlowStatus.Cancelled, mapOf("status_reason" to "app_backgrounded"))
    }

    @Test
    fun `swiping onto web page or same selected tab does not report an entry`() = runTest {
        testee.onSelectedTabChanged("chat", "https://duck.ai/")
        testee.onTabSwiped("chat", "https://duck.ai/")
        testee.onTabSwiped("web", "https://example.com")
        testee.onTabSwiped("empty", null)
        coroutineRule.testScope.advanceUntilIdle()

        verifyNoInteractions(pixels, client)
    }

    @Test
    fun `swiping reports entry pixel while wide event flag is off`() = runTest {
        feature.self().setRawStoredState(Toggle.State(false))
        testee.onTabSwiped("chat", "https://duck.ai/")
        coroutineRule.testScope.advanceUntilIdle()

        verify(pixels).sendDuckChatEntryPixel(DuckChatEntryPoint.TAB_SWIPE, false, false, true, true)
        verifyNoInteractions(client)
    }

    @Test
    fun `app open does not report input screen enabled when Duck ai is disabled`() = runTest {
        whenever(duckChat.isEnabled()).thenReturn(false)
        testee.onForeground()
        testee.onLaunchLandingResolved("tab", "https://duck.ai/")
        coroutineRule.testScope.advanceUntilIdle()

        verify(pixels).sendDuckChatEntryPixel(DuckChatEntryPoint.APP_OPEN, false, false, false, false)
    }

    @Test
    fun `returning to a live voice chat preserves its entry without an app open pixel`() = runTest {
        voiceSessions.value = setOf("tab")
        testee.onForeground()
        testee.onEntry(DuckChatEntryPoint.DUCK_AI_NEW_CHAT, false)
        testee.onDuckAiPageVisible("tab", "https://duck.ai/")
        testee.onClose()
        testee.onForeground()
        testee.onLaunchLandingResolved("tab", "https://duck.ai/")
        coroutineRule.testScope.advanceUntilIdle()

        verify(client, times(1)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
        verify(client, never()).flowFinish(any(), any(), any())
        verifyNoInteractions(pixels)

        voiceSessions.value = emptySet()
        testee.onLaunchLandingResolved("tab", "https://duck.ai/")
        coroutineRule.testScope.advanceUntilIdle()
        verifyNoInteractions(pixels)
        verify(client, times(1)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
    }

    @Test
    fun `voice session stream completing without a voice end preserves the entry`() = coroutineRule.testScope.runTest {
        voiceSessions.value = setOf("tab")
        whenever(duckChat.activeVoiceChatSessions).thenReturn(flowOf(setOf("tab")))
        testee.onForeground()
        testee.onEntry(DuckChatEntryPoint.DUCK_AI_NEW_CHAT, false)
        testee.onDuckAiPageVisible("tab", "https://duck.ai/")

        testee.onClose()
        coroutineRule.testScope.advanceUntilIdle()

        verify(client, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `voice ending while backgrounded completes the preserved entry`() = runTest {
        voiceSessions.value = setOf("tab")
        testee.onForeground()
        testee.onEntry(DuckChatEntryPoint.DUCK_AI_NEW_CHAT, false)
        testee.onDuckAiPageVisible("tab", "https://duck.ai/")
        testee.onClose()
        coroutineRule.testScope.advanceUntilIdle()
        verify(client, never()).flowFinish(any(), any(), any())

        voiceSessions.value = emptySet()
        coroutineRule.testScope.advanceUntilIdle()

        verify(client).flowFinish(1L, FlowStatus.Cancelled, mapOf("status_reason" to "app_backgrounded"))
    }

    @Test
    fun `app landing starts only for Duck ai`() = runTest {
        testee.onForeground()
        testee.onLaunchLandingResolved("tab", "https://example.com")
        coroutineRule.testScope.advanceUntilIdle()
        verifyNoInteractions(client)
        testee.onLaunchLandingResolved("tab", "https://duck.ai/?chatID=local-only")
        coroutineRule.testScope.advanceUntilIdle()
        verify(client).flowStart(
            eq("duckai_entry"),
            anyOrNull(),
            argThat { get("source") == "app_open" && get("chat_state") == "existing" && get("has_prompt") == "false" },
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `launch landing followed by explicit entry does not start app open flow`() = runTest {
        testee.onLaunchLandingResolved("old", "https://duck.ai/")
        coroutineRule.testScope.advanceUntilIdle()
        verifyNoInteractions(client)

        testee.onEntry(DuckChatEntryPoint.DEEP_LINK_OTHER, true)
        testee.onForeground()
        coroutineRule.testScope.advanceUntilIdle()

        verify(client, times(1)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
        verify(client).flowStart(
            eq("duckai_entry"),
            anyOrNull(),
            argThat { get("source") == "deep_link_other" },
            any(),
            any(),
            any(),
        )
        verify(client, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `pending intent prevents app open even after foreground and delayed entry`() = runTest {
        testee.onLaunchIntentReceived()
        testee.onLaunchLandingResolved("old", "https://duck.ai/")
        testee.onForeground()
        coroutineRule.testScope.advanceUntilIdle()
        coroutineRule.testScope.advanceTimeBy(5_000)
        verifyNoInteractions(client)

        testee.onEntry(DuckChatEntryPoint.DEEP_LINK_OTHER, true, opensNewTab = true)
        testee.onLaunchIntentHandled()
        testee.onLaunchLandingResolved("old", "https://duck.ai/")
        coroutineRule.testScope.advanceUntilIdle()

        verify(client, times(1)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
        verify(client).flowStart(
            eq("duckai_entry"),
            anyOrNull(),
            argThat { get("source") == "deep_link_other" },
            any(),
            any(),
            any(),
        )
        verify(client, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `Duck ai intent suppresses app open when entry is reported asynchronously`() = runTest {
        testee.onLaunchIntentReceived(opensDuckAi = true)
        testee.onLaunchLandingResolved("old", "https://duck.ai/")
        testee.onLaunchIntentHandled()
        testee.onForeground()
        coroutineRule.testScope.advanceUntilIdle()
        verifyNoInteractions(client)

        testee.onEntry(DuckChatEntryPoint.WIDGET_FAVORITE, false, opensNewTab = true)
        coroutineRule.testScope.advanceUntilIdle()
        verify(client, times(1)).flowStart(any(), anyOrNull(), argThat { get("source") == "widget_favorite" }, any(), any(), any())
        verify(client, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `ordinary launch starts app open after intent handling and resumes after close`() = runTest {
        testee.onLaunchIntentReceived()
        testee.onLaunchLandingResolved("tab", "https://duck.ai/")
        testee.onForeground()
        coroutineRule.testScope.advanceUntilIdle()
        verifyNoInteractions(client)

        testee.onLaunchIntentHandled()
        coroutineRule.testScope.advanceUntilIdle()
        verify(client, times(1)).flowStart(any(), anyOrNull(), argThat { get("source") == "app_open" }, any(), any(), any())

        testee.onClose()
        testee.onLaunchLandingResolved("tab", "https://duck.ai/")
        testee.onForeground()
        coroutineRule.testScope.advanceUntilIdle()
        verify(client, times(2)).flowStart(any(), anyOrNull(), argThat { get("source") == "app_open" }, any(), any(), any())
    }

    @Test
    fun `same tab entry can resolve on the previously selected tab`() = runTest {
        testee.onSelectedTabChanged("tab", "https://duck.ai/")
        testee.onEntry(DuckChatEntryPoint.DIRECT_URL, true, opensNewTab = false)
        testee.onDuckAiPageVisible("tab", "https://duck.ai/")
        testee.onPromptSubmitted("tab")
        testee.onClose()
        coroutineRule.testScope.advanceUntilIdle()

        verify(client).flowStep(1L, "entry_resolved", metadata = mapOf("chat_state" to "new"))
        verify(client).flowFinish(1L, FlowStatus.Cancelled, mapOf("status_reason" to "app_backgrounded"))
    }

    @Test
    fun `new tab entry ignores old tab callbacks and succeeds after prompt and back`() = runTest {
        testee.onSelectedTabChanged("old", "https://duck.ai/")
        testee.onEntry(DuckChatEntryPoint.DEEP_LINK_OTHER, true, opensNewTab = true)
        testee.onDuckAiPageVisible("old", "https://duck.ai/")
        testee.onSelectedTabChanged("old", "https://duck.ai/")
        testee.onSelectedTabChanged("new", null)
        testee.onSelectedTabChanged("new", "https://duck.ai/?prompt=1")
        coroutineRule.testScope.advanceUntilIdle()
        verify(client, never()).flowFinish(any(), any(), any())
        verify(client, never()).intervalEnd(1L, "time_in_duckai_ms_bucketed")

        coroutineRule.testScope.advanceTimeBy(14_000)
        testee.onPromptSubmitted("new")
        testee.onExitIntent("new", DuckAiSessionExitTrigger.BACK_OR_CLOSE)
        testee.onSelectedTabChanged("old", "https://duck.ai/")
        testee.onClose()
        coroutineRule.testScope.advanceUntilIdle()

        verify(client, times(1)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
        verify(client).flowStep(1L, "prompt_submitted", metadata = mapOf("prompt_submitted" to "true"))
        verify(client).flowStep(1L, "entry_resolved", metadata = mapOf("chat_state" to "new"))
        verify(client, times(1)).intervalEnd(1L, "time_in_duckai_ms_bucketed")
        verify(client, times(1)).flowFinish(any(), any(), any())
        verify(client).flowFinish(1L, FlowStatus.Success, mapOf("status_reason" to "left_duckai", "exit_trigger" to "back_or_close"))
    }

    @Test
    fun `all entry points opening a new tab ignore prompts and visibility from the old tab`() = runTest {
        whenever(client.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(1L))
        DuckChatEntryPoint.entries.forEach { entryPoint ->
            testee.onSelectedTabChanged("old", "https://duck.ai/")
            testee.onEntry(entryPoint, true, opensNewTab = true)
            testee.onPromptSubmitted("old")
            testee.onDuckAiPageVisible("old", "https://duck.ai/")
            testee.onSelectedTabChanged("new", "https://duck.ai/")
            coroutineRule.testScope.advanceUntilIdle()
            verify(client, never()).flowFinish(any(), any(), any())
            verify(client, never()).flowStep(any(), eq("prompt_submitted"), any(), any())

            testee.onPromptSubmitted("new")
            testee.onSelectedTabChanged("old", "https://duck.ai/")
            testee.onClose()
            coroutineRule.testScope.advanceUntilIdle()
            verify(client, times(1)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
            verify(client).flowStep(1L, "prompt_submitted", metadata = mapOf("prompt_submitted" to "true"))
            verify(client).flowFinish(1L, FlowStatus.Success, mapOf("status_reason" to "left_duckai", "exit_trigger" to "tab_switched"))
            clearInvocations(client)
        }
    }

    @Test
    fun `disabled toggle aborts active entry and prevents another start`() = runTest {
        testee.onEntry(DuckChatEntryPoint.ADDRESS_BAR_ICON, false)
        coroutineRule.testScope.advanceUntilIdle()
        feature.self().setRawStoredState(Toggle.State(false))
        testee.onEntry(DuckChatEntryPoint.SERP, true)
        testee.onClose()
        coroutineRule.testScope.advanceUntilIdle()

        verify(client).flowAbort(1L)
        verify(client, times(1)).flowStart(any(), anyOrNull(), any(), any(), any(), any())
        verify(client, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `same tab navigation cancels exit intent and sheet dismissal cannot close tab entry`() = runTest {
        testee.onEntry(DuckChatEntryPoint.CONTEXTUAL_CHAT, false)
        testee.onDuckAiPageVisible("tab", "https://duck.ai/")
        testee.onExitIntent("tab", DuckAiSessionExitTrigger.BACK_OR_CLOSE)
        testee.onSelectedTabChanged("tab", "https://duck.ai/?chatID=local-only")
        testee.onContextualSheetClosed("tab")
        coroutineRule.testScope.advanceUntilIdle()
        verify(client, never()).flowFinish(any(), any(), any())
        testee.onSelectedTabChanged("tab", "https://example.com")
        coroutineRule.testScope.advanceUntilIdle()
        verify(client).flowFinish(1L, FlowStatus.Success, mapOf("status_reason" to "left_duckai", "exit_trigger" to "other_navigation"))
    }

    @Test
    fun `repeated selected tab emissions preserve pending exit trigger`() = runTest {
        testee.onForeground()
        testee.onLaunchLandingResolved("tab", "https://duck.ai/")
        testee.onExitIntent("tab", DuckAiSessionExitTrigger.FIRE_TAB_OPENED)
        testee.onSelectedTabChanged("tab", "https://duck.ai/")
        testee.onSelectedTabChanged("fire", null)
        coroutineRule.testScope.advanceUntilIdle()
        verify(client).flowFinish(1L, FlowStatus.Success, mapOf("status_reason" to "left_duckai", "exit_trigger" to "fire_tab_opened"))
    }

    @Test
    fun `prompt before page visibility is counted and background cancels`() = runTest {
        testee.onEntry(DuckChatEntryPoint.ADDRESS_BAR_PROMPT, true)
        testee.onPromptSubmitted("tab")
        testee.onClose()
        coroutineRule.testScope.advanceUntilIdle()
        verify(client).intervalEnd(1L, "time_to_first_prompt_ms_bucketed")
        verify(client).flowFinish(1L, FlowStatus.Cancelled, mapOf("status_reason" to "app_backgrounded"))
    }

    @Test
    fun `search intent finishes only after active tab navigates away`() = runTest {
        testee.onEntry(DuckChatEntryPoint.ADDRESS_BAR_ICON, false)
        testee.onDuckAiPageVisible("tab", "https://duck.ai/")
        testee.onSearchStarted("tab")
        coroutineRule.testScope.advanceUntilIdle()
        verify(client, never()).flowFinish(any(), any(), any())

        testee.onSelectedTabChanged("tab", "https://duckduckgo.com/?q=example")
        coroutineRule.testScope.advanceUntilIdle()
        verify(client).flowFinish(1L, FlowStatus.Success, mapOf("status_reason" to "left_duckai", "exit_trigger" to "search_started"))
    }

    @Test
    fun `search in another tab cannot set the active entry exit trigger`() = runTest {
        testee.onEntry(DuckChatEntryPoint.ADDRESS_BAR_ICON, false)
        testee.onDuckAiPageVisible("tab", "https://duck.ai/")
        testee.onSearchStarted("other")
        testee.onSelectedTabChanged("tab", "https://example.com")
        coroutineRule.testScope.advanceUntilIdle()
        verify(client).flowFinish(1L, FlowStatus.Success, mapOf("status_reason" to "left_duckai", "exit_trigger" to "other_navigation"))
    }

    @Test
    fun `deleting the active chat ends the entry once without exposing its identifier`() = runTest {
        testee.onEntry(DuckChatEntryPoint.CHAT_HISTORY_OPEN_CHAT, false)
        testee.onDuckAiPageVisible("tab", "https://duck.ai/?chatID=local-only")
        testee.onChatsDeleted(setOf("local-only"))
        testee.onSelectedTabChanged("tab", "https://duck.ai/")
        testee.onClose()
        coroutineRule.testScope.advanceUntilIdle()
        verify(client).flowFinish(1L, FlowStatus.Success, mapOf("status_reason" to "left_duckai", "exit_trigger" to "chat_deleted"))
        verify(client, times(1)).flowFinish(any(), any(), any())
    }

    @Test
    fun `deleting other chats does not end the active entry`() = runTest {
        testee.onContextualSheetOpened("tab", false, "https://duck.ai/?chatID=local-only")
        testee.onChatsDeleted(setOf("other"))
        coroutineRule.testScope.advanceUntilIdle()
        verify(client, never()).flowFinish(any(), any(), any())

        testee.onChatsDeleted(setOf("other", "local-only"))
        coroutineRule.testScope.advanceUntilIdle()
        verify(client).flowFinish(1L, FlowStatus.Success, mapOf("status_reason" to "left_duckai", "exit_trigger" to "chat_deleted"))
    }

    @Test
    fun `failed start does not create intervals or finish a flow`() = runTest {
        whenever(client.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.failure(IllegalStateException()))
        testee.onEntry(DuckChatEntryPoint.ADDRESS_BAR_ICON, false)
        testee.onPromptSubmitted("tab")
        testee.onClose()
        coroutineRule.testScope.advanceUntilIdle()
        verify(client, never()).intervalStart(any(), any(), anyOrNull(), anyOrNull())
        verify(client, never()).flowFinish(any(), any(), any())
    }
}
