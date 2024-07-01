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
import com.duckduckgo.newtabpage.api.NewTabPageShortcutPlugin
import com.duckduckgo.newtabpage.api.NewTabShortcut
import com.duckduckgo.newtabpage.api.NewTabShortcut.Chat
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ShortcutsViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private var shortcutsProvider: NewTabShortcutsProvider = mock()
    private val mockOwner: LifecycleOwner = mock()

    private lateinit var testee: ShortcutsViewModel

    @Before
    fun setUp() {
        testee = ShortcutsViewModel(coroutinesTestRule.testDispatcherProvider, shortcutsProvider)
    }

    @Test
    fun whenViewModelStartsThenInitialViewStateProvided() = runTest {
        testee.viewState.test {
            testee.onStart(mockOwner)
            expectMostRecentItem().also {
                assertEquals(it.shortcuts.size, 0)
            }
        }
    }

    @Test
    fun whenViewModelStartsThenShortcutsProvider() = runTest {
        testee.viewState.test {
            whenever(shortcutsProvider.provideShortcuts()).thenReturn(flowOf(someShortcuts()))
            testee.onStart(mockOwner)
            expectMostRecentItem().also {
                assertEquals(it.shortcuts.size, 0)
            }
        }
    }

    private fun someShortcuts(): List<NewTabPageShortcutPlugin> {
        return listOf(
            FakeShortcutPlugin(Chat),
            FakeShortcutPlugin(NewTabShortcut.Bookmarks),
        )
    }

    class FakeShortcutPlugin(val fakeShortcut: NewTabShortcut) : NewTabPageShortcutPlugin {
        override fun getShortcut(): NewTabShortcut {
            return fakeShortcut
        }
    }
}
