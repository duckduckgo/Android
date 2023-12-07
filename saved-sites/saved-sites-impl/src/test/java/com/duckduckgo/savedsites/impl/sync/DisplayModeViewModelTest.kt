package com.duckduckgo.savedsites.impl.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.savedsites.impl.FavoritesDisplayModeSettingsRepository
import com.duckduckgo.savedsites.impl.RealFavoritesDisplayModeSettingsRepository
import com.duckduckgo.savedsites.impl.sync.DisplayModeViewModel.ViewState
import com.duckduckgo.sync.api.SyncState
import com.duckduckgo.sync.api.SyncStateMonitor
import com.duckduckgo.sync.settings.api.SyncSettingsListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class DisplayModeViewModelTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    private val savedSitesSettingsStore = FakeSavedSitesSettingsStore(coroutineRule.testScope)
    private val syncSettingsListener: SyncSettingsListener = mock()
    private val syncableSetting: DisplayModeSyncableSetting = DisplayModeSyncableSetting(
        savedSitesSettingsStore,
        syncSettingsListener,
    )
    private val syncStateFlow = MutableStateFlow(SyncState.READY)
    private val syncStateMonitor: SyncStateMonitor = FakeSyncStateMonitor(syncStateFlow)
    private val favoritesDisplayModeSettingsRepository: FavoritesDisplayModeSettingsRepository = RealFavoritesDisplayModeSettingsRepository(
        savedSitesSettingsStore,
        syncableSetting,
        syncStateMonitor,
        mock(),
        coroutineRule.testScope,
        coroutineRule.testDispatcherProvider,
    )

    private val testee = DisplayModeViewModel(favoritesDisplayModeSettingsRepository, coroutineRule.testDispatcherProvider)

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

class FakeSyncStateMonitor(private val syncStateFlow: Flow<SyncState>) : SyncStateMonitor {
    override fun syncState(): Flow<SyncState> = syncStateFlow
}
