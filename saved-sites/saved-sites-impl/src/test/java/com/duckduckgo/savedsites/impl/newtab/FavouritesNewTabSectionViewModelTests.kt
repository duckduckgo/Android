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
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.impl.SavedSitesPixelName
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionViewModel.Command.DeleteFavoriteConfirmation
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionViewModel.Command.ShowEditSavedSiteDialog
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionViewModel.SwipeDecision
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class FavouritesNewTabSectionViewModelTests {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockSavedSitesRepository: SavedSitesRepository = mock()
    private val faviconManager: FaviconManager = mock()
    private val syncEngine: SyncEngine = mock()
    private val pixel: Pixel = mock()
    private val feature: FavouritesNewTabSectionFixFeature = mock()
    private val featureToggle: com.duckduckgo.feature.toggles.api.Toggle = mock()

    private lateinit var testee: FavouritesNewTabSectionViewModel

    val favorite1 = Favorite("favorite1", "title", "http://example.com", "timestamp", 0)
    val favorite2 = Favorite("favorite2", "title", "http://example.com", "timestamp", 0)

    @Before
    fun setup() {
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList()))
        whenever(feature.self()).thenReturn(featureToggle)
        whenever(featureToggle.isEnabled()).thenReturn(true)
        testee = FavouritesNewTabSectionViewModel(
            coroutinesTestRule.testDispatcherProvider,
            mockSavedSitesRepository,
            pixel,
            faviconManager,
            syncEngine,
            feature,
        )
    }

    @Test
    fun whenViewModelIsInitializedThenViewStateShouldEmitInitialState() = runTest {
        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.favourites.isEmpty())
            }
        }
    }

    @Test
    fun whenViewModelIsInitializedAndFavouritesPresentThenViewStateShouldEmitCorrectState() = runTest {
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(listOf(favorite1)))
        val testeeWithFavourites = FavouritesNewTabSectionViewModel(
            coroutinesTestRule.testDispatcherProvider,
            mockSavedSitesRepository,
            pixel,
            faviconManager,
            syncEngine,
            feature,
        )

        testeeWithFavourites.viewState.test {
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

    @Test
    fun `when onLongPressTriggered then isLongPressActive returns true`() {
        assertFalse(testee.isLongPressActive())

        testee.onLongPressTriggered()

        assertTrue(testee.isLongPressActive())
    }

    @Test
    fun `when onTouchDown then isLongPressActive returns false`() {
        testee.onLongPressTriggered()
        assertTrue(testee.isLongPressActive())

        testee.onTouchDown(0f, 0f)

        assertFalse(testee.isLongPressActive())
    }

    @Test
    fun `when onTouchUp then isLongPressActive returns false`() {
        testee.onLongPressTriggered()
        assertTrue(testee.isLongPressActive())

        testee.onTouchUp()

        assertFalse(testee.isLongPressActive())
    }

    @Test
    fun `when onTouchMove and dx greater than dy and touchSlop then return HORIZONTAL swipe`() {
        testee.onTouchDown(0f, 0f)

        val decision = testee.onTouchMove(x = 3f, y = 1f, touchSlop = 2)

        assertEquals(SwipeDecision.HORIZONTAL, decision)
    }

    @Test
    fun `when onTouchMove and dy greater than dx and touchSlop then return VERTICAL swipe`() {
        testee.onTouchDown(0f, 0f)

        val decision = testee.onTouchMove(x = 1f, y = 3f, touchSlop = 2)

        assertEquals(SwipeDecision.VERTICAL, decision)
    }

    @Test
    fun `when onTouchMove and dx and dy are equal and greater than touchSlop then return CANCEL_LONG_PRESS`() {
        testee.onTouchDown(0f, 0f)

        val decision = testee.onTouchMove(x = 2f, y = 2f, touchSlop = 1)

        assertEquals(SwipeDecision.CANCEL_LONG_PRESS, decision)
    }

    @Test
    fun `when onTouchMove and dx and dy are less than touchSlop then return null`() {
        testee.onTouchDown(0f, 0f)

        val decision = testee.onTouchMove(x = 1f, y = 1f, touchSlop = 2)

        assertNull(decision)
    }

    @Test
    fun `when onTouchMove and dx and dy and touchSlop are equal then return null`() {
        testee.onTouchDown(0f, 0f)

        val decision = testee.onTouchMove(x = 1f, y = 1f, touchSlop = 1)

        assertNull(decision)
    }

    @Test
    fun `when feature flag enabled and onResume called multiple times then no additional flow collections started`() = runTest {
        val lifecycleOwner: LifecycleOwner = mock()

        testee.viewState.test {
            awaitItem() // initial empty state from stateIn
            testee.onResume(lifecycleOwner)
            testee.onResume(lifecycleOwner)
            testee.onResume(lifecycleOwner)
            expectNoEvents()
        }

        // getFavorites() called once by the stateIn flow, not once per onResume
        verify(mockSavedSitesRepository, times(1)).getFavorites()
    }

    @Test
    fun `when feature flag disabled and onResume called then viewState emits favourites`() = runTest {
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(flowOf(listOf(favorite1)))
        whenever(featureToggle.isEnabled()).thenReturn(false)
        val lifecycleOwner: LifecycleOwner = mock()
        val viewModelWithFixDisabled = FavouritesNewTabSectionViewModel(
            coroutinesTestRule.testDispatcherProvider,
            mockSavedSitesRepository,
            pixel,
            faviconManager,
            syncEngine,
            feature,
        )

        viewModelWithFixDisabled.viewState.test {
            awaitItem() // initial empty state from _legacyViewState
            viewModelWithFixDisabled.onResume(lifecycleOwner)
            val state = awaitItem()
            assertFalse(state.favourites.isEmpty())
            assertTrue(state.favourites.first() == favorite1)
        }
    }
}
