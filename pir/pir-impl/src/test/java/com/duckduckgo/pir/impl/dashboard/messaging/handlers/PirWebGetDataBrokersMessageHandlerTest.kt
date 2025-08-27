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
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.createJsMessage
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.MirrorSite
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PirWebGetDataBrokersMessageHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: PirWebGetDataBrokersMessageHandler

    private val mockRepository: PirRepository = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()
    private val testScope = TestScope()

    @Before
    fun setUp() {
        testee = PirWebGetDataBrokersMessageHandler(
            repository = mockRepository,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            appCoroutineScope = testScope,
        )
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(PirDashboardWebMessages.GET_DATA_BROKERS, testee.message)
    }

    @Test
    fun whenProcessWithNoBrokersThenSendsEmptyResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.GET_DATA_BROKERS)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(emptyList())
        whenever(mockRepository.getAllMirrorSites()).thenReturn(emptyList())
        whenever(mockRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).getAllActiveBrokerObjects()
        verify(mockRepository).getAllMirrorSites()
        verify(mockRepository).getAllBrokerOptOutUrls()
        verifyDataBrokersResponse(jsMessage, expectedBrokers = emptyList())
    }

    @Test
    fun whenProcessWithOnlyActiveBrokersThenSendsBrokerResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.GET_DATA_BROKERS)
        val brokerObject =
            createBrokerObject("TestBroker", "https://testbroker.com", "https://parent.com")
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(brokerObject))
        whenever(mockRepository.getAllMirrorSites()).thenReturn(emptyList())
        whenever(mockRepository.getAllBrokerOptOutUrls()).thenReturn(mapOf("TestBroker" to "https://optout.com"))

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).getAllActiveBrokerObjects()
        verify(mockRepository).getAllMirrorSites()
        verify(mockRepository).getAllBrokerOptOutUrls()

        val expectedBrokers = listOf(
            ExpectedDataBroker(
                url = "https://testbroker.com",
                name = "TestBroker",
                parentURL = "https://parent.com",
                optOutUrl = "https://optout.com",
            ),
        )
        verifyDataBrokersResponse(jsMessage, expectedBrokers = expectedBrokers)
    }

    @Test
    fun whenProcessWithOnlyMirrorSitesThenSendsMirrorResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.GET_DATA_BROKERS)
        val parentBroker = createBrokerObject("ParentBroker", "https://parent.com", null)
        val mirrorSite =
            createMirrorSite("MirrorSite", "https://mirror.com", "ParentBroker", removedAt = 0L)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker))
        whenever(mockRepository.getAllMirrorSites()).thenReturn(listOf(mirrorSite))
        whenever(mockRepository.getAllBrokerOptOutUrls()).thenReturn(mapOf("ParentBroker" to "https://optout.com"))

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).getAllActiveBrokerObjects()
        verify(mockRepository).getAllMirrorSites()
        verify(mockRepository).getAllBrokerOptOutUrls()

        val expectedBrokers = listOf(
            ExpectedDataBroker(
                url = "https://parent.com",
                name = "ParentBroker",
                parentURL = null,
                optOutUrl = "https://optout.com",
            ),
            ExpectedDataBroker(
                url = "https://mirror.com",
                name = "MirrorSite",
                parentURL = "https://parent.com",
                optOutUrl = "https://optout.com",
            ),
        )
        verifyDataBrokersResponse(jsMessage, expectedBrokers = expectedBrokers)
    }

    @Test
    fun whenProcessWithRemovedMirrorSitesThenExcludesRemovedSites() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.GET_DATA_BROKERS)
        val activeMirror =
            createMirrorSite("ActiveMirror", "https://active.com", "ParentBroker", removedAt = 0L)
        val removedMirror = createMirrorSite(
            "RemovedMirror",
            "https://removed.com",
            "ParentBroker",
            removedAt = 123456L,
        )
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(emptyList())
        whenever(mockRepository.getAllMirrorSites()).thenReturn(listOf(activeMirror, removedMirror))
        whenever(mockRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).getAllActiveBrokerObjects()
        verify(mockRepository).getAllMirrorSites()
        verify(mockRepository).getAllBrokerOptOutUrls()

        val expectedBrokers = listOf(
            ExpectedDataBroker(
                url = "https://active.com",
                name = "ActiveMirror",
                parentURL = null, // No parent broker object, so parentURL will be null
                optOutUrl = null,
            ),
        )
        verifyDataBrokersResponse(jsMessage, expectedBrokers = expectedBrokers)
    }

    @Test
    fun whenProcessWithBrokersAndMirrorSitesThenCombinesResults() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.GET_DATA_BROKERS)
        val broker1 = createBrokerObject("Broker1", "https://broker1.com", null)
        val broker2 = createBrokerObject("Broker2", "https://broker2.com", "https://parent.com")
        val mirror1 = createMirrorSite("Mirror1", "https://mirror1.com", "Broker1", removedAt = 0L)
        val mirror2 = createMirrorSite("Mirror2", "https://mirror2.com", "Broker2", removedAt = 0L)

        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(broker1, broker2))
        whenever(mockRepository.getAllMirrorSites()).thenReturn(listOf(mirror1, mirror2))
        whenever(mockRepository.getAllBrokerOptOutUrls()).thenReturn(
            mapOf(
                "Broker1" to "https://optout1.com",
                "Broker2" to "https://optout2.com",
            ),
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).getAllActiveBrokerObjects()
        verify(mockRepository).getAllMirrorSites()
        verify(mockRepository).getAllBrokerOptOutUrls()

        val expectedBrokers = listOf(
            ExpectedDataBroker(
                url = "https://broker1.com",
                name = "Broker1",
                parentURL = null,
                optOutUrl = "https://optout1.com",
            ),
            ExpectedDataBroker(
                url = "https://broker2.com",
                name = "Broker2",
                parentURL = "https://parent.com",
                optOutUrl = "https://optout2.com",
            ),
            ExpectedDataBroker(
                url = "https://mirror1.com",
                name = "Mirror1",
                parentURL = "https://broker1.com", // Mirror's parent is Broker1
                optOutUrl = "https://optout1.com",
            ),
            ExpectedDataBroker(
                url = "https://mirror2.com",
                name = "Mirror2",
                parentURL = "https://broker2.com", // Mirror's parent is Broker2
                optOutUrl = "https://optout2.com",
            ),
        )
        verifyDataBrokersResponse(jsMessage, expectedBrokers = expectedBrokers)
    }

    @Test
    fun whenProcessWithDuplicateDataThenDeduplicatesResults() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.GET_DATA_BROKERS)
        val broker = createBrokerObject("TestBroker", "https://test.com", null)
        val duplicateBroker = createBrokerObject("TestBroker", "https://test.com", null)
        val mirror =
            createMirrorSite("TestBroker", "https://test.com", "TestBroker", removedAt = 0L)

        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(
            listOf(
                broker,
                duplicateBroker,
            ),
        )
        whenever(mockRepository.getAllMirrorSites()).thenReturn(listOf(mirror))
        whenever(mockRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).getAllActiveBrokerObjects()
        verify(mockRepository).getAllMirrorSites()
        verify(mockRepository).getAllBrokerOptOutUrls()

        // Should deduplicate based on the distinct() call in the implementation
        val expectedBrokers = listOf(
            ExpectedDataBroker(
                url = "https://test.com",
                name = "TestBroker",
                parentURL = null,
                optOutUrl = null,
            ),
            ExpectedDataBroker(
                url = "https://test.com",
                name = "TestBroker",
                parentURL = "https://test.com", // Mirror's parent URL comes from the broker
                optOutUrl = null,
            ),
        )
        verifyDataBrokersResponse(jsMessage, expectedBrokers = expectedBrokers)
    }

    @Test
    fun whenProcessWithNoOptOutUrlsThenHandlesGracefully() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.GET_DATA_BROKERS)
        val broker = createBrokerObject("TestBroker", "https://test.com", null)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(broker))
        whenever(mockRepository.getAllMirrorSites()).thenReturn(emptyList())
        whenever(mockRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap()) // No opt-out URLs

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).getAllActiveBrokerObjects()
        verify(mockRepository).getAllMirrorSites()
        verify(mockRepository).getAllBrokerOptOutUrls()

        val expectedBrokers = listOf(
            ExpectedDataBroker(
                url = "https://test.com",
                name = "TestBroker",
                parentURL = null,
                optOutUrl = null, // No opt-out URL provided
            ),
        )
        verifyDataBrokersResponse(jsMessage, expectedBrokers = expectedBrokers)
    }

    private fun createBrokerObject(name: String, url: String, parent: String?): Broker {
        return Broker(
            name = name,
            url = url,
            parent = parent,
            fileName = "",
            version = "",
            addedDatetime = 0L,
            removedAt = 0L,
        )
    }

    private fun createMirrorSite(
        name: String,
        url: String,
        parentSite: String,
        removedAt: Long,
    ): MirrorSite {
        return MirrorSite(
            name = name,
            url = url,
            parentSite = parentSite,
            removedAt = removedAt,
            addedAt = 0L,
            optOutUrl = "",
        )
    }

    private fun verifyDataBrokersResponse(
        jsMessage: JsMessage,
        expectedBrokers: List<ExpectedDataBroker>,
    ) {
        val callbackDataCaptor = argumentCaptor<JsCallbackData>()
        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())

        val callbackData = callbackDataCaptor.firstValue
        assertEquals(jsMessage.featureName, callbackData.featureName)
        assertEquals(jsMessage.method, callbackData.method)
        assertEquals(jsMessage.id ?: "", callbackData.id)

        // Verify data brokers response structure
        assertTrue(callbackData.params.has("dataBrokers"))
        val dataBrokers = callbackData.params.getJSONArray("dataBrokers")
        assertEquals(expectedBrokers.size, dataBrokers.length())

        // Verify each broker's data matches expected values
        val actualBrokers = mutableListOf<ExpectedDataBroker>()
        for (i in 0 until dataBrokers.length()) {
            val broker = dataBrokers.getJSONObject(i)
            actualBrokers.add(
                ExpectedDataBroker(
                    url = broker.getString("url"),
                    name = broker.getString("name"),
                    parentURL = broker.optString("parentURL").takeIf { it.isNotEmpty() },
                    optOutUrl = broker.optString("optOutUrl").takeIf { it.isNotEmpty() },
                ),
            )
        }

        // Sort both lists by name to ensure consistent comparison
        val sortedExpected = expectedBrokers.sortedBy { it.name }
        val sortedActual = actualBrokers.sortedBy { it.name }

        assertEquals("Broker data mismatch", sortedExpected, sortedActual)
    }

    data class ExpectedDataBroker(
        val url: String,
        val name: String,
        val parentURL: String?,
        val optOutUrl: String?,
    )
}
