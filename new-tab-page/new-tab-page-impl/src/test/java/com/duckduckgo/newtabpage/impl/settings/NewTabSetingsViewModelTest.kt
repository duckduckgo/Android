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

package com.duckduckgo.newtabpage.impl.settings

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.newtabpage.api.NewTabShortcut.Bookmarks
import com.duckduckgo.newtabpage.api.NewTabShortcut.Chat
import com.duckduckgo.newtabpage.impl.FakeSettingStore
import com.duckduckgo.newtabpage.impl.FakeShortcutPlugin
import com.duckduckgo.newtabpage.impl.enabledSectionSettingsPlugins
import com.duckduckgo.newtabpage.impl.shortcuts.NewTabShortcutDataStore
import com.duckduckgo.newtabpage.impl.shortcuts.NewTabShortcutsProvider
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NewTabSetingsViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val sectionSettingsProvider: NewTabPageSectionSettingsProvider = mock()
    private val shortcutsProvider: NewTabShortcutsProvider = mock()
    private val shortcutSetting: NewTabShortcutDataStore = mock()
    private val store = FakeSettingStore()

    private lateinit var testee: NewTabSettingsViewModel

    @Before
    fun setup() {
        testee = NewTabSettingsViewModel(
            sectionSettingsProvider,
            shortcutsProvider,
            shortcutSetting,
            store,
            coroutinesTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenViewModelStartsThenCorrectStateEmitted() = runTest {
        whenever(sectionSettingsProvider.provideSections()).thenReturn(flowOf(emptyList()))
        whenever(shortcutsProvider.provideAllShortcuts()).thenReturn(flowOf(emptyList()))
        whenever(shortcutSetting.isEnabled).thenReturn(flowOf(false))

        testee.viewState().test {
            expectMostRecentItem().also {
                assertTrue(it.sections.isEmpty())
                assertTrue(it.shortcuts.isEmpty())
                assertFalse(it.shortcutsManagementEnabled)
            }
        }
    }

    @Test
    fun whenDataIsProvidedThenCorrectStateEmitted() = runTest {
        whenever(sectionSettingsProvider.provideSections()).thenReturn(flowOf(enabledSectionSettingsPlugins))
        whenever(shortcutsProvider.provideAllShortcuts()).thenReturn(
            flowOf(
                listOf(
                    ManageShortcutItem(FakeShortcutPlugin(Chat), true),
                    ManageShortcutItem(FakeShortcutPlugin(Bookmarks), true),
                ),
            ),
        )
        whenever(shortcutSetting.isEnabled).thenReturn(flowOf(true))

        testee.viewState().test {
            expectMostRecentItem().also {
                assertFalse(it.sections.isEmpty())
                assertFalse(it.shortcuts.isEmpty())
                assertTrue(it.shortcutsManagementEnabled)
            }
        }
    }

    @Test
    fun whenShortcutUnselectedThenSettingsUpdated() = runTest {
        whenever(sectionSettingsProvider.provideSections()).thenReturn(flowOf(enabledSectionSettingsPlugins))
        whenever(shortcutsProvider.provideAllShortcuts()).thenReturn(
            flowOf(
                listOf(
                    ManageShortcutItem(FakeShortcutPlugin(Chat), true),
                    ManageShortcutItem(FakeShortcutPlugin(Bookmarks), true),
                ),
            ),
        )
        whenever(shortcutSetting.isEnabled).thenReturn(flowOf(true))

        val shortcut = ManageShortcutItem(FakeShortcutPlugin(Bookmarks), true)

        assertTrue(store.shortcutSettings.size == 5)

        testee.onShortcutSelected(shortcut)

        val shortcuts = store.shortcutSettings
        assertTrue(shortcuts.size == 4)
    }

    @Test
    fun whenShortcutSelectedThenSettingsUpdated() = runTest {
        whenever(sectionSettingsProvider.provideSections()).thenReturn(flowOf(enabledSectionSettingsPlugins))
        whenever(shortcutsProvider.provideAllShortcuts()).thenReturn(
            flowOf(
                listOf(
                    ManageShortcutItem(FakeShortcutPlugin(Chat), true),
                    ManageShortcutItem(FakeShortcutPlugin(Bookmarks), true),
                ),
            ),
        )
        whenever(shortcutSetting.isEnabled).thenReturn(flowOf(true))

        val selectedShortcut = ManageShortcutItem(FakeShortcutPlugin(Bookmarks), false)

        assertTrue(store.shortcutSettings.size == 5)

        testee.onShortcutSelected(selectedShortcut)

        val shortcuts = store.shortcutSettings
        assertTrue(shortcuts.size == 6)
    }
}
