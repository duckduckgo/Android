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

import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.newtabpage.impl.FakeShortcut
import com.duckduckgo.newtabpage.impl.FakeShortcutPlugin
import com.duckduckgo.newtabpage.impl.FakeShortcutPluginPoint
import com.duckduckgo.newtabpage.impl.pixels.NewTabPixels
import com.duckduckgo.newtabpage.impl.settings.NewTabSettingsStore
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ShortcutsViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: ShortcutsViewModel

    private var mockLifecycleOwner: LifecycleOwner = mock()
    private val newTabSettingsStore: NewTabSettingsStore = mock()
    private val newTabShortcutsProvider: NewTabShortcutsProvider = mock()
    private val pixels: NewTabPixels = mock()
    private val shortcutPlugins = FakeShortcutPluginPoint()

    @Before
    fun setup() {
        testee = ShortcutsViewModel(
            coroutineRule.testDispatcherProvider,
            newTabSettingsStore,
            newTabShortcutsProvider,
            pixels,
        )
    }

    @Test
    fun whenViewModelStartsAndNoShortcutsThenViewStateShortcutsAreEmpty() = runTest {
        whenever(newTabShortcutsProvider.provideActiveShortcuts()).thenReturn(flowOf(emptyList()))
        testee.onResume(mockLifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.shortcuts.isEmpty())
            }
        }
    }

    @Test
    fun whenViewModelStartsAndSomeShortcutsThenViewStateShortcutsAreNotEmpty() = runTest {
        whenever(newTabShortcutsProvider.provideActiveShortcuts()).thenReturn(flowOf(shortcutPlugins.getPlugins()))
        testee.onResume(mockLifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.shortcuts.isNotEmpty())
            }
        }
    }

    @Test
    fun whenShortcutPressedThenPixelFired() {
        val shortcut = FakeShortcut("bookmarks")
        testee.onShortcutPressed(FakeShortcutPlugin(shortcut))

        verify(pixels).fireShortcutPressed(shortcut.name)
    }
}
