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

package com.duckduckgo.pir.impl.store

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus
import com.duckduckgo.pir.impl.store.db.EmailConfirmationJobRecordEntity
import com.duckduckgo.pir.impl.store.db.JobSchedulingDao
import com.duckduckgo.pir.impl.store.db.OptOutJobRecordEntity
import com.duckduckgo.pir.impl.store.db.ScanJobRecordEntity
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
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()

    @Before
    fun setUp() {
        testee =
            RealPirSchedulingRepository(
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                jobSchedulingDao = mockJobSchedulingDao,
                currentTimeProvider = mockCurrentTimeProvider,
            )

        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(9000L)
    }

    // Test data
    private val validScanJobEntity =
        ScanJobRecordEntity(
            brokerName = "test-broker",
            userProfileId = 123L,
            status = ScanJobStatus.NOT_EXECUTED.name,
            lastScanDateInMillis = 1000L,
        )

    private val deprecatedScanJobEntity =
        ScanJobRecordEntity(
            brokerName = "invalid-broker",
            userProfileId = 456L,
            status = ScanJobStatus.MATCHES_FOUND.name,
            deprecated = true,
            lastScanDateInMillis = 2000L,
        )

    private val validOptOutJobEntity =
        OptOutJobRecordEntity(
            extractedProfileId = 789L,
            brokerName = "test-broker",
            userProfileId = 123L,
            status = OptOutJobStatus.NOT_EXECUTED.name,
            attemptCount = 0,
            lastOptOutAttemptDate = 1000L,
            optOutRequestedDate = 2000L,
            optOutRemovedDate = 0L,
        )

    private val deprecatedOptOutJobEntity =
        OptOutJobRecordEntity(
            extractedProfileId = 999L,
            brokerName = "invalid-broker",
            userProfileId = 456L,
            deprecated = true,
            status = OptOutJobStatus.REMOVED.name,
            attemptCount = 1,
            lastOptOutAttemptDate = 3000L,
            optOutRequestedDate = 4000L,
            optOutRemovedDate = 0L,
        )

    private val scanJobRecord =
        ScanJobRecord(
            brokerName = "test-broker",
            userProfileId = 123L,
            status = ScanJobStatus.NOT_EXECUTED,
            lastScanDateInMillis = 1000L,
        )

    private val optOutJobRecord =
        OptOutJobRecord(
            extractedProfileId = 789L,
            brokerName = "test-broker",
            userProfileId = 123L,
            status = OptOutJobStatus.NOT_EXECUTED,
            attemptCount = 0,
            lastOptOutAttemptDateInMillis = 1000L,
            optOutRequestedDateInMillis = 2000L,
            optOutRemovedDateInMillis = 0L,
        )

    // Email confirmation test data
    private val emailConfirmationJobEntity =
        EmailConfirmationJobRecordEntity(
            extractedProfileId = 1001L,
            brokerName = "email-broker",
            userProfileId = 123L,
            email = "test@example.com",
            attemptId = "attempt-123",
            dateCreatedInMillis = 5000L,
            emailConfirmationLink = "",
            linkFetchAttemptCount = 0,
            lastLinkFetchDateInMillis = 0L,
            jobAttemptCount = 0,
            lastJobAttemptDateInMillis = 0L,
            deprecated = false,
        )

    private val emailConfirmationJobEntityWithLink =
        EmailConfirmationJobRecordEntity(
            extractedProfileId = 1002L,
            brokerName = "email-broker-2",
            userProfileId = 456L,
            email = "test2@example.com",
            attemptId = "attempt-456",
            dateCreatedInMillis = 6000L,
            emailConfirmationLink = "https://example.com/confirm?token=abc123",
            linkFetchAttemptCount = 1,
            lastLinkFetchDateInMillis = 7000L,
            jobAttemptCount = 0,
            lastJobAttemptDateInMillis = 0L,
            deprecated = false,
        )

    private val emailConfirmationJobRecord =
        EmailConfirmationJobRecord(
            extractedProfileId = 1001L,
            brokerName = "email-broker",
            userProfileId = 123L,
            emailData =
            EmailConfirmationJobRecord.EmailData(
                email = "test@example.com",
                attemptId = "attempt-123",
            ),
            linkFetchData =
            EmailConfirmationJobRecord.LinkFetchData(
                emailConfirmationLink = "",
                linkFetchAttemptCount = 0,
                lastLinkFetchDateInMillis = 0L,
            ),
            jobAttemptData =
            EmailConfirmationJobRecord.JobAttemptData(
                jobAttemptCount = 0,
                lastJobAttemptDateInMillis = 0L,
            ),
            dateCreatedInMillis = 5000L,
            deprecated = false,
        )

    // ScanJobRecord tests
    @Test
    fun whenGetAllValidScanJobRecordsThenReturnOnlyValidRecords() =
        runTest {
            whenever(mockJobSchedulingDao.getAllScanJobRecords()).thenReturn(
                listOf(validScanJobEntity, deprecatedScanJobEntity),
            )

            val result = testee.getAllValidScanJobRecords()

            assertEquals(1, result.size)
            assertEquals("test-broker", result[0].brokerName)
            assertEquals(123L, result[0].userProfileId)
            assertEquals(ScanJobStatus.NOT_EXECUTED, result[0].status)
            assertEquals(1000L, result[0].lastScanDateInMillis)
        }

    @Test
    fun whenGetAllValidScanJobRecordsAndAllAreInvalidThenReturnEmptyList() =
        runTest {
            whenever(mockJobSchedulingDao.getAllScanJobRecords()).thenReturn(
                listOf(deprecatedScanJobEntity),
            )

            val result = testee.getAllValidScanJobRecords()

            assertTrue(result.isEmpty())
        }

    @Test
    fun whenGetAllValidScanJobRecordsAndNoneExistThenReturnEmptyList() =
        runTest {
            whenever(mockJobSchedulingDao.getAllScanJobRecords()).thenReturn(emptyList())

            val result = testee.getAllValidScanJobRecords()

            assertTrue(result.isEmpty())
        }

    @Test
    fun whenSaveScanJobRecordThenConvertAndSaveToDao() =
        runTest {
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
    fun whenSaveScanJobRecordsThenConvertAllAndSaveToDao() =
        runTest {
            val scanJobRecord2 =
                ScanJobRecord(
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
    fun whenDeleteAllScanJobRecordsThenCallDao() =
        runTest {
            testee.deleteAllScanJobRecords()

            verify(mockJobSchedulingDao).deleteAllScanJobRecords()
        }

    @Test
    fun whenGetValidScanJobRecordAndRecordExistsThenReturnRecord() =
        runTest {
            whenever(mockJobSchedulingDao.getScanJobRecord("test-broker", 123L)).thenReturn(validScanJobEntity)

            val result = testee.getValidScanJobRecord("test-broker", 123L)

            assertEquals("test-broker", result?.brokerName)
            assertEquals(123L, result?.userProfileId)
            assertEquals(ScanJobStatus.NOT_EXECUTED, result?.status)
            assertEquals(1000L, result?.lastScanDateInMillis)
        }

    @Test
    fun whenGetValidScanJobRecordAndRecordIsInvalidThenReturnNull() =
        runTest {
            whenever(mockJobSchedulingDao.getScanJobRecord("invalid-broker", 456L)).thenReturn(deprecatedScanJobEntity)

            val result = testee.getValidScanJobRecord("invalid-broker", 456L)

            assertEquals(null, result)
        }

    @Test
    fun whenGetValidScanJobRecordAndRecordNotFoundThenReturnNull() =
        runTest {
            whenever(mockJobSchedulingDao.getScanJobRecord("nonexistent-broker", 999L)).thenReturn(null)

            val result = testee.getValidScanJobRecord("nonexistent-broker", 999L)

            assertEquals(null, result)
        }

    @Test
    fun whenGetAllValidOptOutJobRecordsThenReturnOnlyValidRecords() =
        runTest {
            whenever(mockJobSchedulingDao.getAllOptOutJobRecords()).thenReturn(
                listOf(validOptOutJobEntity, deprecatedOptOutJobEntity),
            )

            val result = testee.getAllValidOptOutJobRecords()

            assertEquals(1, result.size)
            assertEquals(789L, result[0].extractedProfileId)
            assertEquals("test-broker", result[0].brokerName)
            assertEquals(123L, result[0].userProfileId)
            assertEquals(OptOutJobStatus.NOT_EXECUTED, result[0].status)
            assertEquals(0, result[0].attemptCount)
            assertEquals(1000L, result[0].lastOptOutAttemptDateInMillis)
            assertEquals(2000L, result[0].optOutRequestedDateInMillis)
            assertEquals(0L, result[0].optOutRemovedDateInMillis)
        }

    @Test
    fun whenGetAllValidOptOutJobRecordsAndAllAreInvalidThenReturnEmptyList() =
        runTest {
            whenever(mockJobSchedulingDao.getAllOptOutJobRecords()).thenReturn(
                listOf(deprecatedOptOutJobEntity),
            )

            val result = testee.getAllValidOptOutJobRecords()

            assertTrue(result.isEmpty())
        }

    @Test
    fun whenGetAllValidOptOutJobRecordsAndNoneExistThenReturnEmptyList() =
        runTest {
            whenever(mockJobSchedulingDao.getAllOptOutJobRecords()).thenReturn(emptyList())

            val result = testee.getAllValidOptOutJobRecords()

            assertTrue(result.isEmpty())
        }

    @Test
    fun whenSaveOptOutJobRecordThenConvertAndSaveToDao() =
        runTest {
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
    fun whenSaveOptOutJobRecordsThenConvertAllAndSaveToDao() =
        runTest {
            val optOutJobRecord2 =
                OptOutJobRecord(
                    extractedProfileId = 999L,
                    brokerName = "another-broker",
                    userProfileId = 456L,
                    status = OptOutJobStatus.REQUESTED,
                    attemptCount = 2,
                    lastOptOutAttemptDateInMillis = 3000L,
                    optOutRequestedDateInMillis = 4000L,
                    optOutRemovedDateInMillis = 5000L,
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
    fun whenDeleteAllOptOutJobRecordsThenCallDao() =
        runTest {
            testee.deleteAllOptOutJobRecords()

            verify(mockJobSchedulingDao).deleteAllOptOutJobRecords()
        }

    @Test
    fun whenGetValidOptOutJobRecordAndRecordExistsThenReturnRecord() =
        runTest {
            whenever(mockJobSchedulingDao.getOptOutJobRecord(789L)).thenReturn(validOptOutJobEntity)

            val result = testee.getValidOptOutJobRecord(789L)

            assertEquals(789L, result?.extractedProfileId)
            assertEquals("test-broker", result?.brokerName)
            assertEquals(123L, result?.userProfileId)
            assertEquals(OptOutJobStatus.NOT_EXECUTED, result?.status)
            assertEquals(0, result?.attemptCount)
            assertEquals(1000L, result?.lastOptOutAttemptDateInMillis)
            assertEquals(2000L, result?.optOutRequestedDateInMillis)
            assertEquals(0L, result?.optOutRemovedDateInMillis)
        }

    @Test
    fun whenGetValidOptOutJobRecordAndRecordIsInvalidThenReturnNull() =
        runTest {
            whenever(mockJobSchedulingDao.getOptOutJobRecord(999L)).thenReturn(deprecatedOptOutJobEntity)

            val result = testee.getValidOptOutJobRecord(999L)

            assertEquals(null, result)
        }

    @Test
    fun whenGetValidOptOutJobRecordAndRecordNotFoundThenReturnNull() =
        runTest {
            whenever(mockJobSchedulingDao.getOptOutJobRecord(8888L)).thenReturn(null)

            val result = testee.getValidOptOutJobRecord(8888L)

            assertEquals(null, result)
        }

    @Test
    fun whenUpdateScanJobRecordStatusThenCallsDaoWithCorrectParameters() =
        runTest {
            val newStatus = ScanJobStatus.MATCHES_FOUND
            val newLastScanDateMillis = 9999L
            val brokerName = "update-broker"
            val profileQueryId = 888L

            testee.updateScanJobRecordStatus(
                newStatus = newStatus,
                newLastScanDateMillis = newLastScanDateMillis,
                brokerName = brokerName,
                profileQueryId = profileQueryId,
                deprecated = false,
            )

            verify(mockJobSchedulingDao).updateScanJobRecordStatus(
                brokerName,
                profileQueryId,
                newStatus.name,
                newLastScanDateMillis,
                deprecated = false,
            )
        }

    @Test
    fun whenUpdateScanJobRecordStatusWithErrorStatusThenCallsDaoWithErrorStatus() =
        runTest {
            val newStatus = ScanJobStatus.ERROR
            val newLastScanDateMillis = 7777L
            val brokerName = "error-broker"
            val profileQueryId = 555L

            testee.updateScanJobRecordStatus(
                newStatus = newStatus,
                newLastScanDateMillis = newLastScanDateMillis,
                brokerName = brokerName,
                profileQueryId = profileQueryId,
                deprecated = false,
            )

            verify(mockJobSchedulingDao).updateScanJobRecordStatus(
                brokerName,
                profileQueryId,
                "ERROR",
                newLastScanDateMillis,
                false,
            )
        }

    @Test
    fun whenUpdateScanJobRecordStatusWithNoMatchFoundStatusThenCallsDaoWithNoMatchStatus() =
        runTest {
            val newStatus = ScanJobStatus.NO_MATCH_FOUND
            val newLastScanDateMillis = 6666L
            val brokerName = "no-match-broker"
            val profileQueryId = 333L

            testee.updateScanJobRecordStatus(
                newStatus = newStatus,
                newLastScanDateMillis = newLastScanDateMillis,
                brokerName = brokerName,
                profileQueryId = profileQueryId,
                deprecated = false,
            )

            verify(mockJobSchedulingDao).updateScanJobRecordStatus(
                brokerName,
                profileQueryId,
                "NO_MATCH_FOUND",
                newLastScanDateMillis,
                false,
            )
        }

    // Combined delete tests
    @Test
    fun whenDeleteAllJobRecordsThenCallBothDeleteMethods() =
        runTest {
            testee.deleteAllJobRecords()

            verify(mockJobSchedulingDao).deleteAllScanJobRecords()
            verify(mockJobSchedulingDao).deleteAllOptOutJobRecords()
        }

    // Edge cases
    @Test
    fun whenGetAllValidScanJobRecordsWithMixedStatusesThenFilterCorrectly() =
        runTest {
            val entities =
                listOf(
                    validScanJobEntity.copy(status = ScanJobStatus.NOT_EXECUTED.name),
                    validScanJobEntity.copy(status = ScanJobStatus.NO_MATCH_FOUND.name, brokerName = "broker2"),
                    validScanJobEntity.copy(status = ScanJobStatus.MATCHES_FOUND.name, brokerName = "broker3"),
                    validScanJobEntity.copy(status = ScanJobStatus.ERROR.name, brokerName = "broker4"),
                    validScanJobEntity.copy(status = ScanJobStatus.MATCHES_FOUND.name, brokerName = "invalid-broker", deprecated = true),
                )
            whenever(mockJobSchedulingDao.getAllScanJobRecords()).thenReturn(entities)

            val result = testee.getAllValidScanJobRecords()

            assertEquals(4, result.size)
            assertTrue(result.none { it.deprecated })
            assertTrue(result.any { it.status == ScanJobStatus.NOT_EXECUTED })
            assertTrue(result.any { it.status == ScanJobStatus.NO_MATCH_FOUND })
            assertTrue(result.any { it.status == ScanJobStatus.MATCHES_FOUND })
            assertTrue(result.any { it.status == ScanJobStatus.ERROR })
        }

    @Test
    fun whenGetAllValidOptOutJobRecordsWithMixedStatusesThenFilterCorrectly() =
        runTest {
            val entities =
                listOf(
                    validOptOutJobEntity.copy(status = OptOutJobStatus.NOT_EXECUTED.name),
                    validOptOutJobEntity.copy(status = OptOutJobStatus.REQUESTED.name, extractedProfileId = 800L),
                    validOptOutJobEntity.copy(status = OptOutJobStatus.REMOVED.name, extractedProfileId = 801L),
                    validOptOutJobEntity.copy(status = OptOutJobStatus.ERROR.name, extractedProfileId = 802L),
                    validOptOutJobEntity.copy(status = OptOutJobStatus.REMOVED.name, extractedProfileId = 999L, deprecated = true),
                )
            whenever(mockJobSchedulingDao.getAllOptOutJobRecords()).thenReturn(entities)

            val result = testee.getAllValidOptOutJobRecords()

            assertEquals(4, result.size)
            assertTrue(result.none { it.deprecated })
            assertTrue(result.any { it.status == OptOutJobStatus.NOT_EXECUTED })
            assertTrue(result.any { it.status == OptOutJobStatus.REQUESTED })
            assertTrue(result.any { it.status == OptOutJobStatus.REMOVED })
            assertTrue(result.any { it.status == OptOutJobStatus.ERROR })
        }

    // Email confirmation job tests
    @Test
    fun whenSaveEmailConfirmationJobRecordThenConvertAndSaveToDao() =
        runTest {
            testee.saveEmailConfirmationJobRecord(emailConfirmationJobRecord.copy(dateCreatedInMillis = 0L))

            verify(mockJobSchedulingDao).saveEmailConfirmationJobRecord(
                EmailConfirmationJobRecordEntity(
                    extractedProfileId = 1001L,
                    brokerName = "email-broker",
                    userProfileId = 123L,
                    email = "test@example.com",
                    attemptId = "attempt-123",
                    dateCreatedInMillis = 9000L,
                    emailConfirmationLink = "",
                    linkFetchAttemptCount = 0,
                    lastLinkFetchDateInMillis = 0L,
                    jobAttemptCount = 0,
                    lastJobAttemptDateInMillis = 0L,
                    deprecated = false,
                ),
            )
        }

    @Test
    fun whenSaveEmailConfirmationJobRecordWithLinkDataThenConvertAndSaveToDao() =
        runTest {
            val emailJobRecordWithLink =
                emailConfirmationJobRecord.copy(
                    linkFetchData =
                    EmailConfirmationJobRecord.LinkFetchData(
                        emailConfirmationLink = "https://example.com/confirm?token=xyz",
                        linkFetchAttemptCount = 2,
                        lastLinkFetchDateInMillis = 9000L,
                    ),
                    jobAttemptData =
                    EmailConfirmationJobRecord.JobAttemptData(
                        jobAttemptCount = 1,
                        lastJobAttemptDateInMillis = 10000L,
                    ),
                )

            testee.saveEmailConfirmationJobRecord(emailJobRecordWithLink)

            verify(mockJobSchedulingDao).saveEmailConfirmationJobRecord(
                EmailConfirmationJobRecordEntity(
                    extractedProfileId = 1001L,
                    brokerName = "email-broker",
                    userProfileId = 123L,
                    email = "test@example.com",
                    attemptId = "attempt-123",
                    dateCreatedInMillis = 5000L,
                    emailConfirmationLink = "https://example.com/confirm?token=xyz",
                    linkFetchAttemptCount = 2,
                    lastLinkFetchDateInMillis = 9000L,
                    jobAttemptCount = 1,
                    lastJobAttemptDateInMillis = 10000L,
                    deprecated = false,
                ),
            )
        }

    @Test
    fun whenGetEmailConfirmationJobsWithNoLinkThenReturnOnlyJobsWithoutLink() =
        runTest {
            whenever(mockJobSchedulingDao.getAllActiveEmailConfirmationJobRecordsWithNoLink()).thenReturn(
                listOf(emailConfirmationJobEntity),
            )

            val result = testee.getEmailConfirmationJobsWithNoLink()

            assertEquals(1, result.size)
            assertEquals(1001L, result[0].extractedProfileId)
            assertEquals("email-broker", result[0].brokerName)
            assertEquals(123L, result[0].userProfileId)
            assertEquals("test@example.com", result[0].emailData.email)
            assertEquals("attempt-123", result[0].emailData.attemptId)
            assertEquals("", result[0].linkFetchData.emailConfirmationLink)
            assertEquals(0, result[0].linkFetchData.linkFetchAttemptCount)
            assertEquals(0L, result[0].linkFetchData.lastLinkFetchDateInMillis)
            assertEquals(0, result[0].jobAttemptData.jobAttemptCount)
            assertEquals(0L, result[0].jobAttemptData.lastJobAttemptDateInMillis)
            assertEquals(5000L, result[0].dateCreatedInMillis)
            assertEquals(false, result[0].deprecated)
        }

    @Test
    fun whenGetEmailConfirmationJobsWithNoLinkAndNoneExistThenReturnEmptyList() =
        runTest {
            whenever(mockJobSchedulingDao.getAllActiveEmailConfirmationJobRecordsWithNoLink()).thenReturn(emptyList())

            val result = testee.getEmailConfirmationJobsWithNoLink()

            assertTrue(result.isEmpty())
        }

    @Test
    fun whenGetEmailConfirmationJobsWithLinkThenReturnOnlyJobsWithLink() =
        runTest {
            whenever(mockJobSchedulingDao.getAllActiveEmailConfirmationJobRecordsWithLink()).thenReturn(
                listOf(emailConfirmationJobEntityWithLink),
            )

            val result = testee.getEmailConfirmationJobsWithLink()

            assertEquals(1, result.size)
            assertEquals(1002L, result[0].extractedProfileId)
            assertEquals("email-broker-2", result[0].brokerName)
            assertEquals(456L, result[0].userProfileId)
            assertEquals("test2@example.com", result[0].emailData.email)
            assertEquals("attempt-456", result[0].emailData.attemptId)
            assertEquals("https://example.com/confirm?token=abc123", result[0].linkFetchData.emailConfirmationLink)
            assertEquals(1, result[0].linkFetchData.linkFetchAttemptCount)
            assertEquals(7000L, result[0].linkFetchData.lastLinkFetchDateInMillis)
            assertEquals(0, result[0].jobAttemptData.jobAttemptCount)
            assertEquals(0L, result[0].jobAttemptData.lastJobAttemptDateInMillis)
            assertEquals(6000L, result[0].dateCreatedInMillis)
            assertEquals(false, result[0].deprecated)
        }

    @Test
    fun whenGetEmailConfirmationJobsWithLinkAndNoneExistThenReturnEmptyList() =
        runTest {
            whenever(mockJobSchedulingDao.getAllActiveEmailConfirmationJobRecordsWithLink()).thenReturn(emptyList())

            val result = testee.getEmailConfirmationJobsWithLink()

            assertTrue(result.isEmpty())
        }

    @Test
    fun whenGetEmailConfirmationJobsWithLinkAndMultipleExistThenReturnAll() =
        runTest {
            val anotherJobWithLink =
                emailConfirmationJobEntityWithLink.copy(
                    extractedProfileId = 1004L,
                    brokerName = "another-email-broker",
                    userProfileId = 789L,
                    email = "another@example.com",
                    attemptId = "attempt-789",
                    emailConfirmationLink = "https://example.com/confirm?token=def456",
                    linkFetchAttemptCount = 3,
                    lastLinkFetchDateInMillis = 8000L,
                    jobAttemptCount = 2,
                    lastJobAttemptDateInMillis = 9000L,
                )

            whenever(mockJobSchedulingDao.getAllActiveEmailConfirmationJobRecordsWithLink()).thenReturn(
                listOf(emailConfirmationJobEntityWithLink, anotherJobWithLink),
            )

            val result = testee.getEmailConfirmationJobsWithLink()

            assertEquals(2, result.size)
            assertTrue(result.any { it.extractedProfileId == 1002L })
            assertTrue(result.any { it.extractedProfileId == 1004L })
            assertTrue(result.all { it.linkFetchData.emailConfirmationLink.isNotEmpty() })
        }

    @Test
    fun whenGetEmailConfirmationJobAndJobExistsThenReturnJob() =
        runTest {
            whenever(mockJobSchedulingDao.getEmailConfirmationJobRecord(1001L)).thenReturn(emailConfirmationJobEntity)

            val result = testee.getEmailConfirmationJob(1001L)

            assertEquals(1001L, result?.extractedProfileId)
            assertEquals("email-broker", result?.brokerName)
            assertEquals(123L, result?.userProfileId)
            assertEquals("test@example.com", result?.emailData?.email)
            assertEquals("attempt-123", result?.emailData?.attemptId)
            assertEquals("", result?.linkFetchData?.emailConfirmationLink)
            assertEquals(0, result?.linkFetchData?.linkFetchAttemptCount)
            assertEquals(0L, result?.linkFetchData?.lastLinkFetchDateInMillis)
            assertEquals(0, result?.jobAttemptData?.jobAttemptCount)
            assertEquals(0L, result?.jobAttemptData?.lastJobAttemptDateInMillis)
            assertEquals(5000L, result?.dateCreatedInMillis)
            assertEquals(false, result?.deprecated)
        }

    @Test
    fun whenGetEmailConfirmationJobAndJobNotFoundThenReturnNull() =
        runTest {
            whenever(mockJobSchedulingDao.getEmailConfirmationJobRecord(9999L)).thenReturn(null)

            val result = testee.getEmailConfirmationJob(9999L)

            assertEquals(null, result)
        }

    @Test
    fun whenGetEmailConfirmationJobWithComplexDataThenReturnCorrectJob() =
        runTest {
            whenever(mockJobSchedulingDao.getEmailConfirmationJobRecord(1002L)).thenReturn(emailConfirmationJobEntityWithLink)

            val result = testee.getEmailConfirmationJob(1002L)

            assertEquals(1002L, result?.extractedProfileId)
            assertEquals("email-broker-2", result?.brokerName)
            assertEquals(456L, result?.userProfileId)
            assertEquals("test2@example.com", result?.emailData?.email)
            assertEquals("attempt-456", result?.emailData?.attemptId)
            assertEquals("https://example.com/confirm?token=abc123", result?.linkFetchData?.emailConfirmationLink)
            assertEquals(1, result?.linkFetchData?.linkFetchAttemptCount)
            assertEquals(7000L, result?.linkFetchData?.lastLinkFetchDateInMillis)
            assertEquals(0, result?.jobAttemptData?.jobAttemptCount)
            assertEquals(0L, result?.jobAttemptData?.lastJobAttemptDateInMillis)
            assertEquals(6000L, result?.dateCreatedInMillis)
            assertEquals(false, result?.deprecated)
        }

    @Test
    fun whenDeleteEmailConfirmationJobRecordThenCallDao() =
        runTest {
            val extractedProfileId = 1001L

            testee.deleteEmailConfirmationJobRecord(extractedProfileId)

            verify(mockJobSchedulingDao).deleteEmailConfirmationJobRecord(extractedProfileId)
        }

    @Test
    fun whenDeleteAllEmailConfirmationJobRecordsThenCallDao() =
        runTest {
            testee.deleteAllEmailConfirmationJobRecords()

            verify(mockJobSchedulingDao).deleteAllEmailConfirmationJobRecords()
        }

    // Entity to model conversion tests
    @Test
    fun whenConvertEmailConfirmationJobEntityToRecordThenMapCorrectly() =
        runTest {
            whenever(mockJobSchedulingDao.getEmailConfirmationJobRecord(1001L)).thenReturn(emailConfirmationJobEntity)

            val result = testee.getEmailConfirmationJob(1001L)

            // Verify all fields are correctly mapped
            assertEquals(emailConfirmationJobEntity.extractedProfileId, result?.extractedProfileId)
            assertEquals(emailConfirmationJobEntity.brokerName, result?.brokerName)
            assertEquals(emailConfirmationJobEntity.userProfileId, result?.userProfileId)
            assertEquals(emailConfirmationJobEntity.email, result?.emailData?.email)
            assertEquals(emailConfirmationJobEntity.attemptId, result?.emailData?.attemptId)
            assertEquals(emailConfirmationJobEntity.emailConfirmationLink, result?.linkFetchData?.emailConfirmationLink)
            assertEquals(emailConfirmationJobEntity.linkFetchAttemptCount, result?.linkFetchData?.linkFetchAttemptCount)
            assertEquals(emailConfirmationJobEntity.lastLinkFetchDateInMillis, result?.linkFetchData?.lastLinkFetchDateInMillis)
            assertEquals(emailConfirmationJobEntity.jobAttemptCount, result?.jobAttemptData?.jobAttemptCount)
            assertEquals(emailConfirmationJobEntity.lastJobAttemptDateInMillis, result?.jobAttemptData?.lastJobAttemptDateInMillis)
            assertEquals(emailConfirmationJobEntity.dateCreatedInMillis, result?.dateCreatedInMillis)
            assertEquals(emailConfirmationJobEntity.deprecated, result?.deprecated)
        }

    @Test
    fun whenConvertEmailConfirmationJobRecordToEntityThenMapCorrectly() =
        runTest {
            val emailJobWithComplexData =
                emailConfirmationJobRecord.copy(
                    linkFetchData =
                    EmailConfirmationJobRecord.LinkFetchData(
                        emailConfirmationLink = "https://test.com/confirm",
                        linkFetchAttemptCount = 5,
                        lastLinkFetchDateInMillis = 12000L,
                    ),
                    jobAttemptData =
                    EmailConfirmationJobRecord.JobAttemptData(
                        jobAttemptCount = 3,
                        lastJobAttemptDateInMillis = 13000L,
                    ),
                    deprecated = true,
                )

            testee.saveEmailConfirmationJobRecord(emailJobWithComplexData)

            verify(mockJobSchedulingDao).saveEmailConfirmationJobRecord(
                EmailConfirmationJobRecordEntity(
                    extractedProfileId = emailJobWithComplexData.extractedProfileId,
                    brokerName = emailJobWithComplexData.brokerName,
                    userProfileId = emailJobWithComplexData.userProfileId,
                    email = emailJobWithComplexData.emailData.email,
                    attemptId = emailJobWithComplexData.emailData.attemptId,
                    dateCreatedInMillis = emailJobWithComplexData.dateCreatedInMillis,
                    emailConfirmationLink = emailJobWithComplexData.linkFetchData.emailConfirmationLink,
                    linkFetchAttemptCount = emailJobWithComplexData.linkFetchData.linkFetchAttemptCount,
                    lastLinkFetchDateInMillis = emailJobWithComplexData.linkFetchData.lastLinkFetchDateInMillis,
                    jobAttemptCount = emailJobWithComplexData.jobAttemptData.jobAttemptCount,
                    lastJobAttemptDateInMillis = emailJobWithComplexData.jobAttemptData.lastJobAttemptDateInMillis,
                    deprecated = emailJobWithComplexData.deprecated,
                ),
            )
        }
}
