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
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.impl.FakeSettingStore
import com.duckduckgo.newtabpage.impl.FakeShortcut
import com.duckduckgo.newtabpage.impl.FakeShortcutDataStore
import com.duckduckgo.newtabpage.impl.FakeShortcutPlugin
import com.duckduckgo.newtabpage.impl.enabledSectionSettingsPlugins
import com.duckduckgo.newtabpage.impl.pixels.NewTabPixels
import com.duckduckgo.newtabpage.impl.shortcuts.NewTabShortcutsProvider
import kotlinx.coroutines.flow.Flow
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
    private val pixels: NewTabPixels = mock()
    private val shortcutStore = FakeShortcutDataStore()
    private val settingsStore = FakeSettingStore()

    private lateinit var testee: NewTabSettingsViewModel

    @Before
    fun setup() {
        testee = NewTabSettingsViewModel(
            sectionSettingsProvider,
            shortcutsProvider,
            shortcutStore,
            settingsStore,
            pixels,
            coroutinesTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenViewModelStartsThenCorrectStateEmitted() = runTest {
        whenever(sectionSettingsProvider.provideSections()).thenReturn(flowOf(emptyList()))
        whenever(shortcutsProvider.provideAllShortcuts()).thenReturn(flowOf(emptyList()))
        shortcutStore.setIsEnabled(false)

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
        whenever(shortcutsProvider.provideAllShortcuts()).thenReturn(whenAllShortcutsAvailable())
        shortcutStore.setIsEnabled(true)

        testee.viewState().test {
            expectMostRecentItem().also {
                assertFalse(it.sections.isEmpty())
                assertTrue(it.sections.size == 4)
                assertFalse(it.shortcuts.isEmpty())
                assertTrue(it.shortcuts.size == 5)
                assertTrue(it.shortcutsManagementEnabled)
            }
        }
    }

    @Test
    fun whenShortcutUnselectedThenSettingsUpdated() = runTest {
        whenever(sectionSettingsProvider.provideSections()).thenReturn(flowOf(enabledSectionSettingsPlugins))
        whenever(shortcutsProvider.provideAllShortcuts()).thenReturn(whenAllShortcutsAvailable())
        shortcutStore.setIsEnabled(true)

        val shortcut = ManageShortcutItem(FakeShortcutPlugin(FakeShortcut("bookmarks")), true)

        assertTrue(settingsStore.shortcutSettings.size == 5)

        testee.onShortcutSelected(shortcut)

        val shortcuts = settingsStore.shortcutSettings
        assertTrue(shortcuts.size == 4)
    }

    @Test
    fun whenShortcutSelectedThenSettingsUpdated() = runTest {
        whenever(sectionSettingsProvider.provideSections()).thenReturn(flowOf(enabledSectionSettingsPlugins))
        whenever(shortcutsProvider.provideAllShortcuts()).thenReturn(whenAllShortcutsAvailable())
        shortcutStore.setIsEnabled(true)

        val selectedShortcut = ManageShortcutItem(FakeShortcutPlugin(FakeShortcut("newshortcut")), false)

        assertTrue(settingsStore.shortcutSettings.size == 5)

        testee.onShortcutSelected(selectedShortcut)

        val shortcuts = settingsStore.shortcutSettings
        assertTrue(shortcuts.size == 6)
    }

    @Test
    fun whenSectionsSwappedThenStoreUpdate() = runTest {
        whenever(sectionSettingsProvider.provideSections()).thenReturn(flowOf(enabledSectionSettingsPlugins))
        whenever(shortcutsProvider.provideAllShortcuts()).thenReturn(whenAllShortcutsAvailable())
        shortcutStore.setIsEnabled(true)

        assertTrue(
            settingsStore.sectionSettings == listOf(
                NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name,
                NewTabPageSection.APP_TRACKING_PROTECTION.name,
                NewTabPageSection.FAVOURITES.name,
                NewTabPageSection.SHORTCUTS.name,
            ),
        )

        testee.onSectionsSwapped(1, 0)

        assertTrue(
            settingsStore.sectionSettings == listOf(
                NewTabPageSection.APP_TRACKING_PROTECTION.name,
                NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name,
                NewTabPageSection.FAVOURITES.name,
                NewTabPageSection.SHORTCUTS.name,
            ),
        )
    }

    private fun whenAllShortcutsAvailable(): Flow<List<ManageShortcutItem>> {
        return flowOf(
            listOf(
                ManageShortcutItem(FakeShortcutPlugin(FakeShortcut("bookmarks")), true),
                ManageShortcutItem(FakeShortcutPlugin(FakeShortcut("passwords")), true),
                ManageShortcutItem(FakeShortcutPlugin(FakeShortcut("chat")), true),
                ManageShortcutItem(FakeShortcutPlugin(FakeShortcut("downloads")), true),
                ManageShortcutItem(FakeShortcutPlugin(FakeShortcut("settings")), true),
            ),
        )
    }
}
