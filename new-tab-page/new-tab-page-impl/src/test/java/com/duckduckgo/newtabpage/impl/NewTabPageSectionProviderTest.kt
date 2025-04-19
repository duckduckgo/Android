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

import app.cash.turbine.test
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.api.NewTabPageSectionProvider
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
            enabledSectionsPlugins,
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
    fun whenIndonesiaMessageEnabledThenPluginsReturnedInOrder() = runTest {
        whenever(newTabSettingsStore.sectionSettings).thenReturn(
            listOf(
                NewTabPageSection.APP_TRACKING_PROTECTION.name,
                NewTabPageSection.FAVOURITES.name,
                NewTabPageSection.SHORTCUTS.name,
            ),
        )

        testee = RealNewTabPageSectionProvider(
            enabledIndonesiaSectionPlugins,
            activeSectionSettingsPlugins,
            newTabSettingsStore,
        )

        testee.provideSections().test {
            expectMostRecentItem().also {
                assertTrue(it[0].name == NewTabPageSection.INDONESIA_MESSAGE.name)
                assertTrue(it[1].name == NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name)
                assertTrue(it[2].name == NewTabPageSection.APP_TRACKING_PROTECTION.name)
                assertTrue(it[3].name == NewTabPageSection.FAVOURITES.name)
                assertTrue(it[4].name == NewTabPageSection.SHORTCUTS.name)
            }
        }
    }
}
