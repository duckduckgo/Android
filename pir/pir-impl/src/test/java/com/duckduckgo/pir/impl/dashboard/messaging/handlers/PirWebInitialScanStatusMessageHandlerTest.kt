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

package com.duckduckgo.pir.impl.dashboard.messaging.handlers

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.json.JSONObjectAdapter
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.createJsMessage
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse.InitialScanResponse
import com.duckduckgo.pir.impl.dashboard.state.DashboardBroker
import com.duckduckgo.pir.impl.dashboard.state.DashboardExtractedProfileResult
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardInitialScanStateProvider
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardInitialScanStateProvider.DashboardBrokerWithStatus
import com.duckduckgo.pir.impl.models.AddressCityState
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PirWebInitialScanStatusMessageHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: PirWebInitialScanStatusMessageHandler

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
    private val adapter: JsonAdapter<InitialScanResponse> = moshi.adapter(InitialScanResponse::class.java)

    private val fakeJsMessaging = FakeJsMessaging()
    private val mockStateProvider: PirDashboardInitialScanStateProvider = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()
    private val testScope = TestScope()
    private val mockRepository: PirRepository = mock()

    @Before
    fun setUp() {
        testee = PirWebInitialScanStatusMessageHandler(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            appCoroutineScope = testScope,
            stateProvider = mockStateProvider,
            pirRepository = mockRepository,
        )
        fakeJsMessaging.reset()
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(PirDashboardWebMessages.INITIAL_SCAN_STATUS, testee.message)
    }

    @Test
    fun whenProcessWithNoDataThenSendsEmptyResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("", PirDashboardWebMessages.INITIAL_SCAN_STATUS)
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(mock()))
        whenever(mockStateProvider.getScanResults()).thenReturn(emptyList())
        whenever(mockStateProvider.getFullyCompletedBrokersTotal()).thenReturn(0)
        whenever(mockStateProvider.getActiveBrokersAndMirrorSitesTotal()).thenReturn(0)
        whenever(mockStateProvider.getAllScannedBrokersStatus()).thenReturn(emptyList())

        // When
        testee.process(jsMessage, fakeJsMessaging, mockJsMessageCallback)

        // Then
        verifyInitialScanResponse(
            expectedResultsCount = 0,
            expectedCurrentScans = 0,
            expectedTotalScans = 0,
            expectedScannedBrokersCount = 0,
        )
    }

    @Test
    fun whenProcessWithNoProfilesThenSendsEmptyResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("", PirDashboardWebMessages.INITIAL_SCAN_STATUS)
        val extractedProfile = createExtractedProfile()
        val dashboardBroker = createDashboardBroker()
        val scanResults = listOf(
            DashboardExtractedProfileResult(
                extractedProfile = extractedProfile,
                broker = dashboardBroker,
                optOutSubmittedDateInMillis = 1640995200000L,
                optOutRemovedDateInMillis = 1643673600000L,
                estimatedRemovalDateInMillis = 1641081600000L,
                hasMatchingRecordOnParentBroker = true,
            ),
        )
        val scannedBrokers = listOf(
            DashboardBrokerWithStatus(
                broker = dashboardBroker,
                status = DashboardBrokerWithStatus.Status.COMPLETED,
            ),
        )

        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(emptyList())
        whenever(mockStateProvider.getScanResults()).thenReturn(scanResults)
        whenever(mockStateProvider.getFullyCompletedBrokersTotal()).thenReturn(5)
        whenever(mockStateProvider.getActiveBrokersAndMirrorSitesTotal()).thenReturn(10)
        whenever(mockStateProvider.getAllScannedBrokersStatus()).thenReturn(scannedBrokers)

        // When
        testee.process(jsMessage, fakeJsMessaging, mockJsMessageCallback)

        // Then
        verifyInitialScanResponse(
            expectedResultsCount = 0,
            expectedCurrentScans = 0,
            expectedTotalScans = 0,
            expectedScannedBrokersCount = 0,
        )
    }

    @Test
    fun whenProcessWithScanResultsAndProgressThenSendsCorrectResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("", PirDashboardWebMessages.INITIAL_SCAN_STATUS)
        val extractedProfile = createExtractedProfile()
        val dashboardBroker = createDashboardBroker()
        val scanResults = listOf(
            DashboardExtractedProfileResult(
                extractedProfile = extractedProfile,
                broker = dashboardBroker,
                optOutSubmittedDateInMillis = 1640995200000L,
                optOutRemovedDateInMillis = 1643673600000L,
                estimatedRemovalDateInMillis = 1641081600000L,
                hasMatchingRecordOnParentBroker = true,
            ),
        )
        val scannedBrokers = listOf(
            DashboardBrokerWithStatus(
                broker = dashboardBroker,
                status = DashboardBrokerWithStatus.Status.COMPLETED,
            ),
        )

        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(mock()))
        whenever(mockStateProvider.getScanResults()).thenReturn(scanResults)
        whenever(mockStateProvider.getFullyCompletedBrokersTotal()).thenReturn(5)
        whenever(mockStateProvider.getActiveBrokersAndMirrorSitesTotal()).thenReturn(10)
        whenever(mockStateProvider.getAllScannedBrokersStatus()).thenReturn(scannedBrokers)

        // When
        testee.process(jsMessage, fakeJsMessaging, mockJsMessageCallback)

        // Then
        verifyInitialScanResponse(
            expectedResultsCount = 1,
            expectedCurrentScans = 5,
            expectedTotalScans = 10,
            expectedScannedBrokersCount = 1,
        )
    }

    @Test
    fun whenProcessWithMultipleScanResultsThenTransformsDataCorrectly() = runTest {
        // Given
        val jsMessage = createJsMessage("", PirDashboardWebMessages.INITIAL_SCAN_STATUS)
        val extractedProfile1 = createExtractedProfile(
            name = "John Doe",
            alternativeNames = listOf("J. Doe", "Johnny"),
            relatives = listOf("Jane Doe"),
            addresses = listOf(
                AddressCityState(city = "New York", state = "NY"),
                AddressCityState(city = "Boston", state = "MA"),
            ),
            dateAddedInMillis = 1640995200000L, // Jan 1, 2022
        )
        val extractedProfile2 = createExtractedProfile(
            name = "Jane Smith",
            alternativeNames = listOf("J. Smith"),
            relatives = listOf("John Smith"),
            addresses = listOf(AddressCityState(city = "Los Angeles", state = "CA")),
            dateAddedInMillis = 1641081600000L, // Jan 2, 2022
        )
        val broker1 = createDashboardBroker(name = "Broker1", url = "https://broker1.com")
        val broker2 = createDashboardBroker(name = "Broker2", url = "https://broker2.com")

        val scanResults = listOf(
            DashboardExtractedProfileResult(
                extractedProfile = extractedProfile1,
                broker = broker1,
                optOutSubmittedDateInMillis = 1640995200000L,
                hasMatchingRecordOnParentBroker = false,
            ),
            DashboardExtractedProfileResult(
                extractedProfile = extractedProfile2,
                broker = broker2,
                optOutRemovedDateInMillis = 1643673600000L,
                hasMatchingRecordOnParentBroker = true,
            ),
        )

        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(mock()))
        whenever(mockStateProvider.getScanResults()).thenReturn(scanResults)
        whenever(mockStateProvider.getFullyCompletedBrokersTotal()).thenReturn(2)
        whenever(mockStateProvider.getActiveBrokersAndMirrorSitesTotal()).thenReturn(5)
        whenever(mockStateProvider.getAllScannedBrokersStatus()).thenReturn(emptyList())

        // When
        testee.process(jsMessage, fakeJsMessaging, mockJsMessageCallback)

        // Then
        val callbackData = fakeJsMessaging.responses.first()
        assertNotNull(callbackData)
        val response = parseInitialScanResponse(callbackData.params)
        assertNotNull(response)
        assertEquals(2, response!!.resultsFound.size)

        // Verify first result
        val result1 = response.resultsFound[0]
        assertEquals("John Doe", result1.name)
        assertEquals(listOf("J. Doe", "Johnny"), result1.alternativeNames)
        assertEquals(listOf("Jane Doe"), result1.relatives)
        assertEquals(2, result1.addresses.size)
        assertEquals("New York", result1.addresses[0].city)
        assertEquals("NY", result1.addresses[0].state)
        assertEquals("Boston", result1.addresses[1].city)
        assertEquals("MA", result1.addresses[1].state)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1640995200000L), result1.foundDate)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1640995200000L), result1.optOutSubmittedDate)
        assertEquals(false, result1.hasMatchingRecordOnParentBroker)

        // Verify second result
        val result2 = response.resultsFound[1]
        assertEquals("Jane Smith", result2.name)
        assertEquals(listOf("J. Smith"), result2.alternativeNames)
        assertEquals(listOf("John Smith"), result2.relatives)
        assertEquals(1, result2.addresses.size)
        assertEquals("Los Angeles", result2.addresses[0].city)
        assertEquals("CA", result2.addresses[0].state)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1641081600000L), result2.foundDate)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1643673600000L), result2.removedDate)
        assertEquals(true, result2.hasMatchingRecordOnParentBroker)

        // Verify scan progress
        assertEquals(2, response.scanProgress.currentScans)
        assertEquals(5, response.scanProgress.totalScans)
        assertEquals(0, response.scanProgress.scannedBrokers.size)
    }

    @Test
    fun whenProcessWithScannedBrokersThenIncludesStatusCorrectly() = runTest {
        // Given
        val jsMessage = createJsMessage("", PirDashboardWebMessages.INITIAL_SCAN_STATUS)
        val broker1 = createDashboardBroker(
            name = "TestBroker1",
            url = "https://test1.com",
            optOutUrl = "https://test1.com/optout",
            parentUrl = "https://parent1.com",
        )
        val broker2 = createDashboardBroker(
            name = "TestBroker2",
            url = "https://test2.com",
        )

        val scannedBrokers = listOf(
            DashboardBrokerWithStatus(
                broker = broker1,
                status = DashboardBrokerWithStatus.Status.COMPLETED,
            ),
            DashboardBrokerWithStatus(
                broker = broker2,
                status = DashboardBrokerWithStatus.Status.IN_PROGRESS,
            ),
        )

        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(mock()))
        whenever(mockStateProvider.getScanResults()).thenReturn(emptyList())
        whenever(mockStateProvider.getFullyCompletedBrokersTotal()).thenReturn(1)
        whenever(mockStateProvider.getActiveBrokersAndMirrorSitesTotal()).thenReturn(2)
        whenever(mockStateProvider.getAllScannedBrokersStatus()).thenReturn(scannedBrokers)

        // When
        testee.process(jsMessage, fakeJsMessaging, mockJsMessageCallback)

        // Then
        val response = fakeJsMessaging.getLastResponse()
        assertNotNull(response)
        val responseJson = response!!.params
        val parsedResponse = parseInitialScanResponse(responseJson)
        assertNotNull(parsedResponse)
        assertEquals(2, parsedResponse!!.scanProgress.scannedBrokers.size)

        val scannedBroker1 = parsedResponse.scanProgress.scannedBrokers[0]
        assertEquals("TestBroker1", scannedBroker1.name)
        assertEquals("https://test1.com", scannedBroker1.url)
        assertEquals("https://test1.com/optout", scannedBroker1.optOutUrl)
        assertEquals("https://parent1.com", scannedBroker1.parentURL)
        assertEquals("completed", scannedBroker1.status)

        val scannedBroker2 = parsedResponse.scanProgress.scannedBrokers[1]
        assertEquals("TestBroker2", scannedBroker2.name)
        assertEquals("https://test2.com", scannedBroker2.url)
        assertEquals(null, scannedBroker2.optOutUrl)
        assertEquals(null, scannedBroker2.parentURL)
        assertEquals("in-progress", scannedBroker2.status)
    }

    private fun verifyInitialScanResponse(
        expectedResultsCount: Int,
        expectedCurrentScans: Int,
        expectedTotalScans: Int,
        expectedScannedBrokersCount: Int,
    ) {
        val callbackData = fakeJsMessaging.getLastResponse()
        assertNotNull(callbackData)
        assertEquals(callbackData?.method, PirDashboardWebMessages.INITIAL_SCAN_STATUS.messageName)

        callbackData?.params?.let {
            val response = parseInitialScanResponse(it)
            assertNotNull(response)
            assertEquals(expectedResultsCount, response!!.resultsFound.size)
            assertEquals(expectedCurrentScans, response.scanProgress.currentScans)
            assertEquals(expectedTotalScans, response.scanProgress.totalScans)
            assertEquals(expectedScannedBrokersCount, response.scanProgress.scannedBrokers.size)
        }
    }

    private fun parseInitialScanResponse(responseJson: JSONObject): InitialScanResponse? {
        return adapter.fromJson(responseJson.toString())
    }

    private fun createExtractedProfile(
        name: String = "Test User",
        alternativeNames: List<String> = emptyList(),
        relatives: List<String> = emptyList(),
        addresses: List<AddressCityState> = emptyList(),
        dateAddedInMillis: Long = 1640995200000L,
    ): ExtractedProfile {
        return ExtractedProfile(
            dbId = 1L,
            profileQueryId = 1L,
            brokerName = "TestBroker",
            name = name,
            alternativeNames = alternativeNames,
            relatives = relatives,
            addresses = addresses,
            dateAddedInMillis = dateAddedInMillis,
        )
    }

    private fun createDashboardBroker(
        name: String = "TestBroker",
        url: String = "https://testbroker.com",
        optOutUrl: String? = null,
        parentUrl: String? = null,
    ): DashboardBroker {
        return DashboardBroker(
            name = name,
            url = url,
            optOutUrl = optOutUrl,
            parentUrl = parentUrl,
        )
    }
}
