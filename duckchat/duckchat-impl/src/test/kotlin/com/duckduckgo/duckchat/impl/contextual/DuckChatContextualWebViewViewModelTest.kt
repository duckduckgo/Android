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

package com.duckduckgo.duckchat.impl.contextual

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChatEntryPoint
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.helper.NativeAction
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper
import com.duckduckgo.duckchat.impl.history.ChatHistoryItem
import com.duckduckgo.duckchat.impl.history.ChatHistoryRepository
import com.duckduckgo.duckchat.impl.models.ChatType
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.store.DuckChatContextualDataStore
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DuckChatContextualWebViewViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: DuckChatContextualWebViewViewModel
    private val duckChat: com.duckduckgo.duckchat.api.DuckChat = FakeDuckChat()
    private val duckChatInternal: DuckChatInternal = mock()
    private val duckChatJSHelper: DuckChatJSHelper = mock()
    private val contextualDataStore = FakeDuckChatContextualDataStore()
    private val timeProvider = FakeDuckChatContextualTimeProvider()
    private val sessionTimeoutProvider = FakeDuckChatContextualSessionTimeoutProvider()
    private val duckChatPixels: DuckChatPixels = mock()
    private val duckChatFeature: DuckChatFeature = mock()
    private val contextualFireButtonToggle: Toggle = mock()
    private val modelManager: com.duckduckgo.duckchat.impl.models.DuckAiModelManager = mock()
    private val chatHistoryRepository: ChatHistoryRepository = mock()
    private val contextualEntryPromptStore: ContextualEntryPromptStore = mock()
    private val recentChatsFlow = MutableStateFlow<List<ChatHistoryItem>>(emptyList())

    private val serializedPageData =
        """
        {
            "title": "Page Title",
            "url": "https://example.com",
            "content": "Extracted DOM text...",
            "truncated": false,
            "fullContentLength": 1234
        }
        """.trimIndent()

    @Before
    fun setup() {
        whenever(duckChatFeature.contextualFireButton()).thenReturn(contextualFireButtonToggle)
        whenever(contextualFireButtonToggle.isEnabled()).thenReturn(false)
        whenever(chatHistoryRepository.observeChats()).thenReturn(recentChatsFlow)
        whenever(duckChatInternal.isAutomaticContextAttachmentEnabled()).thenReturn(true)
        whenever(duckChatJSHelper.onNativeAction(NativeAction.NEW_CHAT)).thenReturn(
            SubscriptionEventData(RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME, "submitNewChatAction", JSONObject()),
        )
        testee = buildViewModel()
    }

    @Test
    fun `onSheetOpened with no existing chat loads a fresh chat expanded`() = runTest {
        (duckChat as FakeDuckChat).nextUrl = "https://duckduckgo.com/?ia=chat"

        testee.commands.test {
            testee.onSheetOpened("tab-1")

            assertTrue(expectMostRecentItem() is DuckChatContextualWebViewViewModel.Command.LoadUrl)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("tab-1", testee.viewState.value.tabId)
        verify(duckChatPixels).reportContextualSheetOpened()
    }

    @Test
    fun `onSheetOpened with pending entry prompt submits it once web app is ready`() = runTest {
        val prompt = NativeInputPrompt("hello", "model-1", "high", null, null, null)
        whenever(contextualEntryPromptStore.consume("tab-1")).thenReturn(ContextualEntryPrompt("tab-1", prompt, null))
        (duckChat as FakeDuckChat).nextUrl = "https://duckduckgo.com/?ia=chat"

        testee.onSheetOpened("tab-1")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        testee.subscriptionEventDataFlow.test {
            testee.onWebAppReady()

            val event = awaitItem()
            assertEquals("submitAIChatNativePrompt", event.subscriptionName)
            assertEquals("hello", event.params.getJSONObject("query").getString("prompt"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `entry prompt aborted before web app ready is not auto-submitted on a later reopen`() = runTest {
        val prompt = NativeInputPrompt("stale", "model-1", "high", null, null, null)
        // Consumed once on open; nothing parked on the subsequent reopen.
        whenever(contextualEntryPromptStore.consume("tab-1")).thenReturn(ContextualEntryPrompt("tab-1", prompt, null), null)
        (duckChat as FakeDuckChat).nextUrl = "https://duckduckgo.com/?ia=chat"

        // Open with a parked prompt, but dismiss before the web app is ready (no onWebAppReady).
        testee.onSheetOpened("tab-1")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        // Reopen with nothing parked; the leftover prompt must be dropped.
        testee.onSheetReopened()
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        testee.subscriptionEventDataFlow.test {
            testee.onWebAppReady()
            expectNoEvents()
        }
    }

    @Test
    fun `reopen with an unreusable session resets to a new chat and hands off to the entry dialog`() = runTest {
        (duckChat as FakeDuckChat).nextUrl = "https://duckduckgo.com/?ia=chat"
        testee.onSheetOpened("tab-1")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        contextualDataStore.persistTabClosedTimestamp("tab-1", 1L)

        testee.commands.test {
            testee.onSheetReopened()
            coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(expectMostRecentItem() is DuckChatContextualWebViewViewModel.Command.ShowNewChatEntryDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sheet closed after reopen entry dialog handoff does not revert the contextual input state`() = runTest {
        (duckChat as FakeDuckChat).nextUrl = "https://duckduckgo.com/?ia=chat"
        testee.onSheetOpened("tab-1")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        contextualDataStore.persistTabClosedTimestamp("tab-1", 1L)

        testee.onSheetReopened()
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        testee.commands.test {
            // The STATE_HIDDEN that follows the entry dialog handoff must not be treated as a dismissal.
            testee.onSheetClosed()

            assertFalse(expectMostRecentItem() is DuckChatContextualWebViewViewModel.Command.ApplyContextualClosed)
            cancelAndIgnoreRemainingEvents()
        }
        verify(duckChatPixels, never()).reportContextualSheetDismissed()
    }

    @Test
    fun `onSheetOpened reports the sheet opened even for an entry-dialog hand-off`() = runTest {
        val prompt = NativeInputPrompt("hello", "model-1", "high", null, null, null)
        whenever(contextualEntryPromptStore.consume("tab-1")).thenReturn(ContextualEntryPrompt("tab-1", prompt, null))
        (duckChat as FakeDuckChat).nextUrl = "https://duckduckgo.com/?ia=chat"

        testee.onSheetOpened("tab-1")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(duckChatPixels).reportContextualSheetOpened()
    }

    @Test
    fun `onSheetOpened reusing an existing chat reports session restored and the sheet opened`() = runTest {
        val chatUrl = "https://duckduckgo.com/?ia=chat&chatID=abc"
        contextualDataStore.persistTabChatUrl("tab-1", chatUrl)
        recentChatsFlow.value = listOf(
            ChatHistoryItem(
                chatId = "abc",
                displayTitle = "Chat",
                type = ChatType.Discussion,
                model = "",
                pinned = false,
                lastEditMillis = 1L,
            ),
        )

        testee.onSheetOpened("tab-1")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(duckChatPixels).reportContextualSheetSessionRestored()
        verify(duckChatPixels).reportContextualSheetOpened()
    }

    @Test
    fun `onSheetReopened without a pending entry reports the sheet opened`() = runTest {
        testee.onSheetOpened("tab-1")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        testee.onSheetReopened()
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        // Once for the initial open, once for the reopen.
        verify(duckChatPixels, times(2)).reportContextualSheetOpened()
    }

    @Test
    fun `onSheetReopened reports the sheet opened even for an entry-dialog hand-off`() = runTest {
        testee.onSheetOpened("tab-1")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        val prompt = NativeInputPrompt("hello", "model-1", "high", null, null, null)
        whenever(contextualEntryPromptStore.consume("tab-1")).thenReturn(ContextualEntryPrompt("tab-1", prompt, null))
        (duckChat as FakeDuckChat).nextUrl = "https://duckduckgo.com/?ia=chat"

        testee.onSheetReopened()
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        // Once for the initial open, once for the reopen — the sheet reports the open regardless of hand-off.
        verify(duckChatPixels, times(2)).reportContextualSheetOpened()
    }

    @Test
    fun `onPromptSent with attached page context includes it in the event`() = runTest {
        testee.onSheetOpened("tab-1")
        testee.onPageContextReceived("tab-1", serializedPageData)
        testee.addPageContext()

        testee.subscriptionEventDataFlow.test {
            testee.onPromptSent("Summarize this page")

            val event = expectMostRecentItem()
            assertEquals("submitAIChatNativePrompt", event.subscriptionName)
            val pageContext = event.params.getJSONObject("pageContext")
            assertEquals("Page Title", pageContext.getString("title"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removePageContext hides the attached context`() = runTest {
        testee.onSheetOpened("tab-1")
        testee.onPageContextReceived("tab-1", serializedPageData)
        testee.addPageContext()
        assertTrue(testee.viewState.value.showContext)

        testee.removePageContext()

        assertFalse(testee.viewState.value.showContext)
        verify(duckChatPixels).reportContextualPageContextRemovedNative()
    }

    @Test
    fun `onPageContextReceived auto-attaches the current context when automatic attachment is enabled`() = runTest {
        whenever(duckChatInternal.isAutomaticContextAttachmentEnabled()).thenReturn(true)

        testee.onPageContextReceived("tab-1", serializedPageData)

        assertTrue(testee.viewState.value.showContext)
        assertEquals("Page Title", testee.viewState.value.contextTitle)
        verify(duckChatPixels).reportContextualPageContextAutoAttached()
    }

    @Test
    fun `onPageContextReceived does not auto-attach when automatic attachment is disabled`() = runTest {
        whenever(duckChatInternal.isAutomaticContextAttachmentEnabled()).thenReturn(false)

        testee.onPageContextReceived("tab-1", serializedPageData)

        assertFalse(testee.viewState.value.showContext)
        verify(duckChatPixels, never()).reportContextualPageContextAutoAttached()
    }

    @Test
    fun `onPageContextReceived does not re-attach context the user removed`() = runTest {
        whenever(duckChatInternal.isAutomaticContextAttachmentEnabled()).thenReturn(true)

        testee.onPageContextReceived("tab-1", serializedPageData)
        testee.removePageContext()
        testee.onPageContextReceived("tab-1", serializedPageData)

        assertFalse(testee.viewState.value.showContext)
    }

    @Test
    fun `native input auto-attach during hand-off does not add context to the first prompt`() = runTest {
        whenever(duckChatInternal.isAutomaticContextAttachmentEnabled()).thenReturn(true)
        val prompt = NativeInputPrompt("hello", "model-1", "high", null, null, null)
        whenever(contextualEntryPromptStore.consume("tab-1"))
            .thenReturn(ContextualEntryPrompt("tab-1", prompt, serializedPageContext = null))
        (duckChat as FakeDuckChat).nextUrl = "https://duckduckgo.com/?ia=chat"

        testee.onSheetOpened("tab-1")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        testee.subscriptionEventDataFlow.test {
            // The entry dialog handed off no context, so the sheet must not auto-attach the current page...
            testee.onPageContextReceived("tab-1", serializedPageData)
            assertEquals("submitAIChatPageContext", awaitItem().subscriptionName)
            assertFalse(testee.viewState.value.showContext)

            // ...and the first prompt is frozen to the entry dialog's (empty) attachment, so it carries none.
            testee.onWebAppReady()
            val promptEvent = awaitItem()
            assertEquals("submitAIChatNativePrompt", promptEvent.subscriptionName)
            assertTrue(promptEvent.params.isNull("pageContext"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `first prompt from a with-context entry hand-off carries the entry page context`() = runTest {
        val prompt = NativeInputPrompt("hello", "model-1", "high", null, null, null)
        whenever(contextualEntryPromptStore.consume("tab-1"))
            .thenReturn(ContextualEntryPrompt("tab-1", prompt, serializedPageContext = serializedPageData))
        (duckChat as FakeDuckChat).nextUrl = "https://duckduckgo.com/?ia=chat"

        testee.onSheetOpened("tab-1")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        testee.subscriptionEventDataFlow.test {
            testee.onWebAppReady()

            val promptEvent = awaitItem()
            assertEquals("submitAIChatNativePrompt", promptEvent.subscriptionName)
            assertEquals("Page Title", promptEvent.params.getJSONObject("pageContext").getString("title"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no-context entry hand-off does not re-attach the current page while it stays put`() = runTest {
        val prompt = NativeInputPrompt("hello", "model-1", "high", null, null, null)
        whenever(contextualEntryPromptStore.consume("tab-1"))
            .thenReturn(ContextualEntryPrompt("tab-1", prompt, serializedPageContext = null))

        testee.onSheetOpened("tab-1")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        testee.onPageContextReceived("tab-1", serializedPageData)
        assertFalse(testee.viewState.value.showContext)

        // A repeated report for the same page still must not auto-attach.
        testee.onPageContextReceived("tab-1", serializedPageData)
        assertFalse(testee.viewState.value.showContext)
    }

    @Test
    fun `no-context entry hand-off re-attaches after the user navigates to a new page`() = runTest {
        val prompt = NativeInputPrompt("hello", "model-1", "high", null, null, null)
        whenever(contextualEntryPromptStore.consume("tab-1"))
            .thenReturn(ContextualEntryPrompt("tab-1", prompt, serializedPageContext = null))

        testee.onSheetOpened("tab-1")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        testee.onPageContextReceived("tab-1", serializedPageData)
        assertFalse(testee.viewState.value.showContext)

        val otherPageData =
            """
            {
                "title": "Other Page",
                "url": "https://other.example.com",
                "content": "Other extracted DOM text...",
                "truncated": false,
                "fullContentLength": 4321
            }
            """.trimIndent()
        testee.onPageContextReceived("tab-1", otherPageData)

        assertTrue(testee.viewState.value.showContext)
        assertEquals("Other Page", testee.viewState.value.contextTitle)
    }

    @Test
    fun `no-context entry hand-off re-attaches when the user manually attaches`() = runTest {
        val prompt = NativeInputPrompt("hello", "model-1", "high", null, null, null)
        whenever(contextualEntryPromptStore.consume("tab-1"))
            .thenReturn(ContextualEntryPrompt("tab-1", prompt, serializedPageContext = null))

        testee.onSheetOpened("tab-1")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        testee.onPageContextReceived("tab-1", serializedPageData)
        assertFalse(testee.viewState.value.showContext)

        testee.addPageContext()

        assertTrue(testee.viewState.value.showContext)
        assertEquals("Page Title", testee.viewState.value.contextTitle)
    }

    @Test
    fun `onNewChatRequestedFromPopup resets chat and hands off to the entry dialog`() = runTest {
        testee.onSheetOpened("tab-1")

        testee.commands.test {
            testee.onNewChatRequestedFromPopup()

            assertTrue(expectMostRecentItem() is DuckChatContextualWebViewViewModel.Command.ShowNewChatEntryDialog)
            cancelAndIgnoreRemainingEvents()
        }
        verify(duckChatPixels).reportContextualSheetNewChatFromPopup()
    }

    @Test
    fun `sheet closed after New Chat handoff does not revert the contextual input state`() = runTest {
        testee.onSheetOpened("tab-1")
        testee.onNewChatRequestedFromPopup()

        testee.commands.test {
            testee.onSheetClosed()

            assertFalse(expectMostRecentItem() is DuckChatContextualWebViewViewModel.Command.ApplyContextualClosed)
            cancelAndIgnoreRemainingEvents()
        }
        verify(duckChatPixels, never()).reportContextualSheetDismissed()
    }

    @Test
    fun `sheet closed by the user reverts the contextual input state`() = runTest {
        testee.onSheetOpened("tab-1")

        testee.commands.test {
            testee.onSheetClosed()

            val command = expectMostRecentItem()
            assertTrue(command is DuckChatContextualWebViewViewModel.Command.ApplyContextualClosed)
            assertEquals("tab-1", (command as DuckChatContextualWebViewViewModel.Command.ApplyContextualClosed).tabId)
            cancelAndIgnoreRemainingEvents()
        }
        verify(duckChatPixels).reportContextualSheetDismissed()
    }

    @Test
    fun `onContextualFireConfirmed resets chat and hides the sheet`() = runTest {
        testee.onSheetOpened("tab-1")

        testee.commands.test {
            testee.onContextualFireConfirmed()

            val command = expectMostRecentItem()
            assertTrue(command is DuckChatContextualWebViewViewModel.Command.ChangeSheetState)
            assertEquals(BottomSheetBehavior.STATE_HIDDEN, (command as DuckChatContextualWebViewViewModel.Command.ChangeSheetState).newState)
            cancelAndIgnoreRemainingEvents()
        }
        verify(duckChatPixels).reportContextualFireButtonConfirmed()
    }

    @Test
    fun `onFullModeRequested opens fullscreen`() = runTest {
        (duckChat as FakeDuckChat).nextUrl = "https://duckduckgo.com/?ia=chat"

        testee.commands.test {
            testee.onFullModeRequested()

            assertTrue(expectMostRecentItem() is DuckChatContextualWebViewViewModel.Command.OpenFullscreenMode)
            cancelAndIgnoreRemainingEvents()
        }
        verify(duckChatPixels).reportContextualSheetExpanded()
    }

    @Test
    fun `onFullModeRequested with no existing chat reports entry without a prompt`() = runTest {
        (duckChat as FakeDuckChat).nextUrl = "https://duckduckgo.com/?ia=chat"

        testee.onFullModeRequested()

        verify(duckChatInternal).reportDuckChatEntry(DuckChatEntryPoint.CONTEXTUAL_CHAT, opensNewTab = true, hasPrompt = false)
    }

    @Test
    fun `onFullModeRequested with an existing chat reports entry with a prompt`() = runTest {
        testee.onChatPageLoaded("https://duckduckgo.com/?chatID=abc")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        testee.onFullModeRequested()

        verify(duckChatInternal).reportDuckChatEntry(DuckChatEntryPoint.CONTEXTUAL_CHAT, opensNewTab = true, hasPrompt = true)
    }

    @Test
    fun `onOpenDuckAiFromPopup opens a new duck ai tab with no prompt`() = runTest {
        (duckChat as FakeDuckChat).nextUrl = "https://duckduckgo.com/?ia=chat"
        testee.onSheetOpened("tab-1")

        testee.commands.test {
            testee.onOpenDuckAiFromPopup()

            val command = expectMostRecentItem()
            assertTrue(command is DuckChatContextualWebViewViewModel.Command.OpenChatUrl)
            command as DuckChatContextualWebViewViewModel.Command.OpenChatUrl
            assertEquals("https://duckduckgo.com/?ia=chat", command.url)
            assertEquals("tab-1", command.sourceTabId)
            cancelAndIgnoreRemainingEvents()
        }
        verify(duckChatInternal).reportDuckChatEntry(DuckChatEntryPoint.CONTEXTUAL_CHAT, opensNewTab = true, hasPrompt = false)
    }

    @Test
    fun `onChatsIconClicked with no recent chats launches chat history`() = runTest {
        testee.commands.test {
            testee.onChatsIconClicked()

            assertTrue(expectMostRecentItem() is DuckChatContextualWebViewViewModel.Command.LaunchChatHistory)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onChatsIconClicked with recent chats shows the popup`() = runTest {
        recentChatsFlow.value = listOf(
            ChatHistoryItem(
                chatId = "c1",
                displayTitle = "Chat",
                type = ChatType.Discussion,
                model = "",
                pinned = false,
                lastEditMillis = 1L,
            ),
        )
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        testee.commands.test {
            testee.onChatsIconClicked()

            assertTrue(expectMostRecentItem() is DuckChatContextualWebViewViewModel.Command.ShowChatsPopup)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onContextualClose hides the sheet`() = runTest {
        testee.commands.test {
            testee.onContextualClose()

            val command = expectMostRecentItem()
            assertTrue(command is DuckChatContextualWebViewViewModel.Command.ChangeSheetState)
            assertEquals(BottomSheetBehavior.STATE_HIDDEN, (command as DuckChatContextualWebViewViewModel.Command.ChangeSheetState).newState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onChatPageLoaded with a chat id persists the url and shows fullscreen`() = runTest {
        testee.onSheetOpened("tab-1")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        testee.onChatPageLoaded("https://duckduckgo.com/?chatID=abc")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(testee.viewState.value.showFullscreen)
        assertEquals("https://duckduckgo.com/?chatID=abc", contextualDataStore.getTabChatUrl("tab-1"))
    }

    @Test
    fun `handleJSCall close returns true and closes the sheet`() = runTest {
        assertTrue(testee.handleJSCall(RealDuckChatJSHelper.METHOD_CLOSE_AI_CHAT))
        assertFalse(testee.handleJSCall("some.other.method"))
    }

    @Test
    fun `onVoiceRecognitionSuccess opens a search for a non duck ai result`() = runTest {
        testee.commands.test {
            testee.onVoiceRecognitionSuccess("weather today", isDuckAiResult = false)

            assertTrue(expectMostRecentItem() is DuckChatContextualWebViewViewModel.Command.OpenSearchInNewTab)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun buildViewModel() = DuckChatContextualWebViewViewModel(
        dispatchers = coroutineRule.testDispatcherProvider,
        duckChat = duckChat,
        duckChatInternal = duckChatInternal,
        duckChatJSHelper = duckChatJSHelper,
        contextualDataStore = contextualDataStore,
        sessionTimeoutProvider = sessionTimeoutProvider,
        timeProvider = timeProvider,
        duckChatPixels = duckChatPixels,
        duckChatFeature = duckChatFeature,
        modelManager = modelManager,
        chatHistoryRepository = chatHistoryRepository,
        contextualEntryPromptStore = contextualEntryPromptStore,
    )

    private class FakeDuckChat : com.duckduckgo.duckchat.api.DuckChat {
        var nextUrl: String = ""
        private val nativeChatInputEnabled = MutableStateFlow(false)

        override fun isEnabled(): Boolean = true
        override fun openDuckChat(entryPoint: com.duckduckgo.duckchat.api.DuckChatEntryPoint) = Unit
        override fun openDuckChatWithAutoPrompt(query: String, entryPoint: com.duckduckgo.duckchat.api.DuckChatEntryPoint) = Unit
        override fun openDuckChatWithPrefill(query: String, entryPoint: com.duckduckgo.duckchat.api.DuckChatEntryPoint) = Unit
        override fun reportDuckChatEntry(
            entryPoint: com.duckduckgo.duckchat.api.DuckChatEntryPoint,
            opensNewTab: Boolean,
            hasPrompt: Boolean,
        ) = Unit
        override fun getDuckChatUrl(
            query: String,
            autoPrompt: Boolean,
            sidebar: Boolean,
        ): String = nextUrl

        override fun getDuckChatSettingsUrl(): String = "https://duck.ai?settings=open"

        override fun isDuckChatUrl(uri: android.net.Uri): Boolean =
            uri.host == "duck.ai" || uri.host == "duckduckgo.com"
        override suspend fun wasOpenedBefore(): Boolean = false

        override suspend fun setInputScreenUserSetting(enabled: Boolean) = Unit
        override suspend fun isInputScreenEverEnabled(): Boolean = false
        override suspend fun setCosmeticInputScreenUserSetting(enabled: Boolean) = Unit
        override fun observeInputScreenUserSettingEnabled(): Flow<Boolean> = emptyFlow()
        override fun observeCosmeticInputScreenUserSettingEnabled(): Flow<Boolean?> = emptyFlow()
        override fun observeAutomaticContextAttachmentUserSettingEnabled(): Flow<Boolean> = flowOf(true)
        override fun observeNativeInputFieldUserSettingEnabled(): Flow<Boolean> = emptyFlow()
        override fun observeNativeChatInputEnabled(): Flow<Boolean> = nativeChatInputEnabled
        override fun observeNativeInputNavBarEnabled(): Flow<Boolean> = emptyFlow()
        override suspend fun isStandaloneMigrationCompleted(): Boolean = true
        override suspend fun setChatSuggestionsUserSetting(enabled: Boolean) = Unit
        override fun observeChatSuggestionsUserSettingEnabled(): Flow<Boolean> = flowOf(true)
        override fun openVoiceDuckChat(entryPoint: com.duckduckgo.duckchat.api.DuckChatEntryPoint) { }
        override fun isVoiceChatSessionActive(tabId: String): Boolean = false
        override val activeVoiceChatSessions: Flow<Set<String>> = flowOf(emptySet())
        override fun observeTriggerVoiceChatSessionEnd(): Flow<String> = emptyFlow()
        override fun endVoiceChatSession(tabId: String) { }
        override suspend fun isChatHistoryAvailable(): Boolean = false
        override suspend fun hasUserEnabledChatHistory(): Boolean = false
        override fun observeHasChatSuggestions(): Flow<Boolean> = emptyFlow()
        override suspend fun onAddressBarPickerDuckAiSelected() = Unit
    }

    private class FakeDuckChatContextualDataStore : DuckChatContextualDataStore {
        private val urls = mutableMapOf<String, String>()
        private val closeTimestamps = mutableMapOf<String, Long>()

        override suspend fun persistTabChatUrl(
            tabId: String,
            url: String,
        ) {
            urls[tabId] = url
        }

        override suspend fun getTabChatUrl(tabId: String): String? = urls[tabId]

        override suspend fun persistTabClosedTimestamp(
            tabId: String,
            timestampMs: Long,
        ) {
            closeTimestamps[tabId] = timestampMs
        }

        override suspend fun getTabClosedTimestamp(tabId: String): Long? = closeTimestamps[tabId]

        override fun clearTabChatUrl(tabId: String) {
            urls.remove(tabId)
        }

        override fun clearTabClosedTimestamp(tabId: String) {
            closeTimestamps.remove(tabId)
        }

        override fun clearAll() {
            urls.clear()
            closeTimestamps.clear()
        }
    }

    private class FakeDuckChatContextualSessionTimeoutProvider : DuckChatContextualSessionTimeoutProvider {
        var timeoutMs: Long = 0L

        override fun sessionTimeoutMillis(): Long = timeoutMs
    }

    private class FakeDuckChatContextualTimeProvider : DuckChatContextualTimeProvider {
        var nowMs: Long = 0L

        override fun currentTimeMillis(): Long = nowMs
    }
}
