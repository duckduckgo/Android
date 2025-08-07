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

package com.duckduckgo.savedsites.impl.newtab

import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.impl.SavedSitesPixelName
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionViewModel.Command.DeleteFavoriteConfirmation
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionViewModel.Command.ShowEditSavedSiteDialog
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class FavouritesNewTabSectionViewModelTests {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private var mockLifecycleOwner: LifecycleOwner = mock()
    private val mockSavedSitesRepository: SavedSitesRepository = mock()
    private val faviconManager: FaviconManager = mock()
    private val syncEngine: SyncEngine = mock()
    private val pixel: Pixel = mock()

    private lateinit var testee: FavouritesNewTabSectionViewModel

    val favorite1 = Favorite("favorite1", "title", "http://example.com", "timestamp", 0)
    val favorite2 = Favorite("favorite2", "title", "http://example.com", "timestamp", 0)

    @Before
    fun setup() {
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))
        testee = FavouritesNewTabSectionViewModel(
            coroutinesTestRule.testDispatcherProvider,
            mockSavedSitesRepository,
            pixel,
            faviconManager,
            syncEngine,
        )
    }

    @Test
    fun whenViewModelIsInitializedThenViewStateShouldEmitInitialState() = runTest {
        testee.onResume(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.favourites.isEmpty())
            }
        }
    }

    @Test
    fun whenViewModelIsInitializedAndFavouritesPresentThenViewStateShouldEmitCorrectState() = runTest {
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(listOf(favorite1)))

        testee.onResume(mockLifecycleOwner)

        testee.viewState.test {
            expectMostRecentItem().also {
                assertFalse(it.favourites.isEmpty())
                assertTrue(it.favourites.first() == favorite1)
            }
        }
    }

    @Test
    fun whenItemsChangedWithSameItemsThenRepositoryUpdated() {
        whenever(mockSavedSitesRepository.getFavoritesSync()).thenReturn(listOf(favorite1, favorite2))
        val itemsChanged = listOf(favorite2, favorite1)

        testee.onQuickAccessListChanged(itemsChanged)

        verify(mockSavedSitesRepository).updateWithPosition(itemsChanged)
    }

    @Test
    fun whenItemsChangedSavedSitesEmptyThenRepositoryUpdated() {
        whenever(mockSavedSitesRepository.getFavoritesSync()).thenReturn(emptyList())
        val itemsChanged = listOf(favorite1, favorite2)

        testee.onQuickAccessListChanged(itemsChanged)

        verify(mockSavedSitesRepository).updateWithPosition(itemsChanged)
    }

    @Test
    fun whenItemsChangedNewListIsEmptyThenRepositoryUpdated() {
        val storedFavorites = listOf(favorite1, favorite2)
        whenever(mockSavedSitesRepository.getFavoritesSync()).thenReturn(storedFavorites)
        val itemsChanged = emptyList<Favorite>()

        testee.onQuickAccessListChanged(itemsChanged)

        verify(mockSavedSitesRepository).updateWithPosition(storedFavorites)
    }

    @Test
    fun whenItemsChangedLessSavedSitesThenRepositoryUpdated() {
        val storedFavorites = listOf(favorite1)
        whenever(mockSavedSitesRepository.getFavoritesSync()).thenReturn(storedFavorites)
        val itemsChanged = listOf(favorite1, favorite2)

        testee.onQuickAccessListChanged(itemsChanged)

        verify(mockSavedSitesRepository).updateWithPosition(itemsChanged)
    }

    @Test
    fun whenItemsChangedMoreSavedSitesThenRepositoryUpdated() {
        val storedFavorites = listOf(favorite1, favorite2)
        whenever(mockSavedSitesRepository.getFavoritesSync()).thenReturn(storedFavorites)
        val itemsChanged = listOf(favorite1)

        testee.onQuickAccessListChanged(itemsChanged)

        verify(mockSavedSitesRepository).updateWithPosition(listOf(favorite1, favorite2))
    }

    @Test
    fun onEditSavedSiteRequestedThenCommandSent() = runTest {
        testee.commands().test {
            testee.onEditSavedSiteRequested(favorite1)
            expectMostRecentItem().also {
                assertTrue(it is ShowEditSavedSiteDialog)
            }
        }
    }

    @Test
    fun onDeleteFavouriteRequestedThenCommandSent() = runTest {
        testee.commands().test {
            testee.onDeleteFavoriteRequested(favorite1)
            expectMostRecentItem().also {
                assertTrue(it is DeleteFavoriteConfirmation)
            }
        }
    }

    @Test
    fun onDeleteFavouriteRequestedThenRepositoryUpdated() = runTest {
        testee.onDeleteFavoriteSnackbarDismissed(favorite1)

        verify(mockSavedSitesRepository).delete(savedSite = favorite1, deleteBookmark = false)
        verifyNoInteractions(faviconManager)
    }

    @Test
    fun onNewTabShownThenSyncEngineTriggered() = runTest {
        testee.onNewTabFavouritesShown()

        verify(syncEngine).triggerSync(FEATURE_READ)
    }

    @Test
    fun whenTooltipPressedThenPixelSent() = runTest {
        testee.onTooltipPressed()

        verify(pixel).fire(SavedSitesPixelName.FAVOURITES_TOOLTIP_PRESSED)
    }

    @Test
    fun whenListExpandedThenPixelSent() = runTest {
        testee.onListExpanded()

        verify(pixel).fire(SavedSitesPixelName.FAVOURITES_LIST_EXPANDED)
    }

    @Test
    fun whenListCollapsedThenPixelSent() = runTest {
        testee.onListCollapsed()

        verify(pixel).fire(SavedSitesPixelName.FAVOURITES_LIST_COLLAPSED)
    }
}
