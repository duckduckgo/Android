/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.internal.store

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.internal.models.scheduling.OptOutJobRecord
import com.duckduckgo.pir.internal.models.scheduling.OptOutJobStatus
import com.duckduckgo.pir.internal.models.scheduling.ScanJobRecord
import com.duckduckgo.pir.internal.models.scheduling.ScanJobStatus
import com.duckduckgo.pir.internal.store.db.JobSchedulingDao
import com.duckduckgo.pir.internal.store.db.OptOutJobRecordEntity
import com.duckduckgo.pir.internal.store.db.ScanJobRecordEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealPirSchedulingRepositoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealPirSchedulingRepository

    private val mockJobSchedulingDao: JobSchedulingDao = mock()

    @Before
    fun setUp() {
        testee = RealPirSchedulingRepository(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            jobSchedulingDao = mockJobSchedulingDao,
        )
    }

    // Test data
    private val validScanJobEntity = ScanJobRecordEntity(
        brokerName = "test-broker",
        userProfileId = 123L,
        status = ScanJobStatus.NOT_EXECUTED.name,
        lastScanDateInMillis = 1000L,
    )

    private val invalidScanJobEntity = ScanJobRecordEntity(
        brokerName = "invalid-broker",
        userProfileId = 456L,
        status = ScanJobStatus.INVALID.name,
        lastScanDateInMillis = 2000L,
    )

    private val validOptOutJobEntity = OptOutJobRecordEntity(
        extractedProfileId = 789L,
        brokerName = "test-broker",
        userProfileId = 123L,
        status = OptOutJobStatus.NOT_EXECUTED.name,
        attemptCount = 0,
        lastOptOutAttemptDate = 1000L,
        optOutRequestedDate = 2000L,
        optOutRemovedDate = 0L,
    )

    private val invalidOptOutJobEntity = OptOutJobRecordEntity(
        extractedProfileId = 999L,
        brokerName = "invalid-broker",
        userProfileId = 456L,
        status = OptOutJobStatus.INVALID.name,
        attemptCount = 1,
        lastOptOutAttemptDate = 3000L,
        optOutRequestedDate = 4000L,
        optOutRemovedDate = 0L,
    )

    private val scanJobRecord = ScanJobRecord(
        brokerName = "test-broker",
        userProfileId = 123L,
        status = ScanJobStatus.NOT_EXECUTED,
        lastScanDateInMillis = 1000L,
    )

    private val optOutJobRecord = OptOutJobRecord(
        extractedProfileId = 789L,
        brokerName = "test-broker",
        userProfileId = 123L,
        status = OptOutJobStatus.NOT_EXECUTED,
        attemptCount = 0,
        lastOptOutAttemptDate = 1000L,
        optOutRequestedDate = 2000L,
        optOutRemovedDate = 0L,
    )

    // ScanJobRecord tests
    @Test
    fun whenGetAllValidScanJobRecordsThenReturnOnlyValidRecords() = runTest {
        whenever(mockJobSchedulingDao.getAllScanJobRecords()).thenReturn(
            listOf(validScanJobEntity, invalidScanJobEntity),
        )

        val result = testee.getAllValidScanJobRecords()

        assertEquals(1, result.size)
        assertEquals("test-broker", result[0].brokerName)
        assertEquals(123L, result[0].userProfileId)
        assertEquals(ScanJobStatus.NOT_EXECUTED, result[0].status)
        assertEquals(1000L, result[0].lastScanDateInMillis)
    }

    @Test
    fun whenGetAllValidScanJobRecordsAndAllAreInvalidThenReturnEmptyList() = runTest {
        whenever(mockJobSchedulingDao.getAllScanJobRecords()).thenReturn(
            listOf(invalidScanJobEntity),
        )

        val result = testee.getAllValidScanJobRecords()

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllValidScanJobRecordsAndNoneExistThenReturnEmptyList() = runTest {
        whenever(mockJobSchedulingDao.getAllScanJobRecords()).thenReturn(emptyList())

        val result = testee.getAllValidScanJobRecords()

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenSaveScanJobRecordThenConvertAndSaveToDao() = runTest {
        testee.saveScanJobRecord(scanJobRecord)

        verify(mockJobSchedulingDao).saveScanJobRecord(
            ScanJobRecordEntity(
                brokerName = "test-broker",
                userProfileId = 123L,
                status = "NOT_EXECUTED",
                lastScanDateInMillis = 1000L,
            ),
        )
    }

    @Test
    fun whenSaveScanJobRecordsThenConvertAllAndSaveToDao() = runTest {
        val scanJobRecord2 = ScanJobRecord(
            brokerName = "another-broker",
            userProfileId = 456L,
            status = ScanJobStatus.MATCHES_FOUND,
            lastScanDateInMillis = 5000L,
        )
        val scanJobRecords = listOf(scanJobRecord, scanJobRecord2)

        testee.saveScanJobRecords(scanJobRecords)

        verify(mockJobSchedulingDao).saveScanJobRecords(
            listOf(
                ScanJobRecordEntity(
                    brokerName = "test-broker",
                    userProfileId = 123L,
                    status = "NOT_EXECUTED",
                    lastScanDateInMillis = 1000L,
                ),
                ScanJobRecordEntity(
                    brokerName = "another-broker",
                    userProfileId = 456L,
                    status = "MATCHES_FOUND",
                    lastScanDateInMillis = 5000L,
                ),
            ),
        )
    }

    @Test
    fun whenDeleteAllScanJobRecordsThenCallDao() = runTest {
        testee.deleteAllScanJobRecords()

        verify(mockJobSchedulingDao).deleteAllScanJobRecords()
    }

    @Test
    fun whenGetAllValidOptOutJobRecordsThenReturnOnlyValidRecords() = runTest {
        whenever(mockJobSchedulingDao.getAllOptOutJobRecords()).thenReturn(
            listOf(validOptOutJobEntity, invalidOptOutJobEntity),
        )

        val result = testee.getAllValidOptOutJobRecords()

        assertEquals(1, result.size)
        assertEquals(789L, result[0].extractedProfileId)
        assertEquals("test-broker", result[0].brokerName)
        assertEquals(123L, result[0].userProfileId)
        assertEquals(OptOutJobStatus.NOT_EXECUTED, result[0].status)
        assertEquals(0, result[0].attemptCount)
        assertEquals(1000L, result[0].lastOptOutAttemptDate)
        assertEquals(2000L, result[0].optOutRequestedDate)
        assertEquals(0L, result[0].optOutRemovedDate)
    }

    @Test
    fun whenGetAllValidOptOutJobRecordsAndAllAreInvalidThenReturnEmptyList() = runTest {
        whenever(mockJobSchedulingDao.getAllOptOutJobRecords()).thenReturn(
            listOf(invalidOptOutJobEntity),
        )

        val result = testee.getAllValidOptOutJobRecords()

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllValidOptOutJobRecordsAndNoneExistThenReturnEmptyList() = runTest {
        whenever(mockJobSchedulingDao.getAllOptOutJobRecords()).thenReturn(emptyList())

        val result = testee.getAllValidOptOutJobRecords()

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenSaveOptOutJobRecordThenConvertAndSaveToDao() = runTest {
        testee.saveOptOutJobRecord(optOutJobRecord)

        verify(mockJobSchedulingDao).saveOptOutJobRecord(
            OptOutJobRecordEntity(
                extractedProfileId = 789L,
                brokerName = "test-broker",
                userProfileId = 123L,
                status = "NOT_EXECUTED",
                attemptCount = 0,
                lastOptOutAttemptDate = 1000L,
                optOutRequestedDate = 2000L,
                optOutRemovedDate = 0L,
            ),
        )
    }

    @Test
    fun whenSaveOptOutJobRecordsThenConvertAllAndSaveToDao() = runTest {
        val optOutJobRecord2 = OptOutJobRecord(
            extractedProfileId = 999L,
            brokerName = "another-broker",
            userProfileId = 456L,
            status = OptOutJobStatus.REQUESTED,
            attemptCount = 2,
            lastOptOutAttemptDate = 3000L,
            optOutRequestedDate = 4000L,
            optOutRemovedDate = 5000L,
        )
        val optOutJobRecords = listOf(optOutJobRecord, optOutJobRecord2)

        testee.saveOptOutJobRecords(optOutJobRecords)

        verify(mockJobSchedulingDao).saveOptOutJobRecords(
            listOf(
                OptOutJobRecordEntity(
                    extractedProfileId = 789L,
                    brokerName = "test-broker",
                    userProfileId = 123L,
                    status = "NOT_EXECUTED",
                    attemptCount = 0,
                    lastOptOutAttemptDate = 1000L,
                    optOutRequestedDate = 2000L,
                    optOutRemovedDate = 0L,
                ),
                OptOutJobRecordEntity(
                    extractedProfileId = 999L,
                    brokerName = "another-broker",
                    userProfileId = 456L,
                    status = "REQUESTED",
                    attemptCount = 2,
                    lastOptOutAttemptDate = 3000L,
                    optOutRequestedDate = 4000L,
                    optOutRemovedDate = 5000L,
                ),
            ),
        )
    }

    @Test
    fun whenDeleteAllOptOutJobRecordsThenCallDao() = runTest {
        testee.deleteAllOptOutJobRecords()

        verify(mockJobSchedulingDao).deleteAllOptOutJobRecords()
    }

    // Combined delete tests
    @Test
    fun whenDeleteAllJobRecordsThenCallBothDeleteMethods() = runTest {
        testee.deleteAllJobRecords()

        verify(mockJobSchedulingDao).deleteAllScanJobRecords()
        verify(mockJobSchedulingDao).deleteAllOptOutJobRecords()
    }

    // Edge cases
    @Test
    fun whenGetAllValidScanJobRecordsWithMixedStatusesThenFilterCorrectly() = runTest {
        val entities = listOf(
            validScanJobEntity.copy(status = ScanJobStatus.NOT_EXECUTED.name),
            validScanJobEntity.copy(status = ScanJobStatus.NO_MATCH_FOUND.name, brokerName = "broker2"),
            validScanJobEntity.copy(status = ScanJobStatus.MATCHES_FOUND.name, brokerName = "broker3"),
            validScanJobEntity.copy(status = ScanJobStatus.ERROR.name, brokerName = "broker4"),
            validScanJobEntity.copy(status = ScanJobStatus.INVALID.name, brokerName = "invalid-broker"),
        )
        whenever(mockJobSchedulingDao.getAllScanJobRecords()).thenReturn(entities)

        val result = testee.getAllValidScanJobRecords()

        assertEquals(4, result.size)
        assertTrue(result.none { it.status == ScanJobStatus.INVALID })
        assertTrue(result.any { it.status == ScanJobStatus.NOT_EXECUTED })
        assertTrue(result.any { it.status == ScanJobStatus.NO_MATCH_FOUND })
        assertTrue(result.any { it.status == ScanJobStatus.MATCHES_FOUND })
        assertTrue(result.any { it.status == ScanJobStatus.ERROR })
    }

    @Test
    fun whenGetAllValidOptOutJobRecordsWithMixedStatusesThenFilterCorrectly() = runTest {
        val entities = listOf(
            validOptOutJobEntity.copy(status = OptOutJobStatus.NOT_EXECUTED.name),
            validOptOutJobEntity.copy(status = OptOutJobStatus.REQUESTED.name, extractedProfileId = 800L),
            validOptOutJobEntity.copy(status = OptOutJobStatus.REMOVED.name, extractedProfileId = 801L),
            validOptOutJobEntity.copy(status = OptOutJobStatus.ERROR.name, extractedProfileId = 802L),
            validOptOutJobEntity.copy(status = OptOutJobStatus.INVALID.name, extractedProfileId = 999L),
        )
        whenever(mockJobSchedulingDao.getAllOptOutJobRecords()).thenReturn(entities)

        val result = testee.getAllValidOptOutJobRecords()

        assertEquals(4, result.size)
        assertTrue(result.none { it.status == OptOutJobStatus.INVALID })
        assertTrue(result.any { it.status == OptOutJobStatus.NOT_EXECUTED })
        assertTrue(result.any { it.status == OptOutJobStatus.REQUESTED })
        assertTrue(result.any { it.status == OptOutJobStatus.REMOVED })
        assertTrue(result.any { it.status == OptOutJobStatus.ERROR })
    }
}
