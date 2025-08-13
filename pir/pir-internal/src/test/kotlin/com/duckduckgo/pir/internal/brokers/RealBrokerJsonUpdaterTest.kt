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

package com.duckduckgo.pir.internal.brokers

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.internal.service.DbpService
import com.duckduckgo.pir.internal.service.DbpService.PirBrokerEtags
import com.duckduckgo.pir.internal.service.DbpService.PirMainConfig
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.PirRepository.BrokerJson
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import retrofit2.Response

class RealBrokerJsonUpdaterTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealBrokerJsonUpdater

    private val mockDbpService: DbpService = mock()
    private val mockPirRepository: PirRepository = mock()
    private val mockBrokerDataDownloader: BrokerDataDownloader = mock()

    @Before
    fun setUp() {
        testee = RealBrokerJsonUpdater(
            dbpService = mockDbpService,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            pirRepository = mockPirRepository,
            brokerDataDownloader = mockBrokerDataDownloader,
        )
    }

    // Test data
    private val testEtag = "test-etag-123"
    private val testNewEtag = "test-etag-456"
    private val testFileName1 = "broker1.json"
    private val testFileName2 = "broker2.json"
    private val testFileName3 = "broker3.json"

    private val testBrokerEtags = PirBrokerEtags(
        current = mapOf(
            testFileName1 to testEtag,
            testFileName2 to testNewEtag,
            testFileName3 to testNewEtag,
        ),
    )

    private val testMainConfig = PirMainConfig(
        etag = testNewEtag,
        jsonEtags = testBrokerEtags,
        activeBrokers = emptyList(), // to be removed
    )

    private val testExistingBrokerJsons = listOf(
        BrokerJson(
            fileName = testFileName2,
            etag = "old-etag",
        ),
        BrokerJson(
            fileName = testFileName1,
            etag = testEtag,
        ),
    )

    private val testNewBrokerJsons = listOf(
        BrokerJson(
            fileName = testFileName1,
            etag = testEtag,
        ),
        BrokerJson(
            fileName = testFileName2,
            etag = testNewEtag,
        ),
        BrokerJson(
            fileName = testFileName3,
            etag = testNewEtag,
        ),
    )

    @Test
    fun whenMainConfigIsSuccessfulAndEtagsChangedAndNewFileThenDownloadTriggered() = runTest {
        // Given
        val successResponse = Response.success(testMainConfig)
        whenever(mockPirRepository.getCurrentMainEtag()).thenReturn("old-etag")
        whenever(mockPirRepository.getStoredBrokersCount()).thenReturn(2)
        whenever(mockDbpService.getMainConfig("old-etag")).thenReturn(successResponse)
        whenever(mockPirRepository.getAllLocalBrokerJsons()).thenReturn(testExistingBrokerJsons)

        // When
        val result = testee.update()

        // Then
        assertTrue(result)
        verify(mockPirRepository).updateMainEtag(testNewEtag)
        verify(mockPirRepository).updateBrokerJsons(testNewBrokerJsons)
        // testFileName2 has updated etag, testFileName3 is new
        verify(mockBrokerDataDownloader).downloadBrokerData(listOf(testFileName2, testFileName3))
    }

    @Test
    fun whenMainConfigIsSuccessfulWithNoConfigStoredThenDownloadAllFiles() = runTest {
        // Given
        val successResponse = Response.success(testMainConfig)
        whenever(mockPirRepository.getCurrentMainEtag()).thenReturn(null)
        whenever(mockPirRepository.getStoredBrokersCount()).thenReturn(0)
        whenever(mockDbpService.getMainConfig(null)).thenReturn(successResponse)
        whenever(mockPirRepository.getAllLocalBrokerJsons()).thenReturn(emptyList())

        // When
        val result = testee.update()

        // Then
        assertTrue(result)
        verify(mockPirRepository).updateMainEtag(testNewEtag)
        verify(mockPirRepository).updateBrokerJsons(testNewBrokerJsons)
        verify(mockBrokerDataDownloader).downloadBrokerData(listOf(testFileName1, testFileName2, testFileName3))
    }

    @Test
    fun whenMainConfigFailsThenReturnsFalse() = runTest {
        // Given
        val errorResponse = Response.error<PirMainConfig>(500, "Internal Server Error".toResponseBody())
        whenever(mockPirRepository.getCurrentMainEtag()).thenReturn("old-etag")
        whenever(mockPirRepository.getStoredBrokersCount()).thenReturn(2)
        whenever(mockDbpService.getMainConfig("old-etag")).thenReturn(errorResponse)

        // When
        val result = testee.update()

        // Then
        assertFalse(result)
        verifyNoInteractions(mockBrokerDataDownloader)
        verify(mockPirRepository, never()).updateMainEtag(any())
        verify(mockPirRepository, never()).updateBrokerJsons(any())
    }

    @Test
    fun whenMainConfigSuccessfulButBodyIsNullThenReturnsTrueButNoUpdate() = runTest {
        // Given
        val successResponseWithNullBody = Response.success<PirMainConfig>(null)
        whenever(mockPirRepository.getCurrentMainEtag()).thenReturn("old-etag")
        whenever(mockPirRepository.getStoredBrokersCount()).thenReturn(2)
        whenever(mockDbpService.getMainConfig("old-etag")).thenReturn(successResponseWithNullBody)

        // When
        val result = testee.update()

        // Then
        assertTrue(result)
        verifyNoInteractions(mockBrokerDataDownloader)
        verify(mockPirRepository, never()).updateMainEtag(any())
        verify(mockPirRepository, never()).updateBrokerJsons(any())
    }

    @Test
    fun whenDbpServiceThrowsExceptionThenReturnsFalse() = runTest {
        // Given
        whenever(mockPirRepository.getCurrentMainEtag()).thenReturn("old-etag")
        whenever(mockPirRepository.getStoredBrokersCount()).thenReturn(2)
        whenever(mockDbpService.getMainConfig("old-etag")).thenThrow(RuntimeException("Network error"))

        // When
        val result = testee.update()

        // Then
        assertFalse(result)
        verifyNoInteractions(mockBrokerDataDownloader)
        verify(mockPirRepository, never()).updateMainEtag(any())
        verify(mockPirRepository, never()).updateBrokerJsons(any())
    }

    @Test
    fun whenEtagIntegrityCheckFailsWithZeroStoredBrokersThenClearsEtag() = runTest {
        // Given
        val successResponse = Response.success(testMainConfig)
        // Database migration failed.
        whenever(mockPirRepository.getCurrentMainEtag()).thenReturn(testEtag, null)
        whenever(mockPirRepository.getStoredBrokersCount()).thenReturn(0)
        whenever(mockDbpService.getMainConfig(null)).thenReturn(successResponse)
        whenever(mockPirRepository.getAllLocalBrokerJsons()).thenReturn(emptyList())

        // When
        val result = testee.update()

        // Then
        assertTrue(result)
        verify(mockPirRepository).updateMainEtag(null) // Clears etag due to integrity check
        verify(mockPirRepository).updateMainEtag(testNewEtag) // Updates with new etag from response
        verify(mockPirRepository).updateBrokerJsons(testNewBrokerJsons)
        verify(mockBrokerDataDownloader).downloadBrokerData(listOf(testFileName1, testFileName2, testFileName3))
    }

    @Test
    fun whenMainConfigBrokersUnchangedThenDontUpdateBrokers() = runTest {
        // Given
        val successResponse = Response.success(testMainConfig)
        whenever(mockPirRepository.getCurrentMainEtag()).thenReturn("old-etag")
        whenever(mockPirRepository.getStoredBrokersCount()).thenReturn(2)
        whenever(mockDbpService.getMainConfig("old-etag")).thenReturn(successResponse)
        // Config and stored have the same jsons
        whenever(mockPirRepository.getAllLocalBrokerJsons()).thenReturn(testNewBrokerJsons)

        // When
        val result = testee.update()

        // Then
        assertTrue(result)
        verify(mockPirRepository).updateBrokerJsons(testNewBrokerJsons)
        verify(mockPirRepository).updateMainEtag(testNewEtag)
        verifyNoInteractions(mockBrokerDataDownloader)
    }

    @Test
    fun whenRepositoryThrowsExceptionThenReturnsFalse() = runTest {
        // Given
        whenever(mockPirRepository.getCurrentMainEtag()).thenThrow(RuntimeException("Database error"))

        // When
        val result = testee.update()

        // Then
        assertFalse(result)
        verifyNoInteractions(mockDbpService)
        verifyNoInteractions(mockBrokerDataDownloader)
    }
}
