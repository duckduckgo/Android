package com.duckduckgo.app.tabs.ui

import app.cash.turbine.test
import com.duckduckgo.app.tabs.model.TabDataRepository
import com.duckduckgo.app.tabs.model.TabSwitcherData
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.model.TabSwitcherData.UserState
import com.duckduckgo.app.tabs.store.TabSwitcherDataStore
import com.duckduckgo.app.trackerdetection.api.WebTrackersBlockedAppRepository
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class TabSwitcherTileAnimationMonitorTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val testDispatcherProvider = coroutinesTestRule.testDispatcherProvider
    private lateinit var fakeTabSwitcherPrefsDataStore: TabSwitcherDataStore

    @Mock
    private lateinit var mockTabDataRepository: TabDataRepository

    @Mock
    private lateinit var mockWebTrackersBlockedAppRepository: WebTrackersBlockedAppRepository

    private lateinit var testee: TabSwitcherTileAnimationMonitor

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        fakeTabSwitcherPrefsDataStore = FakeTabSwitcherPrefsDataStore()

        testee = TabSwitcherTileAnimationMonitor(
            testDispatcherProvider,
            fakeTabSwitcherPrefsDataStore,
            mockTabDataRepository,
            mockWebTrackersBlockedAppRepository
        )
    }

    @Test
    fun `when animation tile has been dismissed then animation tile not shown`() = runTest {
        fakeTabSwitcherPrefsDataStore.setIsAnimationTileDismissed(true)

        testee.observeAnimationTileVisibility().test {
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `when animation tile has been seen then animation tile shown`() = runTest {
        fakeTabSwitcherPrefsDataStore.setIsAnimationTileDismissed(false)
        fakeTabSwitcherPrefsDataStore.setAnimationTileSeen()

        testee.observeAnimationTileVisibility().test {
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `when animation tile has not been dismissed and minimum requirements met then animation tile shown`() = runTest {
        fakeTabSwitcherPrefsDataStore.setIsAnimationTileDismissed(false)

        whenever(mockTabDataRepository.getOpenTabCount()).thenReturn(2)
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(10)

        testee.observeAnimationTileVisibility().test {
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `when animation tile not dismissed and seen tracker count below minimum then animation tile not shown`() = runTest {
        fakeTabSwitcherPrefsDataStore.setIsAnimationTileDismissed(false)

        whenever(mockTabDataRepository.getOpenTabCount()).thenReturn(2)
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(9)

        testee.observeAnimationTileVisibility().test {
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `when animation tile not dismissed and tab count below minimum then animation tile not shown`() = runTest {
        fakeTabSwitcherPrefsDataStore.setIsAnimationTileDismissed(false)

        whenever(mockTabDataRepository.getOpenTabCount()).thenReturn(1)
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(10)

        testee.observeAnimationTileVisibility().test {
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `when animation tile not dismissed and tracker count and tab count below minimum then animation tile not shown`() = runTest {
        fakeTabSwitcherPrefsDataStore.setIsAnimationTileDismissed(false)

        whenever(mockTabDataRepository.getOpenTabCount()).thenReturn(1)
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(9)

        testee.observeAnimationTileVisibility().test {
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `when animation tile not dismissed and tracker count and tab count empty then animation tile not shown`() = runTest {
        fakeTabSwitcherPrefsDataStore.setIsAnimationTileDismissed(false)

        whenever(mockTabDataRepository.getOpenTabCount()).thenReturn(0)
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(0)

        testee.observeAnimationTileVisibility().test {
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `when animation tile not dismissed and has been seen and tracker count tab count below minimum then animation tile shown`() = runTest {
        fakeTabSwitcherPrefsDataStore.setIsAnimationTileDismissed(false)
        fakeTabSwitcherPrefsDataStore.setAnimationTileSeen()

        whenever(mockTabDataRepository.getOpenTabCount()).thenReturn(1)
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(10)

        testee.observeAnimationTileVisibility().test {
            assertEquals(true, awaitItem())
        }
    }

    private class FakeTabSwitcherPrefsDataStore : TabSwitcherDataStore {

        private val _isAnimationTileDismissedFlow = MutableStateFlow(false)
        private val _hasAnimationTileBeenSeenFlow = MutableStateFlow(false)

        override val data: Flow<TabSwitcherData>
            get() = flowOf() // No-op

        override suspend fun setUserState(userState: UserState) {
            // No-op
        }

        override suspend fun setTabLayoutType(layoutType: LayoutType) {
            // No-op
        }

        override fun isAnimationTileDismissed(): Flow<Boolean> = _isAnimationTileDismissedFlow

        override suspend fun setIsAnimationTileDismissed(isDismissed: Boolean) {
            _isAnimationTileDismissedFlow.value = isDismissed
        }

        override fun hasAnimationTileBeenSeen(): Flow<Boolean> = _hasAnimationTileBeenSeenFlow

        override suspend fun setAnimationTileSeen() {
            _hasAnimationTileBeenSeenFlow.value = true
        }
    }
}
