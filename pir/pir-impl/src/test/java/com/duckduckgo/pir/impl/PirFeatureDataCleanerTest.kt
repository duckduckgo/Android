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

package com.duckduckgo.pir.impl

import com.duckduckgo.pir.impl.store.PirEventsRepository
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.duckduckgo.pir.impl.wideevents.PirInitialScanCompletionWideEvent
import com.duckduckgo.pir.impl.wideevents.PirScanWideEvent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class PirFeatureDataCleanerTest {

    private val pirRepository: PirRepository = mock()
    private val pirSchedulingRepository: PirSchedulingRepository = mock()
    private val pirEventsRepository: PirEventsRepository = mock()
    private val pirScanWideEvent: PirScanWideEvent = mock()
    private val pirInitialScanCompletionWideEvent: PirInitialScanCompletionWideEvent = mock()
    private val pirFreemiumDataStore: PirFreemiumDataStore = mock()

    private lateinit var testee: RealPirFeatureDataCleaner

    @Before
    fun setUp() {
        whenever(pirFreemiumDataStore.didActivate).thenReturn(false)

        testee = RealPirFeatureDataCleaner(
            pirRepository = pirRepository,
            pirSchedulingRepository = pirSchedulingRepository,
            pirEventsRepository = pirEventsRepository,
            pirScanWideEvent = pirScanWideEvent,
            pirInitialScanCompletionWideEvent = pirInitialScanCompletionWideEvent,
            pirFreemiumDataStore = pirFreemiumDataStore,
        )
    }

    @Test
    fun whenRemoveAllDataThenWideEventResetCalledBeforeRepositoryClear() = runTest {
        testee.removeAllData()

        inOrder(pirScanWideEvent, pirInitialScanCompletionWideEvent, pirRepository).run {
            verify(pirScanWideEvent).onUserReset()
            verify(pirInitialScanCompletionWideEvent).onUserReset()
            verify(pirRepository).clearAllData()
        }
        verify(pirSchedulingRepository).clearAllData()
        verify(pirEventsRepository).clearAllData()
    }

    @Test
    fun whenRemoveUserDataThenWideEventResetCalledBeforeRepositoryClear() = runTest {
        testee.removeUserData()

        inOrder(pirScanWideEvent, pirInitialScanCompletionWideEvent, pirRepository).run {
            verify(pirScanWideEvent).onUserReset()
            verify(pirInitialScanCompletionWideEvent).onUserReset()
            verify(pirRepository).clearUserData()
        }
        verify(pirSchedulingRepository).clearAllData()
        verify(pirEventsRepository).clearAllData()
    }

    @Test
    fun whenUserActivatedFreemiumThenRemoveAllDataIsANoOp() = runTest {
        whenever(pirFreemiumDataStore.didActivate).thenReturn(true)

        testee.removeAllData()

        verifyNoInteractions(pirRepository)
        verifyNoInteractions(pirSchedulingRepository)
        verifyNoInteractions(pirEventsRepository)
        verify(pirScanWideEvent, never()).onUserReset()
    }

    @Test
    fun whenUserNeverActivatedFreemiumThenRemoveAllDataStillWipes() = runTest {
        whenever(pirFreemiumDataStore.didActivate).thenReturn(false)

        testee.removeAllData()

        verify(pirRepository).clearAllData()
        verify(pirSchedulingRepository).clearAllData()
        verify(pirEventsRepository).clearAllData()
    }

    @Test
    fun whenUserDeletesTheirProfileThenFreemiumStateIsReset() = runTest {
        testee.removeUserData()

        verify(pirFreemiumDataStore).reset()
        verify(pirRepository).clearUserData()
    }
}
