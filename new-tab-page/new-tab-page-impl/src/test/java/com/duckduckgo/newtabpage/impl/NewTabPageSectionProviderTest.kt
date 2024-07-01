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

package com.duckduckgo.newtabpage.impl

import android.content.Context
import android.view.View
import app.cash.turbine.test
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.api.NewTabPageSectionPlugin
import com.duckduckgo.newtabpage.api.NewTabPageSectionProvider
import com.duckduckgo.newtabpage.api.NewTabPageSectionSettingsPlugin
import com.duckduckgo.newtabpage.impl.settings.NewTabSettingsStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NewTabPageSectionProviderTest {

    private lateinit var testee: NewTabPageSectionProvider

    private var newTabSettingsStore: NewTabSettingsStore = mock()

    @Before
    fun setup() {
        whenever(newTabSettingsStore.sectionSettings).thenReturn(
            listOf(
                NewTabPageSection.APP_TRACKING_PROTECTION.name,
                NewTabPageSection.FAVOURITES.name,
                NewTabPageSection.SHORTCUTS.name,
            ),
        )
    }

    @Test
    fun whenNoSectionsActiveThenNoPluginsReturned() = runTest {
        testee = RealNewTabPageSectionProvider(
            disabledSectionPlugins,
            disabledSectionSettingsPlugins,
            newTabSettingsStore,
        )

        testee.provideSections().test {
            expectMostRecentItem().also {
                assertTrue(it.isEmpty())
            }
        }
    }

    @Test
    fun whenSectionsAllActiveThenPluginsReturnedInOrder() = runTest {
        testee = RealNewTabPageSectionProvider(
            enabledSectionPlugins,
            activeSectionSettingsPlugins,
            newTabSettingsStore,
        )

        testee.provideSections().test {
            expectMostRecentItem().also {
                assertTrue(it[0].name == NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name)
                assertTrue(it[1].name == NewTabPageSection.APP_TRACKING_PROTECTION.name)
                assertTrue(it[2].name == NewTabPageSection.FAVOURITES.name)
                assertTrue(it[3].name == NewTabPageSection.SHORTCUTS.name)
            }
        }
    }

    @Test
    fun whenUserDisabledFavoritesThenPluginsReturnedInOrder() = runTest {
        whenever(newTabSettingsStore.sectionSettings).thenReturn(
            listOf(
                NewTabPageSection.APP_TRACKING_PROTECTION.name,
                NewTabPageSection.SHORTCUTS.name,
            ),
        )

        testee = RealNewTabPageSectionProvider(
            enabledSectionPlugins,
            activeSectionSettingsPlugins,
            newTabSettingsStore,
        )

        testee.provideSections().test {
            expectMostRecentItem().also {
                assertTrue(it[0].name == NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name)
                assertTrue(it[1].name == NewTabPageSection.APP_TRACKING_PROTECTION.name)
                assertTrue(it[2].name == NewTabPageSection.SHORTCUTS.name)
            }
        }
    }

    @Test
    fun whenRemoteDisabledFavoritesThenPluginsReturnedInOrder() = runTest {
        whenever(newTabSettingsStore.sectionSettings).thenReturn(
            listOf(
                NewTabPageSection.APP_TRACKING_PROTECTION.name,
                NewTabPageSection.FAVOURITES.name,
                NewTabPageSection.SHORTCUTS.name,
            ),
        )

        testee = RealNewTabPageSectionProvider(
            favoriteDisabledSectionPlugins,
            activeSectionSettingsPlugins,
            newTabSettingsStore,
        )

        testee.provideSections().test {
            expectMostRecentItem().also {
                assertTrue(it[0].name == NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name)
                assertTrue(it[1].name == NewTabPageSection.APP_TRACKING_PROTECTION.name)
                assertTrue(it[2].name == NewTabPageSection.SHORTCUTS.name)
            }
        }
    }

    @Test
    fun whenDisabledFavoritesSettingThenPluginsReturnedInOrder() = runTest {
        whenever(newTabSettingsStore.sectionSettings).thenReturn(
            listOf(
                NewTabPageSection.APP_TRACKING_PROTECTION.name,
                NewTabPageSection.SHORTCUTS.name,
            ),
        )

        testee = RealNewTabPageSectionProvider(
            enabledSectionPlugins,
            activeSectionSettingsPlugins,
            newTabSettingsStore,
        )

        testee.provideSections().test {
            expectMostRecentItem().also {
                assertTrue(it[0].name == NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name)
                assertTrue(it[1].name == NewTabPageSection.APP_TRACKING_PROTECTION.name)
                assertTrue(it[2].name == NewTabPageSection.SHORTCUTS.name)
            }
        }
    }

    private val activeSectionSettingsPlugins = object : PluginPoint<NewTabPageSectionSettingsPlugin> {
        override fun getPlugins(): Collection<NewTabPageSectionSettingsPlugin> {
            return listOf(
                FakeActiveSectionSettingPlugin(NewTabPageSection.APP_TRACKING_PROTECTION.name, true),
                FakeActiveSectionSettingPlugin(NewTabPageSection.FAVOURITES.name, true),
                FakeActiveSectionSettingPlugin(NewTabPageSection.SHORTCUTS.name, true),
            )
        }
    }

    private val disabledSectionSettingsPlugins = object : PluginPoint<NewTabPageSectionSettingsPlugin> {
        override fun getPlugins(): Collection<NewTabPageSectionSettingsPlugin> {
            return listOf(
                FakeActiveSectionSettingPlugin(NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name, false),
                FakeActiveSectionSettingPlugin(NewTabPageSection.APP_TRACKING_PROTECTION.name, false),
                FakeActiveSectionSettingPlugin(NewTabPageSection.FAVOURITES.name, false),
                FakeActiveSectionSettingPlugin(NewTabPageSection.SHORTCUTS.name, false),
            )
        }
    }

    private class FakeActiveSectionSettingPlugin(
        val section: String,
        val isEnabled: Boolean,
    ) : NewTabPageSectionSettingsPlugin {
        override val name: String
            get() = section

        override fun getView(context: Context): View? {
            return null
        }

        override suspend fun isActive(): Boolean {
            return isEnabled
        }
    }

    private val enabledSectionPlugins = object : ActivePluginPoint<NewTabPageSectionPlugin> {
        override suspend fun getPlugins(): Collection<NewTabPageSectionPlugin> {
            return listOf(
                FakeEnabledSectionPlugin(NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name, true),
                FakeEnabledSectionPlugin(NewTabPageSection.APP_TRACKING_PROTECTION.name, true),
                FakeEnabledSectionPlugin(NewTabPageSection.FAVOURITES.name, true),
                FakeEnabledSectionPlugin(NewTabPageSection.SHORTCUTS.name, true),
            )
        }
    }

    private val favoriteDisabledSectionPlugins = object : ActivePluginPoint<NewTabPageSectionPlugin> {
        override suspend fun getPlugins(): Collection<NewTabPageSectionPlugin> {
            return listOf(
                FakeEnabledSectionPlugin(NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name, true),
                FakeEnabledSectionPlugin(NewTabPageSection.APP_TRACKING_PROTECTION.name, true),
                FakeEnabledSectionPlugin(NewTabPageSection.FAVOURITES.name, false),
                FakeEnabledSectionPlugin(NewTabPageSection.SHORTCUTS.name, true),
            )
        }
    }

    private val disabledSectionPlugins = object : ActivePluginPoint<NewTabPageSectionPlugin> {
        override suspend fun getPlugins(): Collection<NewTabPageSectionPlugin> {
            return listOf(
                FakeEnabledSectionPlugin(NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name, false),
                FakeEnabledSectionPlugin(NewTabPageSection.APP_TRACKING_PROTECTION.name, false),
                FakeEnabledSectionPlugin(NewTabPageSection.FAVOURITES.name, false),
                FakeEnabledSectionPlugin(NewTabPageSection.SHORTCUTS.name, false),
            )
        }
    }

    private class FakeEnabledSectionPlugin(
        val section: String,
        val isUserEnabled: Boolean,
    ) : NewTabPageSectionPlugin {
        override val name: String
            get() = section

        override fun getView(context: Context): View? {
            return null
        }

        override suspend fun isUserEnabled(): Boolean {
            return isUserEnabled
        }
    }
}
