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

package com.duckduckgo.app.fire

import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.verification.VerificationMode

class FireModeLastTabObserverTest {

    @get:Rule val coroutineRule = CoroutineTestRule()

    /** Open and pending-undo rows alike, exactly as `select count(*) from tabs` reports them. */
    private val fireTabCount = MutableStateFlow(1)
    private var pendingUndoIds = emptyList<String>()

    private val fireTabsDao: TabsDao = mock {
        on { it.flowTabCount() } doReturn fireTabCount
    }
    private val fireTabRepository: TabRepository = mock()
    private val adClickManager: AdClickManager = mock()
    private val dataClearing: ManualDataClearing = mock()
    private val dataClearingWideEvent: DataClearingWideEvent = mock()
    private val fireModeAvailability: FireModeAvailability = mock {
        on { it.isAvailable() } doReturn true
    }
    private val fireModeDataClearingState = FireModeDataClearingState()

    private val testee = FireModeLastTabObserver(
        fireTabsDao = fireTabsDao,
        fireTabRepository = fireTabRepository,
        adClickManager = adClickManager,
        dataClearing = dataClearing,
        dataClearingWideEvent = dataClearingWideEvent,
        fireModeDataClearingState = fireModeDataClearingState,
        fireModeAvailability = fireModeAvailability,
        appCoroutineScope = coroutineRule.testScope,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenLastFireTabIsRemovedThenFireDataIsBurned() = runTest {
        startObserving()

        fireTabCount.value = 0

        verifyBurned(times(1))
        verify(dataClearingWideEvent).start(
            entryPoint = DataClearingWideEvent.EntryPoint.FIRE_TABS_EMPTIED,
            clearOptions = setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS),
            browserMode = BrowserMode.FIRE,
        )
        verify(dataClearingWideEvent).finishSuccess()
    }

    @Test
    fun whenAClosedFireTabIsStillPendingUndoThenFireDataIsNotBurned() = runTest {
        startObserving()

        // the row is still counted while it is only marked deletable
        fireTabCount.value = 1

        verifyBurned(never())
    }

    @Test
    fun whenFireTabsRemainThenFireDataIsNotBurned() = runTest {
        startObserving()

        fireTabCount.value = 2

        verifyBurned(never())
    }

    @Test
    fun whenOnlySomeFireTabsAreDeletedMidClearThenTheBurnIsNotReArmed() = runTest {
        fireTabCount.value = 2
        startObserving()

        // a clear marks the profile clean, then its plugins empty the tabs in two steps
        fireModeDataClearingState.onDataCleared()
        fireTabCount.value = 1
        fireTabCount.value = 0

        verifyBurned(never())
    }

    @Test
    fun whenFireModeIsAlreadyEmptyAndCleanThenNothingIsBurned() = runTest {
        fireTabCount.value = 0

        startObserving()

        verifyBurned(never())
    }

    @Test
    fun whenDataIsAlreadyOwedThenAnEmptyFireModeIsBurnedAsSoonAsObservingStarts() = runTest {
        fireTabCount.value = 0
        fireModeDataClearingState.markDataForClearing()

        startObserving()

        verifyBurned(times(1))
    }

    @Test
    fun whenAPreviousProcessLeftAFireClosePendingThenItIsCommittedAndBurned() = runTest {
        pendingUndoIds = listOf("fire-1")
        whenever(fireTabRepository.purgeDeletableTabs()).thenAnswer {
            fireTabCount.value = 0
            Unit
        }

        startObserving()

        verify(adClickManager).clearTabId("fire-1")
        verify(fireTabRepository).purgeDeletableTabs()
        verifyBurned(times(1))
    }

    @Test
    fun whenTheAppIsForegroundedWithAFireClosePendingThenItIsCommittedAndBurned() = runTest {
        startObserving()
        // the user closed the last Fire tab, so its row is still counted while it waits on Undo
        pendingUndoIds = listOf("fire-1")
        whenever(fireTabRepository.purgeDeletableTabs()).thenAnswer {
            fireTabCount.value = 0
            Unit
        }

        testee.onStart(mock())

        verify(adClickManager).clearTabId("fire-1")
        verifyBurned(times(1))
    }

    @Test
    fun whenNoFireCloseIsPendingThenStartupPurgesNothing() = runTest {
        fireTabCount.value = 0

        startObserving()

        verify(fireTabRepository, never()).purgeDeletableTabs()
        verifyBurned(never())
    }

    @Test
    fun whenFireIsUsedAgainAfterABurnThenEmptyingBurnsAgain() = runTest {
        startObserving()

        fireTabCount.value = 0
        fireTabCount.value = 1
        fireTabCount.value = 0

        verifyBurned(times(2))
    }

    @Test
    fun whenFireModeIsUnavailableThenNothingIsBurned() = runTest {
        whenever(fireModeAvailability.isAvailable()).thenReturn(false)

        startObserving()
        fireTabCount.value = 0

        verifyBurned(never())
    }

    @Test
    fun whenTheBurnFailsThenItIsNotRetriedInALoop() = runTest {
        whenever(dataClearing.clearDataUsingManualFireOptions(any(), any(), any()))
            .thenThrow(RuntimeException("boom"))
        startObserving()

        fireTabCount.value = 0

        verifyBurned(times(1))
        verify(dataClearingWideEvent).finishFailure(any<Throwable>())
    }

    private suspend fun startObserving() {
        whenever(fireTabRepository.getDeletableTabIds()).thenAnswer { pendingUndoIds }
        testee.onCreate(mock())
    }

    private suspend fun verifyBurned(mode: VerificationMode) {
        verify(dataClearing, mode).clearDataUsingManualFireOptions(
            shouldRestartIfRequired = false,
            browserMode = BrowserMode.FIRE,
        )
    }
}
