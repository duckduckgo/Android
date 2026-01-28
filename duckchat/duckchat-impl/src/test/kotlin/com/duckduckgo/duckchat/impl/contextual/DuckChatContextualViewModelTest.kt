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
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DuckChatContextualViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: DuckChatContextualViewModel
    private val duckChat: com.duckduckgo.duckchat.api.DuckChat = FakeDuckChat()

    @Before
    fun setup() {
        testee = DuckChatContextualViewModel(
            dispatchers = coroutineRule.testDispatcherProvider,
            duckChat = duckChat,
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

            enableAutomaticContextAttachment()
            testee.onSheetOpened(tabId)
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
            enableAutomaticContextAttachment()
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
        }

    @Test
    fun `when sheet opened in input mode then load url command emitted`() =
        runTest {
            val expectedUrl = "https://duckduckgo.com/?placement=sidebar&q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"
            (duckChat as FakeDuckChat).nextUrl = expectedUrl
            val tabId = "tab-1"

            testee.commands.test {
                testee.onSheetOpened(tabId)
                val command = awaitItem()
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

            enableAutomaticContextAttachment()
            testee.viewState.test {
                awaitItem()

                testee.onPageContextReceived(tabId, serializedPageData)

                val state = awaitItem() as DuckChatContextualViewModel.ViewState
                assertTrue(state.showContext)
                assertEquals("Ctx Title", state.contextTitle)
                assertEquals("https://ctx.com", state.contextUrl)
                assertEquals("tab-1", state.tabId)
            }
        }

    @Test
    fun `when automatic context attachment changes then view state updates`() =
        runTest {
            testee.viewState.test {
                val initial = awaitItem()
                assertFalse(initial.allowsAutomaticContextAttachment)
                assertFalse(initial.showContext)

                (duckChat as FakeDuckChat).setAutomaticContextAttachment(true)

                val updated = awaitItem()
                assertTrue(updated.allowsAutomaticContextAttachment)
                assertTrue(updated.showContext)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when native input focused then sheet state updates`() =
        runTest {
            testee.viewState.test {
                // initial emission
                awaitItem()

                testee.onNativeInputFocused(true)
                val expandedState = expectMostRecentItem() as DuckChatContextualViewModel.ViewState
                assertEquals(BottomSheetBehavior.STATE_EXPANDED, expandedState.sheetState)

                testee.onNativeInputFocused(false)
                val halfExpandedState = expectMostRecentItem() as DuckChatContextualViewModel.ViewState
                assertEquals(BottomSheetBehavior.STATE_HALF_EXPANDED, halfExpandedState.sheetState)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when prompt sent then chat view state emitted`() =
        runTest {
            testee.viewState.test {
                // initial emission
                awaitItem()

                testee.onPromptSent("hello")
                val viewState = expectMostRecentItem() as DuckChatContextualViewModel.ViewState
                assertEquals(DuckChatContextualViewModel.SheetMode.WEBVIEW, viewState.sheetMode)
                assertEquals("chatUrl", viewState.url)
                assertEquals(BottomSheetBehavior.STATE_EXPANDED, viewState.sheetState)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when contextual close invoked then sheet hidden`() =
        runTest {
            testee.viewState.test {
                // initial emission
                awaitItem()

                testee.onContextualClose()
                val state = expectMostRecentItem()
                assertEquals(BottomSheetBehavior.STATE_HIDDEN, state.sheetState)

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

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when full mode requested with url then open fullscreen command emitted`() =
        runTest {
            setFullModeUrl("https://duck.chat/full")

            testee.commands.test {
                testee.onFullModeRequested()

                val command = awaitItem() as DuckChatContextualViewModel.Command.OpenFullscreenMode
                assertEquals("https://duck.chat/full", command.url)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when full mode requested without url then no command emitted`() =
        runTest {
            testee.commands.test {
                testee.onFullModeRequested()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `handleJSCall closeAIChat hides sheet and returns true`() = runTest {
        testee.viewState.test {
            awaitItem() // initial emission

            val handled = testee.handleJSCall("closeAIChat")
            val state = expectMostRecentItem()

            assertTrue(handled)
            assertEquals(BottomSheetBehavior.STATE_HIDDEN, state.sheetState)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handleJSCall unknown method returns false and leaves state unchanged`() = runTest {
        val handled = testee.handleJSCall("unknownMethod")
        assertFalse(handled)
    }

    private fun setFullModeUrl(url: String) {
        val fullModeUrlField = DuckChatContextualViewModel::class.java.getDeclaredField("fullModeUrl")
        fullModeUrlField.isAccessible = true
        fullModeUrlField.set(testee, url)
    }

    private fun enableAutomaticContextAttachment() {
        (duckChat as FakeDuckChat).setAutomaticContextAttachment(true)
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
    }

    private class FakeDuckChat : com.duckduckgo.duckchat.api.DuckChat {
        var nextUrl: String = ""
        private val automaticContextAttachment = MutableStateFlow(false)

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

        fun setAutomaticContextAttachment(enabled: Boolean) {
            automaticContextAttachment.value = enabled
        }
    }
}
