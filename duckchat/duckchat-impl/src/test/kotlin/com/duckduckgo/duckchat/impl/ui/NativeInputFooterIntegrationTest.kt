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

package com.duckduckgo.duckchat.impl.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooter
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterContext
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterCoordinator
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterPlugin
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterState
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterView
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputModeWidget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class NativeInputFooterIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val widgetContext = ContextThemeWrapper(context, com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light)

    @Test
    fun whenSelectedFooterChangesThenHostOwnsOnlyTheSelectedView() = runTest {
        val host = TestNativeInputFooterView(context)
        val hostContext = MutableStateFlow(duckAiContext())
        val primaryState = MutableStateFlow(NativeInputFooterState(visible = true))
        val primaryView = View(context)
        val fallbackView = View(context)
        val coordinator = coordinator(
            plugin(priority = 10, view = primaryView, state = primaryState),
            plugin(priority = 20, view = fallbackView, state = MutableStateFlow(NativeInputFooterState(visible = true))),
        )

        host.bind(this, coordinator.state(context, hostContext))
        host.attach()
        advanceUntilIdle()

        assertEquals(1, host.childCount)
        assertSame(primaryView, host.getChildAt(0))

        primaryState.value = NativeInputFooterState(visible = false)
        advanceUntilIdle()

        assertEquals(1, host.childCount)
        assertSame(fallbackView, host.getChildAt(0))
        host.detach()
    }

    @Test
    fun whenContextualSurfaceIsHiddenThenLaterFooterEmissionsCannotReshowHost() = runTest {
        val host = TestNativeInputFooterView(context)
        val footerState = MutableStateFlow(NativeInputFooterState(visible = true))
        val coordinator = coordinator(plugin(priority = 10, view = View(context), state = footerState))

        host.bind(this, coordinator.state(context, MutableStateFlow(duckAiContext())))
        host.attach()
        advanceUntilIdle()
        assertEquals(View.VISIBLE, host.visibility)

        host.setSurfaceVisible(false)
        footerState.value = NativeInputFooterState(visible = false)
        footerState.value = NativeInputFooterState(visible = true)
        advanceUntilIdle()

        assertEquals(View.GONE, host.visibility)
        host.detach()
    }

    @Test
    fun whenExitAnimationRunsThenHostHidesAndReturnsWhenItStops() = runTest {
        val host = TestNativeInputFooterView(context)
        val coordinator = coordinator(plugin(priority = 10, view = View(context), state = MutableStateFlow(NativeInputFooterState(visible = true))))

        host.bind(this, coordinator.state(context, MutableStateFlow(duckAiContext())))
        host.attach()
        advanceUntilIdle()
        assertEquals(View.VISIBLE, host.visibility)

        host.setExitAnimationRunning(true)
        assertEquals(View.GONE, host.visibility)

        host.setExitAnimationRunning(false)
        assertEquals(View.VISIBLE, host.visibility)
        host.detach()
    }

    @Test
    fun whenFooterHostDetachesThenItsStateCollectionIsCancelled() = runTest {
        val host = TestNativeInputFooterView(context)
        var collectionCancelled = false
        val state = flow {
            emit(NativeInputFooterCoordinator.State(view = View(context)))
            try {
                awaitCancellation()
            } finally {
                collectionCancelled = true
            }
        }

        host.bind(this, state)
        host.attach()
        advanceUntilIdle()

        host.detach()
        advanceUntilIdle()

        assertTrue(collectionCancelled)
        assertEquals(0, host.childCount)
    }

    @Test
    fun whenFooterBlocksComposerThenTypingIsRejectedAndReleasedWhenUnblocked() = runTest {
        val widget = NativeInputModeWidget(widgetContext)

        widget.setFooterInputBlocked(true)
        widget.inputField.text.append("hello")
        assertEquals("", widget.inputField.text.toString())

        widget.setFooterInputBlocked(false)
        widget.inputField.text.append("hello")
        assertEquals("hello", widget.inputField.text.toString())
    }

    @Test
    fun whenFooterBlocksComposerThenProgrammaticTextStillLandsAndTypingStaysRejected() = runTest {
        val widget = NativeInputModeWidget(widgetContext)
        widget.setFooterInputBlocked(true)

        widget.text = "https://example.com"

        assertEquals("https://example.com", widget.text)
        assertEquals("https://example.com".length, widget.inputField.selectionEnd)

        widget.inputField.text.append("x")
        assertEquals("https://example.com", widget.text)
    }

    @Test
    fun whenFooterBlocksComposerThenKeyboardGoActionIsSwallowed() = runTest {
        val widget = NativeInputModeWidget(widgetContext)
        widget.inputField.setText("carried over from search")

        widget.setFooterInputBlocked(true)

        // Without the guard this reaches submitMessage and the uninjected ViewModel, which throws.
        widget.inputField.onEditorAction(EditorInfo.IME_ACTION_GO)
        assertEquals("carried over from search", widget.inputField.text.toString())
    }

    @Test
    fun whenSelectedFooterBlocksComposerThenWidgetLocksWithoutAffectingFooter() = runTest {
        val widget = NativeInputModeWidget(widgetContext)
        val host = TestNativeInputFooterView(context)
        val state = MutableStateFlow(
            NativeInputFooterCoordinator.State(
                view = View(context),
                blocksComposer = true,
            ),
        )

        host.bind(this, state, onBlocksComposerChanged = widget::setFooterInputBlocked)
        host.attach()
        advanceUntilIdle()

        assertEquals(1f, widget.alpha)
        assertEquals(0.4f, widget.findViewById<View>(com.duckduckgo.duckchat.impl.R.id.inputModeWidgetCardContent).alpha)
        assertEquals(0.4f, widget.findViewById<View>(com.duckduckgo.duckchat.impl.R.id.inputModeWidgetBottomRow).alpha)
        assertEquals(1f, widget.findViewById<View>(com.duckduckgo.duckchat.impl.R.id.inputModeSwitchRow).alpha)
        assertTrue(widget.onInterceptTouchEvent(null))
        assertEquals(1f, host.alpha)
        assertTrue(host.isEnabled)
        assertFalse(host.onInterceptTouchEvent(null))
        host.detach()
    }

    @Test
    fun whenNoFooterIsSelectedThenFooterOwnedWidgetLockClears() = runTest {
        val widget = NativeInputModeWidget(widgetContext)
        val host = TestNativeInputFooterView(context)
        val state = MutableStateFlow(
            NativeInputFooterCoordinator.State(
                view = View(context),
                blocksComposer = true,
            ),
        )
        host.bind(this, state, onBlocksComposerChanged = widget::setFooterInputBlocked)
        host.attach()
        advanceUntilIdle()

        state.value = NativeInputFooterCoordinator.State(view = null, blocksComposer = true)
        advanceUntilIdle()

        assertEquals(1f, widget.alpha)
        assertFalse(widget.onInterceptTouchEvent(null))
        host.detach()
    }

    @Test
    fun whenExistingAndFooterLocksCoexistThenClearingEitherOneKeepsTheOther() = runTest {
        val widget = NativeInputModeWidget(widgetContext)
        val host = TestNativeInputFooterView(context)
        val state = MutableStateFlow(
            NativeInputFooterCoordinator.State(
                view = View(context),
                blocksComposer = true,
            ),
        )
        widget.setInteractionLocked(true)
        host.bind(this, state, onBlocksComposerChanged = widget::setFooterInputBlocked)
        host.attach()
        advanceUntilIdle()

        widget.setInteractionLocked(false)
        assertEquals(1f, widget.alpha)
        assertEquals(0.4f, widget.findViewById<View>(com.duckduckgo.duckchat.impl.R.id.inputModeWidgetCardContent).alpha)
        assertTrue(widget.onInterceptTouchEvent(null))

        widget.setInteractionLocked(true)
        state.value = NativeInputFooterCoordinator.State(view = View(context), blocksComposer = false)
        advanceUntilIdle()
        assertEquals(0.4f, widget.alpha)
        assertEquals(1f, widget.findViewById<View>(com.duckduckgo.duckchat.impl.R.id.inputModeWidgetCardContent).alpha)
        assertTrue(widget.onInterceptTouchEvent(null))

        widget.setInteractionLocked(false)
        assertEquals(1f, widget.alpha)
        assertFalse(widget.onInterceptTouchEvent(null))
        host.detach()
    }

    @Test
    fun whenFooterHostUnbindsOrDetachesThenOnlyItsWidgetLockClears() = runTest {
        val widget = NativeInputModeWidget(widgetContext)
        val host = TestNativeInputFooterView(context)
        val blockingState = MutableStateFlow(
            NativeInputFooterCoordinator.State(
                view = View(context),
                blocksComposer = true,
            ),
        )
        host.bind(this, blockingState, onBlocksComposerChanged = widget::setFooterInputBlocked)
        host.attach()
        advanceUntilIdle()

        host.unbind()
        assertEquals(1f, widget.alpha)

        widget.setInteractionLocked(true)
        host.bind(this, blockingState, onBlocksComposerChanged = widget::setFooterInputBlocked)
        advanceUntilIdle()
        host.detach()

        assertEquals(0.4f, widget.alpha)
        assertTrue(widget.onInterceptTouchEvent(null))

        widget.setInteractionLocked(false)
        assertEquals(1f, widget.alpha)
        assertFalse(widget.onInterceptTouchEvent(null))
    }

    private fun coordinator(vararg plugins: NativeInputFooterPlugin): NativeInputFooterCoordinator {
        return NativeInputFooterCoordinator(
            object : ActivePluginPoint<NativeInputFooterPlugin> {
                override suspend fun getPlugins(): Collection<NativeInputFooterPlugin> = plugins.toList()
            },
        )
    }

    private fun plugin(
        priority: Int,
        view: View,
        state: Flow<NativeInputFooterState>,
    ): NativeInputFooterPlugin = object : NativeInputFooterPlugin {
        override val priority: Int = priority

        override fun createFooter(
            context: Context,
            hostContext: StateFlow<NativeInputFooterContext>,
        ): NativeInputFooter = object : NativeInputFooter {
            override val view: View = view
            override val state: Flow<NativeInputFooterState> = state
        }
    }

    private fun duckAiContext() = NativeInputFooterContext(
        isDuckAiSelected = true,
        isEditing = false,
        isFireMode = false,
        isInputFocused = true,
    )

    private class TestNativeInputFooterView(context: Context) : NativeInputFooterView(context) {
        fun attach() = onAttachedToWindow()
        fun detach() = onDetachedFromWindow()
    }
}
