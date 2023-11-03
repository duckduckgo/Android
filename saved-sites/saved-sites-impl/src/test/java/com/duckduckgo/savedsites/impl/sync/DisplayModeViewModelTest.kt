package com.duckduckgo.savedsites.impl.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.savedsites.impl.RealSavedSitesSettingsRepository
import com.duckduckgo.savedsites.impl.SavedSitesSettingsRepository
import com.duckduckgo.savedsites.impl.sync.DisplayModeViewModel.ViewState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DisplayModeViewModelTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    private val savedSitesSettingsStore = FakeSavedSitesSettingsStore(coroutineRule.testScope)
    private val savedSitesSettingsRepository: SavedSitesSettingsRepository = RealSavedSitesSettingsRepository(savedSitesSettingsStore)

    private val testee = DisplayModeViewModel(savedSitesSettingsRepository, coroutineRule.testDispatcherProvider)

    @Test
    fun whenObserverAddedThenCurrentViewStateEmitted() = runTest {
        testee.viewState().test {
            assertEquals(ViewState(), awaitItem())
        }
    }

    @Test
    fun whenDisplayModeChangedThenViewStateIsUpdated() = runTest {
        testee.viewState().test {
            awaitItem()
            testee.onDisplayModeChanged(true)
            assertEquals(ViewState(shareFavoritesEnabled = true), awaitItem())
        }
    }
}
