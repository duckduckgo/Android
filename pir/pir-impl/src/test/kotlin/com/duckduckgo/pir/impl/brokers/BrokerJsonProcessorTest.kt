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

package com.duckduckgo.pir.impl.brokers

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BrokerJsonProcessorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockPirRepository: PirRepository = mock()
    private val mockPixelSender: PirPixelSender = mock()

    private lateinit var testee: BrokerJsonProcessor

    @Before
    fun setUp() {
        testee = RealBrokerJsonProcessor(mockPirRepository, mockPixelSender)
    }

    // region parseBroker

    @Test
    fun whenParseBrokerWithValidJsonThenReturnsPopulatedBroker() {
        val result = testee.parseBroker(brokerJsonSource("Broker One", "1.0"))

        assertNotNull(result)
        assertEquals("Broker One", result!!.name)
        assertEquals("1.0", result.version)
    }

    @Test
    fun whenParseBrokerWithInvalidJsonThenReturnsNull() {
        val result = testee.parseBroker(Buffer().writeUtf8("{ not valid json }"))

        assertNull(result)
    }

    // endregion

    // region processAndStoreBroker

    @Test
    fun whenInvalidJsonThenReportsFailurePixelWithZeroRemovedAt() = runTest {
        testee.processAndStoreBroker("broker.json", Buffer().writeUtf8("{ not valid json }"))

        verify(mockPixelSender).reportUpdateBrokerJsonFailure(eq("broker.json"), eq(0L))
        verify(mockPirRepository, never()).updateBrokerData(any(), any())
    }

    @Test
    fun whenStoredVersionMatchesThenSkipsStoreAndEmitsNoPixels() = runTest {
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(storedBroker(version = "1.0"))

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0"))

        verify(mockPirRepository, never()).updateBrokerData(any(), any())
        verify(mockPixelSender, never()).reportUpdateBrokerJsonSuccess(any(), any())
        verify(mockPixelSender, never()).reportUpdateBrokerJsonFailure(any(), any())
    }

    @Test
    fun whenVersionChangedThenStoresAndReportsSuccessPixel() = runTest {
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(storedBroker(version = "1.0"))

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "2.0"))

        verify(mockPirRepository).updateBrokerData(eq("broker.json"), any())
        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker.json"), eq(0L))
    }

    @Test
    fun whenBrokerNotPreviouslyStoredThenStoresAndReportsSuccessPixel() = runTest {
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(null)

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0"))

        verify(mockPirRepository).updateBrokerData(eq("broker.json"), any())
        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker.json"), eq(0L))
    }

    @Test
    fun whenGetBrokerForNameThrowsThenReportsFailurePixelWithoutRethrow() = runTest {
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenThrow(RuntimeException("DB error"))

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0"))

        verify(mockPixelSender).reportUpdateBrokerJsonFailure(eq("broker.json"), eq(0L))
        verify(mockPirRepository, never()).updateBrokerData(any(), any())
    }

    @Test
    fun whenUpdateBrokerDataThrowsThenReportsFailurePixelWithoutRethrow() = runTest {
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(null)
        whenever(mockPirRepository.updateBrokerData(any(), any())).thenThrow(RuntimeException("DB error"))

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0"))

        verify(mockPixelSender).reportUpdateBrokerJsonFailure(eq("broker.json"), eq(0L))
    }

    @Test
    fun whenBrokerHasRemovedAtThenPassesRemovedAtToSuccessPixel() = runTest {
        val removedAt = 123456789L
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(null)

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0", removedAt = removedAt))

        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker.json"), eq(removedAt))
    }

    @Test
    fun whenBrokerHasRemovedAtAndStoreThrowsThenPassesRemovedAtToFailurePixel() = runTest {
        val removedAt = 123456789L
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(null)
        whenever(mockPirRepository.updateBrokerData(any(), any())).thenThrow(RuntimeException("DB error"))

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0", removedAt = removedAt))

        verify(mockPixelSender).reportUpdateBrokerJsonFailure(eq("broker.json"), eq(removedAt))
    }

    // endregion

    // region helpers

    private fun brokerJsonSource(
        name: String,
        version: String,
        removedAt: Long? = null,
    ): okio.Source =
        Buffer().writeUtf8(
            """
            {
                "name": "$name",
                "url": "https://broker.com",
                "version": "$version",
                "parent": null,
                "addedDatetime": 1000,
                "optOutUrl": "https://broker.com/optout",
                "steps": [],
                "schedulingConfig": { "retryError": 24, "confirmOptOutScan": 7, "maintenanceScan": 30, "maxAttempts": 3 },
                "removedAt": ${removedAt ?: "null"}
            }
            """.trimIndent(),
        )

    private fun storedBroker(version: String) = Broker(
        name = "Broker One",
        fileName = "broker.json",
        url = "https://broker.com",
        version = version,
        parent = null,
        addedDatetime = 1000L,
        removedAt = 0L,
    )

    // endregion
}
