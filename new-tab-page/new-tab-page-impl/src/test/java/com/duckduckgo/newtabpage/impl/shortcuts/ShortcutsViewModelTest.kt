/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.newtabpage.impl.shortcuts

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.newtabpage.api.NewTabPageShortcutPlugin
import com.duckduckgo.newtabpage.api.NewTabShortcut
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ShortcutsViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: ShortcutsViewModel

    private var mockLifecycleOwner: LifecycleOwner = mock()
    private val newTabShortcutsProvider: NewTabShortcutsProvider = mock()
    private val shortcutPlugins = FakePluginPoint()

    @Before
    fun setup() {
        testee = ShortcutsViewModel(
            coroutineRule.testDispatcherProvider,
            newTabShortcutsProvider,
        )
    }

    @Test
    fun whenViewModelStartsAndNoShortcutsThenViewStateShortcutsAreEmpty() = runTest {
        whenever(newTabShortcutsProvider.provideActiveShortcuts()).thenReturn(flowOf(emptyList()))
        testee.onStart(mockLifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.shortcuts.isEmpty())
            }
        }
    }

    @Test
    fun whenViewModelStartsAndSomeShortcutsThenViewStateShortcutsAreNotEmpty() = runTest {
        whenever(newTabShortcutsProvider.provideActiveShortcuts()).thenReturn(flowOf(shortcutPlugins.getPlugins()))
        testee.onStart(mockLifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.shortcuts.isNotEmpty())
            }
        }
    }

    private class FakePluginPoint : PluginPoint<NewTabPageShortcutPlugin> {
        val plugin = FakeShortcutPlugin()
        override fun getPlugins(): List<NewTabPageShortcutPlugin> {
            return listOf(plugin)
        }
    }

    private class FakeShortcutPlugin : NewTabPageShortcutPlugin {
        override fun getShortcut(): NewTabShortcut {
            return NewTabShortcut.Bookmarks
        }

        override fun onClick(
            context: Context,
            shortcut: NewTabShortcut,
        ) {
            // no - op
        }
    }
}
