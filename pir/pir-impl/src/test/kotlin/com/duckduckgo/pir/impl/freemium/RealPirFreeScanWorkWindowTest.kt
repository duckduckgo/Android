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

package com.duckduckgo.pir.impl.freemium

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class RealPirFreeScanWorkWindowTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockPirFreeScanBrokerFilter: PirFreeScanBrokerFilter = mock()
    private val mockPirSchedulingRepository: PirSchedulingRepository = mock()
    private val mockPirFreemiumDataStore: PirFreemiumDataStore = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()

    private lateinit var testee: RealPirFreeScanWorkWindow

    @Before
    fun setUp() {
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(NOW)
        whenever(mockPirFreemiumDataStore.firstProfileSavedTimestamp).thenReturn(NOW)

        testee = RealPirFreeScanWorkWindow(
            pirFreeScanBrokerFilter = mockPirFreeScanBrokerFilter,
            pirSchedulingRepository = mockPirSchedulingRepository,
            pirFreemiumDataStore = mockPirFreemiumDataStore,
            currentTimeProvider = mockCurrentTimeProvider,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenAScannableBrokerHasNoTerminalResultThenWindowIsOpen() = runTest {
        whenever(mockPirFreeScanBrokerFilter.freeScannableBrokerNames()).thenReturn(setOf("A", "B"))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(
            listOf(
                record("A", ScanJobStatus.MATCHES_FOUND),
                record("B", ScanJobStatus.NOT_EXECUTED),
            ),
        )

        assertTrue(testee.isOpen())
    }

    @Test
    fun whenEveryScannableBrokerHasATerminalResultThenWindowIsClosed() = runTest {
        whenever(mockPirFreeScanBrokerFilter.freeScannableBrokerNames()).thenReturn(setOf("A", "B"))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(
            listOf(
                record("A", ScanJobStatus.MATCHES_FOUND),
                record("B", ScanJobStatus.NO_MATCH_FOUND),
            ),
        )

        assertFalse(testee.isOpen())
    }

    @Test
    fun whenAnErroredBrokerRemainsThenWindowIsOpenSoTheRetryStillHasAWorker() = runTest {
        whenever(mockPirFreeScanBrokerFilter.freeScannableBrokerNames()).thenReturn(setOf("A"))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(listOf(record("A", ScanJobStatus.ERROR)))

        assertTrue(testee.isOpen())
    }

    @Test
    fun whenNoScanJobRecordsExistYetThenWindowIsOpen() = runTest {
        whenever(mockPirFreeScanBrokerFilter.freeScannableBrokerNames()).thenReturn(setOf("A"))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(emptyList())

        assertTrue(testee.isOpen())
    }

    @Test
    fun whenNoBrokerIsFreeScannableThenWindowIsClosed() = runTest {
        whenever(mockPirFreeScanBrokerFilter.freeScannableBrokerNames()).thenReturn(emptySet())

        assertFalse(testee.isOpen())
    }

    @Test
    fun whenGatedBrokerRecordsAreOutstandingThenTheyDoNotHoldTheWindowOpen() = runTest {
        whenever(mockPirFreeScanBrokerFilter.freeScannableBrokerNames()).thenReturn(setOf("A"))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(
            listOf(
                record("A", ScanJobStatus.NO_MATCH_FOUND),
                record("Gated", ScanJobStatus.NOT_EXECUTED),
            ),
        )

        assertFalse(testee.isOpen())
    }

    @Test
    fun whenDeprecatedRecordsAreOutstandingThenTheyDoNotHoldTheWindowOpen() = runTest {
        whenever(mockPirFreeScanBrokerFilter.freeScannableBrokerNames()).thenReturn(setOf("A"))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(
            listOf(
                record("A", ScanJobStatus.NO_MATCH_FOUND),
                record("A", ScanJobStatus.NOT_EXECUTED).copy(userProfileId = 2L, deprecated = true),
            ),
        )

        assertFalse(testee.isOpen())
    }

    @Test
    fun whenSevenDaysHaveElapsedSinceFirstProfileSaveThenWindowIsClosed() = runTest {
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(NOW + TimeUnit.DAYS.toMillis(7))
        whenever(mockPirFreeScanBrokerFilter.freeScannableBrokerNames()).thenReturn(setOf("A"))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(listOf(record("A", ScanJobStatus.NOT_EXECUTED)))

        assertFalse(testee.isOpen())
    }

    @Test
    fun whenJustUnderSevenDaysHaveElapsedThenWindowIsStillOpen() = runTest {
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(NOW + TimeUnit.DAYS.toMillis(7) - 1)
        whenever(mockPirFreeScanBrokerFilter.freeScannableBrokerNames()).thenReturn(setOf("A"))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(listOf(record("A", ScanJobStatus.NOT_EXECUTED)))

        assertTrue(testee.isOpen())
    }

    @Test
    fun whenNoActivationTimestampIsRecordedThenTheTimeBoundDoesNotCloseTheWindow() = runTest {
        whenever(mockPirFreemiumDataStore.firstProfileSavedTimestamp).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(NOW + TimeUnit.DAYS.toMillis(365))
        whenever(mockPirFreeScanBrokerFilter.freeScannableBrokerNames()).thenReturn(setOf("A"))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(listOf(record("A", ScanJobStatus.NOT_EXECUTED)))

        assertTrue(testee.isOpen())
    }

    private fun record(
        brokerName: String,
        status: ScanJobStatus,
    ) = ScanJobRecord(brokerName = brokerName, userProfileId = 1L, status = status)

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
