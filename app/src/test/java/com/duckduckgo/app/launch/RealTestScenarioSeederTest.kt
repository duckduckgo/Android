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

package com.duckduckgo.app.launch

import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.duckduckgo.savedsites.api.SavedSitesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class RealTestScenarioSeederTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val savedSitesRepository: SavedSitesRepository = mock()
    private val settingsDataStore: SettingsDataStore = mock()
    private val duckChatDataStore: DuckChatDataStore = mock()
    private lateinit var seeder: RealTestScenarioSeeder

    @Before
    fun setup() {
        seeder = RealTestScenarioSeeder(savedSitesRepository, settingsDataStore, duckChatDataStore, coroutineRule.testDispatcherProvider)
    }

    private suspend fun seed(
        isMaestro: String? = "true",
        scenario: String? = null,
        omnibarPosition: String? = null,
        nativeInputToggle: String? = null,
    ) = seeder.seedIfNeeded(
        isMaestroExtra = isMaestro,
        scenarioKey = scenario,
        omnibarPosition = omnibarPosition,
        nativeInputToggle = nativeInputToggle,
    )

    @Test
    fun `when isMaestro extra is absent, nothing is seeded`() = runTest {
        seed(isMaestro = null, scenario = "favorites_3", omnibarPosition = "bottom", nativeInputToggle = "true")

        verifyNoInteractions(savedSitesRepository, settingsDataStore, duckChatDataStore)
    }

    @Test
    fun `when isMaestro is not true, nothing is seeded`() = runTest {
        seed(isMaestro = "false", scenario = "favorites_3", omnibarPosition = "bottom", nativeInputToggle = "true")

        verifyNoInteractions(savedSitesRepository, settingsDataStore, duckChatDataStore)
    }

    @Test
    fun `when isMaestro is true but all args absent, nothing is seeded`() = runTest {
        seed()

        verifyNoInteractions(savedSitesRepository, settingsDataStore, duckChatDataStore)
    }

    @Test
    fun `when scenario key is unknown, no data is seeded`() = runTest {
        seed(scenario = "unknown_scenario_key")

        verifyNoInteractions(savedSitesRepository, settingsDataStore, duckChatDataStore)
    }

    @Test
    fun `when scenario is favorites_3, three favorites are inserted`() = runTest {
        seed(scenario = "favorites_3")

        verify(savedSitesRepository, times(3)).insertFavorite(any(), any(), any(), anyOrNull())
        verify(savedSitesRepository, never()).insertBookmark(any(), any())
    }

    @Test
    fun `when scenario is bookmarks_2, two bookmarks are inserted`() = runTest {
        seed(scenario = "bookmarks_2")

        verify(savedSitesRepository, times(2)).insertBookmark(any(), any())
        verify(savedSitesRepository, never()).insertFavorite(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `when omnibarPosition is top, omnibar type is set to top`() = runTest {
        seed(omnibarPosition = "top")

        verify(settingsDataStore).omnibarType = OmnibarType.SINGLE_TOP
        verifyNoInteractions(savedSitesRepository, duckChatDataStore)
    }

    @Test
    fun `when omnibarPosition is bottom, omnibar type is set to bottom`() = runTest {
        seed(omnibarPosition = "bottom")

        verify(settingsDataStore).omnibarType = OmnibarType.SINGLE_BOTTOM
        verifyNoInteractions(savedSitesRepository, duckChatDataStore)
    }

    @Test
    fun `when omnibarPosition is split, omnibar type is set to split`() = runTest {
        seed(omnibarPosition = "split")

        verify(settingsDataStore).omnibarType = OmnibarType.SPLIT
        verifyNoInteractions(savedSitesRepository, duckChatDataStore)
    }

    @Test
    fun `when omnibarPosition is unknown, omnibar type is not set`() = runTest {
        seed(omnibarPosition = "unknown")

        verifyNoInteractions(savedSitesRepository, settingsDataStore, duckChatDataStore)
    }

    @Test
    fun `when nativeInputToggle is true, duck ai is enabled`() = runTest {
        seed(nativeInputToggle = "true")

        verify(duckChatDataStore).setDuckChatUserEnabled(true)
        verifyNoInteractions(savedSitesRepository, settingsDataStore)
    }

    @Test
    fun `when nativeInputToggle is false, duck ai is disabled`() = runTest {
        seed(nativeInputToggle = "false")

        verify(duckChatDataStore).setDuckChatUserEnabled(false)
        verifyNoInteractions(savedSitesRepository, settingsDataStore)
    }

    @Test
    fun `all three args can be combined independently`() = runTest {
        seed(scenario = "favorites_3", omnibarPosition = "bottom", nativeInputToggle = "true")

        verify(savedSitesRepository, times(3)).insertFavorite(any(), any(), any(), anyOrNull())
        verify(settingsDataStore).omnibarType = OmnibarType.SINGLE_BOTTOM
        verify(duckChatDataStore).setDuckChatUserEnabled(true)
    }
}
