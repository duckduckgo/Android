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

package com.duckduckgo.duckchat.impl.nativeinput.footer

import android.content.Context
import android.view.View
import app.cash.turbine.test
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class NativeInputFooterCoordinatorTest {

    private val context: Context = mock()
    private val hostContext = MutableStateFlow(
        NativeInputFooterContext(
            isDuckAiSelected = true,
            isEditing = false,
            isFireMode = false,
            isInputFocused = true,
        ),
    )

    @Test
    fun whenMultipleFootersAreVisibleThenLowestPriorityValueWins() = runTest {
        val lowerPriorityView: View = mock()
        val higherPriorityView: View = mock()
        val testee = coordinator(
            plugin(priority = 20, footer = footer(view = lowerPriorityView, visible = true)),
            plugin(priority = 10, footer = footer(view = higherPriorityView, visible = true)),
        )

        testee.state(context, hostContext, FakeNativeInputFooterHost()).test {
            assertSame(higherPriorityView, awaitItem().view)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSelectedFooterHidesThenNextVisibleFooterIsSelected() = runTest {
        val selectedState = MutableStateFlow(NativeInputFooterState(visible = true))
        val selectedView: View = mock()
        val fallbackView: View = mock()
        val testee = coordinator(
            plugin(priority = 10, footer = footer(view = selectedView, state = selectedState)),
            plugin(priority = 20, footer = footer(view = fallbackView, visible = true)),
        )

        testee.state(context, hostContext, FakeNativeInputFooterHost()).test {
            assertSame(selectedView, awaitItem().view)

            selectedState.value = NativeInputFooterState(visible = false)

            assertSame(fallbackView, awaitItem().view)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAllFootersAreHiddenThenNothingIsSelected() = runTest {
        val testee = coordinator(
            plugin(priority = 10, footer = footer(view = mock(), visible = false)),
            plugin(priority = 20, footer = footer(view = mock(), visible = false)),
        )

        testee.state(context, hostContext, FakeNativeInputFooterHost()).test {
            val state = awaitItem()

            assertNull(state.view)
            assertFalse(state.blocksComposer)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFooterIsSelectedThenItsComposerBlockingStateIsReported() = runTest {
        val selectedState = MutableStateFlow(
            NativeInputFooterState(
                visible = true,
                blocksComposer = true,
            ),
        )
        val testee = coordinator(
            plugin(priority = 10, footer = footer(view = mock(), visible = false, blocksComposer = false)),
            plugin(priority = 20, footer = footer(view = mock(), state = selectedState)),
        )

        testee.state(context, hostContext, FakeNativeInputFooterHost()).test {
            assertTrue(awaitItem().blocksComposer)

            selectedState.value = NativeInputFooterState(
                visible = true,
                blocksComposer = false,
            )

            assertFalse(awaitItem().blocksComposer)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFooterIsNotSelectedThenItsViewIsNotCreated() = runTest {
        var unselectedViewAccessed = false
        val selectedView: View = mock()
        val unselectedFooter = FakeFooter(
            viewProvider = {
                unselectedViewAccessed = true
                mock()
            },
            state = MutableStateFlow(NativeInputFooterState(visible = true)),
        )
        val testee = coordinator(
            plugin(priority = 10, footer = footer(view = selectedView, visible = true)),
            plugin(priority = 20, footer = unselectedFooter),
        )

        testee.state(context, hostContext, FakeNativeInputFooterHost()).test {
            assertSame(selectedView, awaitItem().view)
            assertFalse(unselectedViewAccessed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun coordinator(vararg plugins: NativeInputFooterPlugin): NativeInputFooterCoordinator {
        return NativeInputFooterCoordinator(FakeActivePluginPoint(plugins.toList()))
    }

    private fun plugin(
        priority: Int,
        footer: NativeInputFooter,
    ): NativeInputFooterPlugin = object : NativeInputFooterPlugin {
        override val priority: Int = priority

        override fun createFooter(
            context: Context,
            hostContext: StateFlow<NativeInputFooterContext>,
            host: NativeInputFooterHost,
        ): NativeInputFooter = footer
    }

    private fun footer(
        view: View,
        visible: Boolean,
        blocksComposer: Boolean = false,
    ): NativeInputFooter {
        return footer(
            view = view,
            state = MutableStateFlow(
                NativeInputFooterState(
                    visible = visible,
                    blocksComposer = blocksComposer,
                ),
            ),
        )
    }

    private fun footer(
        view: View,
        state: Flow<NativeInputFooterState>,
    ): NativeInputFooter = FakeFooter(viewProvider = { view }, state = state)

    private class FakeFooter(
        private val viewProvider: () -> View,
        override val state: Flow<NativeInputFooterState>,
    ) : NativeInputFooter {
        override val view: View
            get() = viewProvider()
    }

    private class FakeActivePluginPoint(
        private val plugins: Collection<NativeInputFooterPlugin>,
    ) : ActivePluginPoint<NativeInputFooterPlugin> {
        override suspend fun getPlugins(): Collection<NativeInputFooterPlugin> = plugins
    }
}
