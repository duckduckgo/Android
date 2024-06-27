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
import app.cash.turbine.test
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.newtabpage.api.NewTabPageShortcutPlugin
import com.duckduckgo.newtabpage.api.NewTabShortcut
import com.duckduckgo.newtabpage.api.NewTabShortcut.Bookmarks
import com.duckduckgo.newtabpage.api.NewTabShortcut.Chat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class NewTabShortcutsProviderTest {

    private lateinit var testee: NewTabShortcutsProvider

    @Test
    fun whenShortcutsEnabledThenProvided() = runTest {
        testee = RealNewTabPageShortcutProvider(enabledSectionPlugins)
        testee.provideActiveShortcuts().test {
            expectMostRecentItem().also {
                assertTrue(it[0].getShortcut() == Bookmarks)
                assertTrue(it[1].getShortcut() == Chat)
            }
        }
    }

    @Test
    fun whenShortcutsDisabledThenProvided() = runTest {
        testee = RealNewTabPageShortcutProvider(disabledSectionPlugins)
        testee.provideActiveShortcuts().test {
            expectMostRecentItem().also {
                assertTrue(it.isEmpty())
            }
        }
    }

    private val enabledSectionPlugins = object : ActivePluginPoint<NewTabPageShortcutPlugin> {
        override suspend fun getPlugins(): Collection<NewTabPageShortcutPlugin> {
            return listOf(
                FakeShortcutPlugin(Bookmarks),
                FakeShortcutPlugin(Chat),
            )
        }
    }

    private val disabledSectionPlugins = object : ActivePluginPoint<NewTabPageShortcutPlugin> {
        override suspend fun getPlugins(): Collection<NewTabPageShortcutPlugin> {
            return emptyList()
        }
    }

    private class FakeShortcutPlugin(val fakeShortcut: NewTabShortcut) : NewTabPageShortcutPlugin {
        override fun getShortcut(): NewTabShortcut {
            return fakeShortcut
        }

        override fun onClick(
            context: Context,
        ) {
            // no-op
        }
    }
}
