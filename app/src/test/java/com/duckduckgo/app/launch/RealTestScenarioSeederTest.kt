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

import android.content.Intent
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

    @Test
    fun `when isMaestro extra is absent, nothing is seeded`() = runTest {
        val intent = Intent()

        seeder.seedIfNeeded(intent)

        verify(savedSitesRepository, never()).insertFavorite(any(), any(), any(), any())
        verify(savedSitesRepository, never()).insertBookmark(any(), any())
        verifyNoInteractions(settingsDataStore, duckChatDataStore)
    }

    @Test
    fun `when isMaestro is true but testScenario is absent, nothing is seeded`() = runTest {
        val intent = Intent().apply {
            putExtra("isMaestro", "true")
        }

        seeder.seedIfNeeded(intent)

        verify(savedSitesRepository, never()).insertFavorite(any(), any(), any(), any())
        verify(savedSitesRepository, never()).insertBookmark(any(), any())
        verifyNoInteractions(settingsDataStore, duckChatDataStore)
    }

    @Test
    fun `when isMaestro is true but testScenario key is unknown, nothing is seeded`() = runTest {
        val intent = Intent().apply {
            putExtra("isMaestro", "true")
            putExtra("testScenario", "unknown_scenario_key")
        }

        seeder.seedIfNeeded(intent)

        verify(savedSitesRepository, never()).insertFavorite(any(), any(), any(), any())
        verify(savedSitesRepository, never()).insertBookmark(any(), any())
        verifyNoInteractions(settingsDataStore, duckChatDataStore)
    }

    @Test
    fun `when isMaestro is present but not true, nothing is seeded`() = runTest {
        val intent = Intent().apply {
            putExtra("isMaestro", "false")
            putExtra("testScenario", "native_input_favorites_3")
        }

        seeder.seedIfNeeded(intent)

        verify(savedSitesRepository, never()).insertFavorite(any(), any(), any(), any())
        verify(savedSitesRepository, never()).insertBookmark(any(), any())
        verifyNoInteractions(settingsDataStore, duckChatDataStore)
    }

    @Test
    fun `when scenario is NATIVE_INPUT_FAVORITES_3, three favorites are inserted`() = runTest {
        val intent = Intent().apply {
            putExtra("isMaestro", "true")
            putExtra("testScenario", "native_input_favorites_3")
        }

        seeder.seedIfNeeded(intent)

        verify(savedSitesRepository, times(3)).insertFavorite(any(), any(), any(), any())
        verify(savedSitesRepository, never()).insertBookmark(any(), any())
    }

    @Test
    fun `when scenario is NATIVE_INPUT_BOOKMARKS_2, two bookmarks are inserted`() = runTest {
        val intent = Intent().apply {
            putExtra("isMaestro", "true")
            putExtra("testScenario", "native_input_bookmarks_2")
        }

        seeder.seedIfNeeded(intent)

        verify(savedSitesRepository, times(2)).insertBookmark(any(), any())
        verify(savedSitesRepository, never()).insertFavorite(any(), any(), any(), any())
    }

    @Test
    fun `when scenario is NATIVE_INPUT_OMNIBAR_BOTTOM, omnibar type is set to bottom`() = runTest {
        val intent = Intent().apply {
            putExtra("isMaestro", "true")
            putExtra("testScenario", "native_input_omnibar_bottom")
        }

        seeder.seedIfNeeded(intent)

        verify(settingsDataStore).omnibarType = OmnibarType.SINGLE_BOTTOM
        verify(savedSitesRepository, never()).insertFavorite(any(), any(), any(), any())
        verifyNoInteractions(duckChatDataStore)
    }

    @Test
    fun `when scenario is NATIVE_INPUT_OMNIBAR_TOP, omnibar type is set to top`() = runTest {
        val intent = Intent().apply {
            putExtra("isMaestro", "true")
            putExtra("testScenario", "native_input_omnibar_top")
        }

        seeder.seedIfNeeded(intent)

        verify(settingsDataStore).omnibarType = OmnibarType.SINGLE_TOP
        verify(savedSitesRepository, never()).insertFavorite(any(), any(), any(), any())
        verifyNoInteractions(duckChatDataStore)
    }

    @Test
    fun `when scenario is NATIVE_INPUT_DUCK_AI_ENABLED, duck ai user enabled is set to true`() = runTest {
        val intent = Intent().apply {
            putExtra("isMaestro", "true")
            putExtra("testScenario", "native_input_duck_ai_enabled")
        }

        seeder.seedIfNeeded(intent)

        verify(duckChatDataStore).setDuckChatUserEnabled(true)
        verify(savedSitesRepository, never()).insertFavorite(any(), any(), any(), any())
        verifyNoInteractions(settingsDataStore)
    }

    @Test
    fun `when scenario is NATIVE_INPUT_DUCK_AI_DISABLED, duck ai user enabled is set to false`() = runTest {
        val intent = Intent().apply {
            putExtra("isMaestro", "true")
            putExtra("testScenario", "native_input_duck_ai_disabled")
        }

        seeder.seedIfNeeded(intent)

        verify(duckChatDataStore).setDuckChatUserEnabled(false)
        verify(savedSitesRepository, never()).insertFavorite(any(), any(), any(), any())
        verifyNoInteractions(settingsDataStore)
    }
}
