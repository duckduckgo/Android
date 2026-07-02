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

import android.content.res.Resources
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStatePublisher
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputModeWidget
import com.duckduckgo.js.messaging.api.JsMessaging
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealContextualNativeInputManagerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val duckChat: DuckChat = mock()
    private val publisher: NativeInputStatePublisher = mock()
    private val testee = RealContextualNativeInputManager(duckChat, publisher)

    @Test
    fun `when onContextualClosed called with blank tabId then publisher is not touched`() {
        testee.onContextualClosed("")

        verify(publisher, never()).update(any(), any())
    }

    @Test
    fun `when onContextualClosed called then publisher resets inputContext to browser`() {
        val tabId = "tab-1"
        val previous = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_ONLY,
            inputContext = NativeInputState.InputContext.DUCK_AI_CONTEXTUAL,
            toggleSelection = NativeInputState.ToggleSelection.DUCK_AI,
        )

        testee.onContextualClosed(tabId)

        val captor = argumentCaptor<(NativeInputState) -> NativeInputState>()
        verify(publisher).update(eq(tabId), captor.capture())
        val updated = captor.firstValue.invoke(previous)

        assertEquals(NativeInputState.InputContext.BROWSER, updated.inputContext)
        assertEquals(NativeInputState.ToggleSelection.SEARCH, updated.toggleSelection)
    }

    @Test
    fun `when onContextualClosed called then inputMode is preserved`() {
        val tabId = "tab-1"
        val previous = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_ONLY,
            inputContext = NativeInputState.InputContext.DUCK_AI_CONTEXTUAL,
            toggleSelection = NativeInputState.ToggleSelection.DUCK_AI,
        )

        testee.onContextualClosed(tabId)

        val captor = argumentCaptor<(NativeInputState) -> NativeInputState>()
        verify(publisher).update(eq(tabId), captor.capture())
        val updated = captor.firstValue.invoke(previous)

        // inputMode is owned by the main widget VM; the reset must leave it alone.
        assertEquals(NativeInputState.InputMode.SEARCH_ONLY, updated.inputMode)
    }

    @Test
    fun `when webview mode then input mode modelPickerEnabled emits false then true`() = runTest {
        val enabled = MutableStateFlow(true)
        whenever(duckChat.observeNativeChatInputEnabled()).thenReturn(enabled)
        val widget = mock<NativeInputModeWidget>()
        val pickerFlowCaptor = argumentCaptor<Flow<Boolean>>()
        testee.init(
            tabId = "tab",
            card = mockCard(),
            widget = widget,
            jsMessaging = mock<JsMessaging>(),
            lifecycleOwner = lifecycleOwner(),
            chatIdFlow = emptyFlow(),
            onSearchSubmitted = {},
        )
        verify(widget).bindModelPickerEnabledSource(pickerFlowCaptor.capture())
        val pickerFlow = pickerFlowCaptor.firstValue

        pickerFlow.test {
            assertTrue(awaitItem())

            testee.onWebViewMode()
            assertFalse(awaitItem())

            testee.onInputMode()
            assertTrue(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when native chat input flips off in web view mode then card is hidden`() {
        val enabled = MutableStateFlow(true)
        whenever(duckChat.observeNativeChatInputEnabled()).thenReturn(enabled)
        val card = mockCard()
        val widget = mock<NativeInputModeWidget>()
        testee.init(
            tabId = "tab",
            card = card,
            widget = widget,
            jsMessaging = mock<JsMessaging>(),
            lifecycleOwner = lifecycleOwner(),
            chatIdFlow = emptyFlow(),
            onSearchSubmitted = {},
        )
        testee.onWebViewMode()

        enabled.value = false

        verify(card).visibility = View.GONE
    }

    @Test
    fun `when native chat input enabled and onInputMode then card shown`() {
        val enabled = MutableStateFlow(true)
        whenever(duckChat.observeNativeChatInputEnabled()).thenReturn(enabled)
        val card = mockCard()
        // View.show() only writes visibility when it isn't already VISIBLE; a mock defaults to 0
        // (VISIBLE), so start it GONE to observe the transition.
        whenever(card.visibility).thenReturn(View.GONE)
        val widget = mock<NativeInputModeWidget>()
        testee.init(
            tabId = "tab",
            card = card,
            widget = widget,
            jsMessaging = mock<JsMessaging>(),
            lifecycleOwner = lifecycleOwner(),
            chatIdFlow = emptyFlow(),
            onSearchSubmitted = {},
        )

        testee.onInputMode()

        // The model picker is re-enabled via the bound modelPickerEnabled flow (covered separately);
        // here we assert the contextual card itself is shown in INPUT mode.
        verify(card).visibility = View.VISIBLE
    }

    @Test
    fun `when native chat input disabled and onInputMode then card hidden`() {
        val enabled = MutableStateFlow(false)
        whenever(duckChat.observeNativeChatInputEnabled()).thenReturn(enabled)
        val card = mockCard()
        val widget = mock<NativeInputModeWidget>()
        testee.init(
            tabId = "tab",
            card = card,
            widget = widget,
            jsMessaging = mock<JsMessaging>(),
            lifecycleOwner = lifecycleOwner(),
            chatIdFlow = emptyFlow(),
            onSearchSubmitted = {},
        )

        testee.onInputMode()

        verify(card).visibility = View.GONE
    }

    @Test
    fun `when input mode and chat submitted then routes to new chat callback with widget selections`() {
        val enabled = MutableStateFlow(true)
        whenever(duckChat.observeNativeChatInputEnabled()).thenReturn(enabled)
        val widget = mock<NativeInputModeWidget>()
        whenever(widget.getSelectedModelId()).thenReturn("model-1")
        whenever(widget.getResolvedReasoningEffort()).thenReturn("high")
        whenever(widget.getSelectedTool()).thenReturn("web-search")
        var submitted: NativeInputPrompt? = null
        testee.init(
            tabId = "tab",
            card = mockCard(),
            widget = widget,
            jsMessaging = mock<JsMessaging>(),
            lifecycleOwner = lifecycleOwner(),
            chatIdFlow = emptyFlow(),
            onSearchSubmitted = {},
            onNewChatPromptSubmitted = { submitted = it },
        )
        testee.onInputMode()

        val captor = argumentCaptor<(String) -> Unit>()
        verify(widget).bindInputEvents(any(), any(), captor.capture())
        captor.firstValue.invoke("hello")

        assertEquals("hello", submitted?.prompt)
        assertEquals("model-1", submitted?.modelId)
        assertEquals("high", submitted?.reasoningEffort)
        assertEquals("web-search", submitted?.selectedTool)
    }

    @Test
    fun `when web view mode and chat submitted then sends in-chat prompt event and skips new chat callback`() {
        val enabled = MutableStateFlow(true)
        whenever(duckChat.observeNativeChatInputEnabled()).thenReturn(enabled)
        val widget = mock<NativeInputModeWidget>()
        val jsMessaging = mock<JsMessaging>()
        var submitted: NativeInputPrompt? = null
        testee.init(
            tabId = "tab",
            card = mockCard(),
            widget = widget,
            jsMessaging = jsMessaging,
            lifecycleOwner = lifecycleOwner(),
            chatIdFlow = emptyFlow(),
            onSearchSubmitted = {},
            onNewChatPromptSubmitted = { submitted = it },
        )
        testee.onWebViewMode()

        val captor = argumentCaptor<(String) -> Unit>()
        verify(widget).bindInputEvents(any(), any(), captor.capture())
        captor.firstValue.invoke("hello")

        assertNull(submitted)
        verify(jsMessaging).sendSubscriptionEvent(any())
    }

    private fun mockCard(): MaterialCardView {
        val card = mock<MaterialCardView>()
        val resources = mock<Resources>()
        whenever(card.resources).thenReturn(resources)
        whenever(resources.getDimension(any())).thenReturn(16f)
        whenever(card.shapeAppearanceModel).thenReturn(ShapeAppearanceModel())
        return card
    }

    private fun lifecycleOwner(): LifecycleOwner {
        val owner = object : LifecycleOwner {
            lateinit var registry: LifecycleRegistry
            override val lifecycle: Lifecycle get() = registry
        }
        // createUnsafe bypasses the main-thread assertion so the registry can be driven from the test thread.
        owner.registry = LifecycleRegistry.createUnsafe(owner).apply { currentState = Lifecycle.State.RESUMED }
        return owner
    }
}
