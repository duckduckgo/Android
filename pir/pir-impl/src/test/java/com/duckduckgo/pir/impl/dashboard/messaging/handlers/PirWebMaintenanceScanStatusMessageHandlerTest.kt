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
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse.MaintenanceScanStatusResponse
import com.duckduckgo.pir.impl.dashboard.state.DashboardBroker
import com.duckduckgo.pir.impl.dashboard.state.DashboardExtractedProfileResult
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardMaintenanceScanDataProvider
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardMaintenanceScanDataProvider.DashboardBrokerMatch
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardMaintenanceScanDataProvider.DashboardRemovedExtractedProfileResult
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardMaintenanceScanDataProvider.DashboardScanDetails
import com.duckduckgo.pir.impl.models.AddressCityState
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit
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

@RunWith(AndroidJUnit4::class)
class PirWebMaintenanceScanStatusMessageHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: PirWebMaintenanceScanStatusMessageHandler

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
    private val adapter: JsonAdapter<MaintenanceScanStatusResponse> = moshi.adapter(MaintenanceScanStatusResponse::class.java)

    private val fakeJsMessaging = FakeJsMessaging()
    private val mockStatusProvider: PirDashboardMaintenanceScanDataProvider = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()
    private val testScope = TestScope()

    @Before
    fun setUp() {
        testee = PirWebMaintenanceScanStatusMessageHandler(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            appCoroutineScope = testScope,
            statusProvider = mockStatusProvider,
        )
        fakeJsMessaging.reset()
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(PirDashboardWebMessages.MAINTENANCE_SCAN_STATUS, testee.message)
    }

    @Test
    fun whenProcessWithNoDataThenSendsEmptyResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("", PirDashboardWebMessages.MAINTENANCE_SCAN_STATUS)
        whenever(mockStatusProvider.getInProgressOptOuts()).thenReturn(emptyList())
        whenever(mockStatusProvider.getRemovedOptOuts()).thenReturn(emptyList())
        whenever(mockStatusProvider.getScannedBrokerCount()).thenReturn(0)
        whenever(mockStatusProvider.getLastScanDetails()).thenReturn(
            DashboardScanDetails(
                dateInMillis = 0L,
                brokerMatches = emptyList(),
            ),
        )
        whenever(mockStatusProvider.getNextScanDetails()).thenReturn(
            DashboardScanDetails(
                dateInMillis = 0L,
                brokerMatches = emptyList(),
            ),
        )

        // When
        testee.process(jsMessage, fakeJsMessaging, mockJsMessageCallback)

        // Then
        verifyMaintenanceScanResponse(
            expectedInProgressCount = 0,
            expectedCompletedCount = 0,
            expectedScannedSitesCount = 0,
            expectedLastScanBrokersCount = 0,
            expectedNextScanBrokersCount = 0,
        )
    }

    @Test
    fun whenProcessWithInProgressOptOutsThenSendsCorrectResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("", PirDashboardWebMessages.MAINTENANCE_SCAN_STATUS)
        val extractedProfile = createExtractedProfile()
        val dashboardBroker = createDashboardBroker()
        val inProgressOptOuts = listOf(
            DashboardExtractedProfileResult(
                extractedProfile = extractedProfile,
                broker = dashboardBroker,
                optOutSubmittedDateInMillis = 1640995200000L,
                estimatedRemovalDateInMillis = 1641081600000L,
                hasMatchingRecordOnParentBroker = false,
            ),
        )

        whenever(mockStatusProvider.getInProgressOptOuts()).thenReturn(inProgressOptOuts)
        whenever(mockStatusProvider.getRemovedOptOuts()).thenReturn(emptyList())
        whenever(mockStatusProvider.getScannedBrokerCount()).thenReturn(5)
        whenever(mockStatusProvider.getLastScanDetails()).thenReturn(createScanDetails())
        whenever(mockStatusProvider.getNextScanDetails()).thenReturn(createScanDetails())

        // When
        testee.process(jsMessage, fakeJsMessaging, mockJsMessageCallback)

        // Then
        verifyMaintenanceScanResponse(
            expectedInProgressCount = 1,
            expectedCompletedCount = 0,
            expectedScannedSitesCount = 5,
            expectedLastScanBrokersCount = 1,
            expectedNextScanBrokersCount = 1,
        )
    }

    @Test
    fun whenProcessWithRemovedOptOutsThenSendsCorrectResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("", PirDashboardWebMessages.MAINTENANCE_SCAN_STATUS)
        val extractedProfile = createExtractedProfile()
        val dashboardBroker = createDashboardBroker()
        val removedOptOuts = listOf(
            DashboardRemovedExtractedProfileResult(
                result = DashboardExtractedProfileResult(
                    extractedProfile = extractedProfile,
                    broker = dashboardBroker,
                    optOutSubmittedDateInMillis = 1640995200000L,
                    optOutRemovedDateInMillis = 1643673600000L,
                    estimatedRemovalDateInMillis = 1641081600000L,
                    hasMatchingRecordOnParentBroker = true,
                ),
                matches = 3,
            ),
        )

        whenever(mockStatusProvider.getInProgressOptOuts()).thenReturn(emptyList())
        whenever(mockStatusProvider.getRemovedOptOuts()).thenReturn(removedOptOuts)
        whenever(mockStatusProvider.getScannedBrokerCount()).thenReturn(10)
        whenever(mockStatusProvider.getLastScanDetails()).thenReturn(createScanDetails())
        whenever(mockStatusProvider.getNextScanDetails()).thenReturn(createScanDetails())

        // When
        testee.process(jsMessage, fakeJsMessaging, mockJsMessageCallback)

        // Then
        verifyMaintenanceScanResponse(
            expectedInProgressCount = 0,
            expectedCompletedCount = 1,
            expectedScannedSitesCount = 10,
            expectedLastScanBrokersCount = 1,
            expectedNextScanBrokersCount = 1,
        )
    }

    @Test
    fun whenProcessWithComplexDataThenTransformsCorrectly() = runTest {
        // Given
        val jsMessage = createJsMessage("", PirDashboardWebMessages.MAINTENANCE_SCAN_STATUS)
        val extractedProfile = createExtractedProfile(
            name = "John Doe",
            alternativeNames = listOf("Johnny", "J. Doe"),
            relatives = listOf("Jane Doe"),
            addresses = listOf(
                AddressCityState(city = "New York", state = "NY"),
                AddressCityState(city = "Boston", state = "MA"),
            ),
            dateAddedInMillis = 1640995200000L,
        )
        val broker = createDashboardBroker(
            name = "TestBroker",
            url = "https://testbroker.com",
            optOutUrl = "https://testbroker.com/optout",
            parentUrl = "https://parent.com",
        )

        val inProgressOptOut = DashboardExtractedProfileResult(
            extractedProfile = extractedProfile,
            broker = broker,
            optOutSubmittedDateInMillis = 1640995200000L,
            estimatedRemovalDateInMillis = 1641081600000L,
            hasMatchingRecordOnParentBroker = true,
        )

        val removedOptOut = DashboardRemovedExtractedProfileResult(
            result = DashboardExtractedProfileResult(
                extractedProfile = extractedProfile,
                broker = broker,
                optOutSubmittedDateInMillis = 1640995200000L,
                optOutRemovedDateInMillis = 1643673600000L,
                estimatedRemovalDateInMillis = 1641081600000L,
                hasMatchingRecordOnParentBroker = false,
            ),
            matches = 5,
        )

        val scanDetails = DashboardScanDetails(
            dateInMillis = 1640995200000L,
            brokerMatches = listOf(
                DashboardBrokerMatch(
                    broker = broker,
                    dateInMillis = 1640995200000L,
                ),
            ),
        )

        whenever(mockStatusProvider.getInProgressOptOuts()).thenReturn(listOf(inProgressOptOut))
        whenever(mockStatusProvider.getRemovedOptOuts()).thenReturn(listOf(removedOptOut))
        whenever(mockStatusProvider.getScannedBrokerCount()).thenReturn(15)
        whenever(mockStatusProvider.getLastScanDetails()).thenReturn(scanDetails)
        whenever(mockStatusProvider.getNextScanDetails()).thenReturn(scanDetails)

        // When
        testee.process(jsMessage, fakeJsMessaging, mockJsMessageCallback)

        // Then
        val response = fakeJsMessaging.getLastResponse()
        assertNotNull(response)
        val responseJson = response!!.params
        val parsedResponse = parseMaintenanceScanResponse(responseJson)
        assertNotNull(parsedResponse)

        // Verify in-progress opt-out
        assertEquals(1, parsedResponse!!.inProgressOptOuts.size)
        val inProgressResult = parsedResponse.inProgressOptOuts[0]
        assertEquals("John Doe", inProgressResult.name)
        assertEquals(listOf("Johnny", "J. Doe"), inProgressResult.alternativeNames)
        assertEquals(listOf("Jane Doe"), inProgressResult.relatives)
        assertEquals(2, inProgressResult.addresses.size)
        assertEquals("New York", inProgressResult.addresses[0].city)
        assertEquals("NY", inProgressResult.addresses[0].state)
        assertEquals("Boston", inProgressResult.addresses[1].city)
        assertEquals("MA", inProgressResult.addresses[1].state)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1640995200000L), inProgressResult.foundDate)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1640995200000L), inProgressResult.optOutSubmittedDate)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1641081600000L), inProgressResult.estimatedRemovalDate)
        assertEquals(null, inProgressResult.removedDate)
        assertEquals(true, inProgressResult.hasMatchingRecordOnParentBroker)

        // Verify completed opt-out
        assertEquals(1, parsedResponse.completedOptOuts.size)
        val completedResult = parsedResponse.completedOptOuts[0]
        assertEquals("John Doe", completedResult.name)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1643673600000L), completedResult.removedDate)
        assertEquals(5, completedResult.matches)
        assertEquals(false, completedResult.hasMatchingRecordOnParentBroker)

        // Verify scan schedule
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1640995200000L), parsedResponse.scanSchedule.lastScan.date)
        assertEquals(1, parsedResponse.scanSchedule.lastScan.dataBrokers.size)
        assertEquals("TestBroker", parsedResponse.scanSchedule.lastScan.dataBrokers[0].name)
        assertEquals("https://testbroker.com", parsedResponse.scanSchedule.lastScan.dataBrokers[0].url)
        assertEquals("https://testbroker.com/optout", parsedResponse.scanSchedule.lastScan.dataBrokers[0].optOutUrl)
        assertEquals("https://parent.com", parsedResponse.scanSchedule.lastScan.dataBrokers[0].parentURL)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1640995200000L), parsedResponse.scanSchedule.lastScan.dataBrokers[0].date)

        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1640995200000L), parsedResponse.scanSchedule.nextScan.date)
        assertEquals(1, parsedResponse.scanSchedule.nextScan.dataBrokers.size)

        // Verify scan history
        assertEquals(15, parsedResponse.scanHistory.sitesScanned)
    }

    @Test
    fun whenProcessWithScanDetailsThenIncludesBrokerInformation() = runTest {
        // Given
        val jsMessage = createJsMessage("", PirDashboardWebMessages.MAINTENANCE_SCAN_STATUS)
        val broker1 = createDashboardBroker(
            name = "Broker1",
            url = "https://broker1.com",
            optOutUrl = "https://broker1.com/optout",
        )
        val broker2 = createDashboardBroker(
            name = "Broker2",
            url = "https://broker2.com",
            parentUrl = "https://parent.com",
        )

        val lastScanDetails = DashboardScanDetails(
            dateInMillis = 1640995200000L,
            brokerMatches = listOf(
                DashboardBrokerMatch(broker = broker1, dateInMillis = 1640995200000L),
                DashboardBrokerMatch(broker = broker2, dateInMillis = 1641081600000L),
            ),
        )

        val nextScanDetails = DashboardScanDetails(
            dateInMillis = 1643673600000L,
            brokerMatches = listOf(
                DashboardBrokerMatch(broker = broker1, dateInMillis = 1643673600000L),
            ),
        )

        whenever(mockStatusProvider.getInProgressOptOuts()).thenReturn(emptyList())
        whenever(mockStatusProvider.getRemovedOptOuts()).thenReturn(emptyList())
        whenever(mockStatusProvider.getScannedBrokerCount()).thenReturn(2)
        whenever(mockStatusProvider.getLastScanDetails()).thenReturn(lastScanDetails)
        whenever(mockStatusProvider.getNextScanDetails()).thenReturn(nextScanDetails)

        // When
        testee.process(jsMessage, fakeJsMessaging, mockJsMessageCallback)

        // Then
        val response = fakeJsMessaging.getLastResponse()
        assertNotNull(response)
        val responseJson = response!!.params
        val parsedResponse = parseMaintenanceScanResponse(responseJson)
        assertNotNull(parsedResponse)

        // Verify last scan details
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1640995200000L), parsedResponse!!.scanSchedule.lastScan.date)
        assertEquals(2, parsedResponse.scanSchedule.lastScan.dataBrokers.size)

        val lastScanBroker1 = parsedResponse.scanSchedule.lastScan.dataBrokers[0]
        assertEquals("Broker1", lastScanBroker1.name)
        assertEquals("https://broker1.com", lastScanBroker1.url)
        assertEquals("https://broker1.com/optout", lastScanBroker1.optOutUrl)
        assertEquals(null, lastScanBroker1.parentURL)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1640995200000L), lastScanBroker1.date)

        val lastScanBroker2 = parsedResponse.scanSchedule.lastScan.dataBrokers[1]
        assertEquals("Broker2", lastScanBroker2.name)
        assertEquals("https://broker2.com", lastScanBroker2.url)
        assertEquals(null, lastScanBroker2.optOutUrl)
        assertEquals("https://parent.com", lastScanBroker2.parentURL)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1641081600000L), lastScanBroker2.date)

        // Verify next scan details
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1643673600000L), parsedResponse.scanSchedule.nextScan.date)
        assertEquals(1, parsedResponse.scanSchedule.nextScan.dataBrokers.size)

        val nextScanBroker = parsedResponse.scanSchedule.nextScan.dataBrokers[0]
        assertEquals("Broker1", nextScanBroker.name)
        assertEquals("https://broker1.com", nextScanBroker.url)
        assertEquals("https://broker1.com/optout", nextScanBroker.optOutUrl)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(1643673600000L), nextScanBroker.date)
    }

    private fun verifyMaintenanceScanResponse(
        expectedInProgressCount: Int,
        expectedCompletedCount: Int,
        expectedScannedSitesCount: Int,
        expectedLastScanBrokersCount: Int,
        expectedNextScanBrokersCount: Int,
    ) {
        val callbackData = fakeJsMessaging.getLastResponse()
        assertNotNull(callbackData)
        assertEquals(callbackData?.method, PirDashboardWebMessages.MAINTENANCE_SCAN_STATUS.messageName)

        callbackData?.params?.let {
            val response = parseMaintenanceScanResponse(it)
            assertNotNull(response)
            assertEquals(expectedInProgressCount, response!!.inProgressOptOuts.size)
            assertEquals(expectedCompletedCount, response.completedOptOuts.size)
            assertEquals(expectedScannedSitesCount, response.scanHistory.sitesScanned)
            assertEquals(expectedLastScanBrokersCount, response.scanSchedule.lastScan.dataBrokers.size)
            assertEquals(expectedNextScanBrokersCount, response.scanSchedule.nextScan.dataBrokers.size)
        }
    }

    private fun parseMaintenanceScanResponse(responseJson: JSONObject): MaintenanceScanStatusResponse? {
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

    private fun createScanDetails(
        dateInMillis: Long = 1640995200000L,
        brokerMatches: List<DashboardBrokerMatch> = listOf(
            DashboardBrokerMatch(
                broker = createDashboardBroker(),
                dateInMillis = dateInMillis,
            ),
        ),
    ): DashboardScanDetails {
        return DashboardScanDetails(
            dateInMillis = dateInMillis,
            brokerMatches = brokerMatches,
        )
    }
}
