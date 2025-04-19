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

import app.cash.turbine.test
import com.duckduckgo.newtabpage.impl.FakeSettingStore
import com.duckduckgo.newtabpage.impl.disabledShortcutPlugins
import com.duckduckgo.newtabpage.impl.enabledShortcutPlugins
import com.duckduckgo.newtabpage.impl.settings.NewTabSettingsStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class NewTabShortcutsProviderTest {

    private val newTabSettingsStore: NewTabSettingsStore = mock()

    private lateinit var testee: NewTabShortcutsProvider

    @Test
    fun whenShortcutPluginsEnabledThenProvided() = runTest {
        val store = FakeSettingStore()
        testee = RealNewTabPageShortcutProvider(enabledShortcutPlugins, store)

        testee.provideActiveShortcuts().test {
            expectMostRecentItem().also {
                assertTrue(it[0].getShortcut().name() == "bookmarks")
                assertTrue(it[1].getShortcut().name() == "chat")
            }
        }
    }

    @Test
    fun whenShortcutsDisabledThenProvided() = runTest {
        testee = RealNewTabPageShortcutProvider(disabledShortcutPlugins, newTabSettingsStore)
        testee.provideActiveShortcuts().test {
            expectMostRecentItem().also {
                assertTrue(it.isEmpty())
            }
        }
    }
}
