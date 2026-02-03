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

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.helper.NativeAction
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper
import com.duckduckgo.duckchat.impl.store.DuckChatContextualDataStore
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DuckChatContextualViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: DuckChatContextualViewModel
    private val duckChat: com.duckduckgo.duckchat.api.DuckChat = FakeDuckChat()
    private val duckChatInternal: DuckChatInternal = mock()
    private val duckChatJSHelper: DuckChatJSHelper = mock()
    private val contextualDataStore = FakeDuckChatContextualDataStore()
    private val timeProvider = FakeDuckChatContextualTimeProvider()
    private val sessionTimeoutProvider = FakeDuckChatContextualSessionTimeoutProvider()

    @Before
    fun setup() {
        whenever(duckChatInternal.isAutomaticContextAttachmentEnabled()).thenReturn(true)
        whenever(
            duckChatJSHelper.onNativeAction(NativeAction.NEW_CHAT),
        ).thenReturn(
            SubscriptionEventData(
                RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME,
                "submitNewChatAction",
                JSONObject(),
            ),
        )

        testee = DuckChatContextualViewModel(
            dispatchers = coroutineRule.testDispatcherProvider,
            duckChat = duckChat,
            duckChatInternal = duckChatInternal,
            duckChatJSHelper = duckChatJSHelper,
            contextualDataStore = contextualDataStore,
            sessionTimeoutProvider = sessionTimeoutProvider,
            timeProvider = timeProvider,
        )
    }

    @Test
    fun `when prompt sent with page context then event contains context`() =
        runTest {
            val tabId = "tab-1"
            val serializedPageData =
                """
                {
                    "title": "Page Title",
                    "url": "https://example.com",
                    "content": "Extracted DOM text...",
                    "favicon": [{"href": "data:image/png;base64,...", "rel": "icon"}],
                    "truncated": false,
                    "fullContentLength": 1234
                }
                """.trimIndent()

            testee.onSheetOpened(tabId)
            testee.addPageContext()
            testee.onPageContextReceived(tabId, serializedPageData)

            testee.subscriptionEventDataFlow.test {
                val prompt = "Summarize this page"

                testee.onPromptSent(prompt)

                val event = awaitItem()
                assertEquals(RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME, event.featureName)
                assertEquals("submitAIChatNativePrompt", event.subscriptionName)

                val params = event.params
                assertEquals("android", params.getString("platform"))
                assertEquals("query", params.getString("tool"))

                val query = params.getJSONObject("query")
                assertEquals(prompt, query.getString("prompt"))
                assertTrue(query.getBoolean("autoSubmit"))

                val pageContext = params.getJSONObject("pageContext")
                assertEquals("Page Title", pageContext.getString("title"))
                assertEquals("https://example.com", pageContext.getString("url"))
                assertEquals("Extracted DOM text...", pageContext.getString("content"))
                assertFalse(pageContext.getBoolean("truncated"))
                assertEquals(1234, pageContext.getInt("fullContentLength"))

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when prompt sent without page context then event omits pageContext`() =
        runTest {
            val tabId = "tab-1"
            testee.onSheetOpened(tabId)

            testee.subscriptionEventDataFlow.test {
                val prompt = "Hello Duck.ai"

                testee.onPromptSent(prompt)

                val event = awaitItem()
                assertEquals(RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME, event.featureName)
                assertEquals("submitAIChatNativePrompt", event.subscriptionName)

                val params = event.params
                assertEquals("android", params.getString("platform"))
                assertEquals("query", params.getString("tool"))
                assertFalse(params.has("pageContext"))

                val query = params.getJSONObject("query")
                assertEquals(prompt, query.getString("prompt"))
                assertTrue(query.getBoolean("autoSubmit"))

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when page context removed then prompt omits pageContext even if cached`() =
        runTest {
            val tabId = "tab-1"
            val serializedPageData =
                """
                {
                    "title": "Ctx Title",
                    "url": "https://ctx.com",
                    "content": "content"
                }
                """.trimIndent()

            // Load and cache context, then remove it to set hasContext=false while data remains cached.
            testee.onSheetOpened(tabId)
            testee.onPageContextReceived(tabId, serializedPageData)
            testee.removePageContext()

            testee.subscriptionEventDataFlow.test {
                val prompt = "Hello Duck.ai"

                testee.onPromptSent(prompt)

                val event = awaitItem()
                val params = event.params
                // Even though updatedPageContext is populated, hasContext=false should omit pageContext.
                assertFalse(params.has("pageContext"))

                cancelAndIgnoreRemainingEvents()
            }

            val state = testee.viewState.value
            assertFalse(state.showContext)
            assertTrue(state.userRemovedContext)
        }

    @Test
    fun `when sheet opened in input mode then load url command emitted`() =
        runTest {
            val expectedUrl = "https://duckduckgo.com/?placement=sidebar&q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"
            (duckChat as FakeDuckChat).nextUrl = expectedUrl
            val tabId = "tab-1"

            testee.commands.test {
                testee.onSheetOpened(tabId)
                val command = expectMostRecentItem()
                assertTrue(command is DuckChatContextualViewModel.Command.LoadUrl)
                assertEquals(expectedUrl, (command as DuckChatContextualViewModel.Command.LoadUrl).url)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when page context arrives input state stores context`() =
        runTest {
            val tabId = "tab-1"
            val serializedPageData =
                """
                {
                    "title": "Ctx Title",
                    "url": "https://ctx.com",
                    "content": "content"
                }
                """.trimIndent()

            testee.viewState.test {
                testee.addPageContext()
                testee.onPageContextReceived(tabId, serializedPageData)

                val state = expectMostRecentItem()
                assertTrue(state.showContext)
                assertFalse(state.userRemovedContext)
                assertEquals("Ctx Title", state.contextTitle)
                assertEquals("https://ctx.com", state.contextUrl)
                assertEquals("tab-1", state.tabId)
            }
        }

    @Test
    fun `when prompt sent then chat view state emitted`() =
        runTest {
            testee.commands.test {
                testee.onPromptSent("hello")

                val command = awaitItem() as DuckChatContextualViewModel.Command.ChangeSheetState
                assertEquals(BottomSheetBehavior.STATE_EXPANDED, command.newState)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(DuckChatContextualViewModel.SheetMode.WEBVIEW, testee.viewState.value.sheetMode)
        }

    @Test
    fun `when contextual close invoked then sheet hidden`() =
        runTest {
            testee.commands.test {
                testee.onContextualClose()

                val command = awaitItem() as DuckChatContextualViewModel.Command.ChangeSheetState
                assertEquals(BottomSheetBehavior.STATE_HIDDEN, command.newState)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when keyboard becomes visible in input mode then sheet expanded`() =
        runTest {
            testee.commands.test {
                testee.onKeyboardVisibilityChanged(true)

                val command = awaitItem() as DuckChatContextualViewModel.Command.ChangeSheetState
                assertEquals(BottomSheetBehavior.STATE_EXPANDED, command.newState)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when keyboard hidden in input mode then sheet half expanded`() =
        runTest {
            testee.commands.test {
                testee.onKeyboardVisibilityChanged(false)

                val command = awaitItem() as DuckChatContextualViewModel.Command.ChangeSheetState
                assertEquals(BottomSheetBehavior.STATE_HALF_EXPANDED, command.newState)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when add and remove page context then hasContext toggles`() =
        runTest {
            testee.viewState.test {
                // initial emission
                awaitItem()

                testee.addPageContext()
                val withContext = expectMostRecentItem() as DuckChatContextualViewModel.ViewState
                assertTrue(withContext.showContext)

                testee.removePageContext()
                val withoutContext = expectMostRecentItem() as DuckChatContextualViewModel.ViewState
                assertFalse(withoutContext.showContext)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when replace prompt then prompt stored`() =
        runTest {
            testee.viewState.test {
                // initial emission
                awaitItem()

                testee.replacePrompt("new prompt")
                val state = expectMostRecentItem() as DuckChatContextualViewModel.ViewState
                assertEquals("new prompt", state.prompt)
                assertTrue(state.showContext)
                assertFalse(state.userRemovedContext)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when user removed context then page context remains hidden`() = runTest {
        val tabId = "tab-1"
        val serializedPageData =
            """
            {
                "title": "Ctx Title",
                "url": "https://ctx.com",
                "content": "content"
            }
            """.trimIndent()

        testee.removePageContext()
        testee.onPageContextReceived(tabId, serializedPageData)

        val state = testee.viewState.value
        assertTrue(state.userRemovedContext)
        assertFalse(state.showContext)
    }

    @Test
    fun `when full mode requested with url then open fullscreen command emitted`() =
        runTest {
            testee.commands.test {
                val expectedUrl = "https://duckduckgo.com/?placement=sidebar&q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"
                (duckChat as FakeDuckChat).nextUrl = expectedUrl

                testee.onFullModeRequested()

                val command = awaitItem() as DuckChatContextualViewModel.Command.OpenFullscreenMode
                assertEquals(expectedUrl, command.url)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `handleJSCall closeAIChat hides sheet and returns true`() = runTest {
        testee.commands.test {
            val handled = testee.handleJSCall("closeAIChat")

            val command = awaitItem() as DuckChatContextualViewModel.Command.ChangeSheetState
            assertTrue(handled)
            assertEquals(BottomSheetBehavior.STATE_HIDDEN, command.newState)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `handleJSCall unknown method returns false and leaves state unchanged`() = runTest {
        val handled = testee.handleJSCall("unknownMethod")
        assertFalse(handled)
    }

    @Test
    fun `when sheet opened with stored chat url then load it and expand`() = runTest {
        val tabId = "tab-1"
        val storedUrl = "https://duck.ai/chat?chatID=123"
        contextualDataStore.persistTabChatUrl(tabId, storedUrl)

        testee.commands.test {
            testee.onSheetOpened(tabId)

            val changeStateCommand = awaitItem() as DuckChatContextualViewModel.Command.ChangeSheetState
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, changeStateCommand.newState)

            val loadCommand = awaitItem() as DuckChatContextualViewModel.Command.LoadUrl
            assertEquals(storedUrl, loadCommand.url)

            val state = testee.viewState.value
            assertEquals(DuckChatContextualViewModel.SheetMode.WEBVIEW, state.sheetMode)
            assertEquals(tabId, state.tabId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when sheet opened with stored chat url and session expired then load new url`() = runTest {
        val tabId = "tab-1"
        val storedUrl = "https://duck.ai/chat?chatID=123"
        contextualDataStore.persistTabChatUrl(tabId, storedUrl)

        val now = 100_000L
        timeProvider.nowMs = now
        sessionTimeoutProvider.timeoutMs = 10_000L
        contextualDataStore.persistTabClosedTimestamp(tabId, now - 20_000L)

        testee.subscriptionEventDataFlow.test {
            testee.onSheetOpened(tabId)

            val event = awaitItem()
            assertEquals("submitNewChatAction", event.subscriptionName)
            assertEquals(RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME, event.featureName)

            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }

        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
        assertNull(contextualDataStore.getTabChatUrl(tabId))
    }

    @Test
    fun `onNewChatRequested emits new chat subscription`() = runTest {
        testee.subscriptionEventDataFlow.test {
            testee.onSheetOpened("tab-1")
            testee.onNewChatRequested()

            val event = awaitItem()
            assertEquals("submitNewChatAction", event.subscriptionName)
            assertEquals(RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME, event.featureName)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onChatPageLoaded in Input Mode doesn't store url`() = runTest {
        val tabId = "tab-1"
        val url = "https://duck.ai/chat?chatID=123"
        testee.onSheetOpened(tabId)

        testee.onChatPageLoaded(url)
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, contextualDataStore.getTabChatUrl(tabId))
    }

    @Test
    fun `onChatPageLoaded stores url by tab id`() = runTest {
        val tabId = "tab-1"
        val url = "https://duck.ai/chat?chatID=123"
        contextualDataStore.persistTabChatUrl(tabId, url)

        testee.onSheetOpened(tabId)
        testee.onPromptSent("prompt")

        testee.onChatPageLoaded(url)
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(url, contextualDataStore.getTabChatUrl(tabId))
    }

    @Test
    fun `onChatPageLoaded without chat id does not store url`() = runTest {
        val tabId = "tab-1"
        val url = "https://duck.ai/chat"
        testee.onSheetOpened(tabId)

        testee.onChatPageLoaded(url)
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertNull(contextualDataStore.getTabChatUrl(tabId))
    }

    @Test
    fun `onNewChatRequested clears stored url for current tab`() = runTest {
        val tabId = "tab-1"
        val url = "https://duck.ai/chat?chatID=123"
        testee.onSheetOpened(tabId)
        contextualDataStore.persistTabChatUrl(tabId, url)

        testee.onNewChatRequested()
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertNull(contextualDataStore.getTabChatUrl(tabId))
    }

    @Test
    fun `onContextualClose stores last closed timestamp`() = runTest {
        val tabId = "tab-1"
        val now = 55_000L
        timeProvider.nowMs = now

        testee.onSheetOpened(tabId)
        testee.onContextualClose()
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(now, contextualDataStore.getTabClosedTimestamp(tabId))
    }

    @Test
    fun `persistTabClosed stores last closed timestamp`() = runTest {
        val tabId = "tab-1"
        val now = 77_000L
        timeProvider.nowMs = now

        testee.onSheetOpened(tabId)
        testee.persistTabClosed()
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(now, contextualDataStore.getTabClosedTimestamp(tabId))
    }

    @Test
    fun `reopenSheet in webview mode reuses stored url when session active`() = runTest {
        val tabId = "tab-1"
        val storedUrl = "https://duck.ai/chat?chatID=123"
        contextualDataStore.persistTabChatUrl(tabId, storedUrl)
        sessionTimeoutProvider.timeoutMs = 10_000L
        timeProvider.nowMs = 100_000L
        contextualDataStore.persistTabClosedTimestamp(tabId, 95_000L)

        testee.commands.test {
            testee.onSheetOpened(tabId)
            awaitItem()
            awaitItem()

            testee.reopenSheet()
            val changeStateCommand = awaitItem() as DuckChatContextualViewModel.Command.ChangeSheetState
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, changeStateCommand.newState)

            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reopenSheet in webview mode starts new chat when session expired`() = runTest {
        val tabId = "tab-1"
        val storedUrl = "https://duck.ai/chat?chatID=123"
        contextualDataStore.persistTabChatUrl(tabId, storedUrl)
        sessionTimeoutProvider.timeoutMs = 10_000L
        timeProvider.nowMs = 100_000L
        contextualDataStore.persistTabClosedTimestamp(tabId, 95_000L)

        testee.onSheetOpened(tabId)

        testee.subscriptionEventDataFlow.test {
            timeProvider.nowMs = 120_000L
            testee.reopenSheet()

            val event = awaitItem()
            assertEquals("submitNewChatAction", event.subscriptionName)
            assertEquals(RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME, event.featureName)

            cancelAndIgnoreRemainingEvents()
        }

        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
        assertNull(contextualDataStore.getTabChatUrl(tabId))
    }

    @Test
    fun `reopenSheet in webview mode loads new url when no stored url`() = runTest {
        val tabId = "tab-1"
        val storedUrl = "https://duck.ai/chat?chatID=123"
        contextualDataStore.persistTabChatUrl(tabId, storedUrl)
        sessionTimeoutProvider.timeoutMs = 10_000L
        timeProvider.nowMs = 100_000L
        contextualDataStore.persistTabClosedTimestamp(tabId, 95_000L)

        val expectedUrl = "https://duckduckgo.com/?placement=sidebar&q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"
        (duckChat as FakeDuckChat).nextUrl = expectedUrl

        testee.commands.test {
            testee.onSheetOpened(tabId)
            awaitItem()
            awaitItem()

            contextualDataStore.clearTabChatUrl(tabId)

            testee.reopenSheet()
            val changeStateCommand = awaitItem() as DuckChatContextualViewModel.Command.ChangeSheetState
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, changeStateCommand.newState)

            val loadCommand = awaitItem() as DuckChatContextualViewModel.Command.LoadUrl
            assertEquals(expectedUrl, loadCommand.url)

            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reopenSheet in webview mode starts new chat when session expired and no stored url`() = runTest {
        val tabId = "tab-1"
        val storedUrl = "https://duck.ai/chat?chatID=123"
        contextualDataStore.persistTabChatUrl(tabId, storedUrl)
        sessionTimeoutProvider.timeoutMs = 10_000L
        timeProvider.nowMs = 100_000L
        contextualDataStore.persistTabClosedTimestamp(tabId, 80_000L)

        testee.onSheetOpened(tabId)
        contextualDataStore.clearTabChatUrl(tabId)

        testee.subscriptionEventDataFlow.test {
            testee.reopenSheet()

            val event = awaitItem()
            assertEquals("submitNewChatAction", event.subscriptionName)
            assertEquals(RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME, event.featureName)

            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reopenSheet in input mode half expands sheet`() = runTest {
        testee.commands.test {
            testee.reopenSheet()

            val command = awaitItem() as DuckChatContextualViewModel.Command.ChangeSheetState
            assertEquals(BottomSheetBehavior.STATE_HALF_EXPANDED, command.newState)

            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun enableAutomaticContextAttachment() {
        (duckChat as FakeDuckChat).setAutomaticContextAttachment(true)
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
    }

    private class FakeDuckChat : com.duckduckgo.duckchat.api.DuckChat {
        var nextUrl: String = ""
        private val automaticContextAttachment = MutableStateFlow(true)

        override fun isEnabled(): Boolean = true
        override fun openDuckChat() = Unit
        override fun openDuckChatWithAutoPrompt(query: String) = Unit
        override fun openDuckChatWithPrefill(query: String) = Unit
        override fun getDuckChatUrl(query: String, autoPrompt: Boolean, sidebar: Boolean): String = nextUrl
        override fun isDuckChatUrl(uri: android.net.Uri): Boolean = false
        override suspend fun wasOpenedBefore(): Boolean = false
        override fun showNewAddressBarOptionChoiceScreen(context: android.content.Context, isDarkThemeEnabled: Boolean) = Unit
        override suspend fun setInputScreenUserSetting(enabled: Boolean) = Unit
        override suspend fun setCosmeticInputScreenUserSetting(enabled: Boolean) = Unit
        override fun observeInputScreenUserSettingEnabled(): Flow<Boolean> = kotlinx.coroutines.flow.emptyFlow()
        override fun observeCosmeticInputScreenUserSettingEnabled(): Flow<Boolean?> = kotlinx.coroutines.flow.emptyFlow()
        override fun observeAutomaticContextAttachmentUserSettingEnabled(): Flow<Boolean> = automaticContextAttachment
        override fun showContextualOnboarding(context: Context, onConfirmed: () -> Unit) = Unit
        override suspend fun isContextualOnboardingCompleted(): Boolean = true
    }

    private class FakeDuckChatContextualDataStore : DuckChatContextualDataStore {
        private val urls = mutableMapOf<String, String>()
        private val closeTimestamps = mutableMapOf<String, Long>()

        override suspend fun persistTabChatUrl(tabId: String, url: String) {
            urls[tabId] = url
        }

        override suspend fun getTabChatUrl(tabId: String): String? = urls[tabId]

        override suspend fun persistTabClosedTimestamp(tabId: String, timestampMs: Long) {
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
