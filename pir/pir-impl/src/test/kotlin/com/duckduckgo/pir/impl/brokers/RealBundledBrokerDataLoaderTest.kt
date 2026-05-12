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

import android.content.Context
import android.content.res.AssetManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealBundledBrokerDataLoaderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockContext: Context = mock()
    private val mockAssetManager: AssetManager = mock()
    private val mockPirRepository: PirRepository = mock()
    private val mockPixelSender: PirPixelSender = mock()

    private lateinit var testee: RealBundledBrokerDataLoader

    @Before
    fun setUp() {
        whenever(mockContext.assets).thenReturn(mockAssetManager)
        testee = RealBundledBrokerDataLoader(
            context = mockContext,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokerJsonProcessor = RealBrokerJsonProcessor(mockPirRepository, mockPixelSender),
        )
    }

    @Test
    fun whenNoBundledAssetsFoundThenDoNothing() = runTest {
        whenever(mockAssetManager.list("brokers")).thenReturn(emptyArray())

        testee.loadBundledBrokerData()

        verify(mockPirRepository, never()).updateBrokerData(any(), any())
    }

    @Test
    fun whenAssetListThrowsThenDoNothing() = runTest {
        whenever(mockAssetManager.list("brokers")).thenThrow(RuntimeException("IO error"))

        testee.loadBundledBrokerData()

        verify(mockPirRepository, never()).updateBrokerData(any(), any())
    }

    @Test
    fun whenOnlyNonJsonFilesFoundThenDoNothing() = runTest {
        whenever(mockAssetManager.list("brokers")).thenReturn(arrayOf("readme.txt", "notes.md"))

        testee.loadBundledBrokerData()

        verify(mockPirRepository, never()).updateBrokerData(any(), any())
    }

    @Test
    fun whenBrokerAtSameVersionThenSkipsProcessing() = runTest {
        whenever(mockAssetManager.list("brokers")).thenReturn(arrayOf("broker.json"))
        whenever(mockAssetManager.open("brokers/broker.json")).thenReturn(brokerJson("Broker One", "1.0").byteInputStream())
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(storedBroker("Broker One", "1.0"))

        testee.loadBundledBrokerData()

        verify(mockPirRepository, never()).updateBrokerData(any(), any())
        verify(mockPixelSender, never()).reportUpdateBrokerJsonSuccess(any(), any())
        verify(mockPixelSender, never()).reportUpdateBrokerJsonFailure(any(), any())
    }

    @Test
    fun whenBrokerVersionChangedThenProcessesBroker() = runTest {
        whenever(mockAssetManager.list("brokers")).thenReturn(arrayOf("broker.json"))
        whenever(mockAssetManager.open("brokers/broker.json")).thenReturn(brokerJson("Broker One", "2.0").byteInputStream())
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(storedBroker("Broker One", "1.0"))

        testee.loadBundledBrokerData()

        verify(mockPirRepository).updateBrokerData(eq("broker.json"), any())
        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker.json"), any())
    }

    @Test
    fun whenBrokerNotInStoreThenProcessesBroker() = runTest {
        whenever(mockAssetManager.list("brokers")).thenReturn(arrayOf("broker.json"))
        whenever(mockAssetManager.open("brokers/broker.json")).thenReturn(brokerJson("Broker One", "1.0").byteInputStream())
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(null)

        testee.loadBundledBrokerData()

        verify(mockPirRepository).updateBrokerData(eq("broker.json"), any())
        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker.json"), any())
    }

    @Test
    fun whenAssetOpenThrowsThenSkipsThatBrokerAndContinues() = runTest {
        whenever(mockAssetManager.list("brokers")).thenReturn(arrayOf("broker1.json", "broker2.json"))
        whenever(mockAssetManager.open("brokers/broker1.json")).thenThrow(RuntimeException("IO error"))
        whenever(mockAssetManager.open("brokers/broker2.json")).thenReturn(brokerJson("Broker Two", "1.0").byteInputStream())
        whenever(mockPirRepository.getBrokerForName("Broker Two")).thenReturn(null)

        testee.loadBundledBrokerData()

        verify(mockPirRepository, never()).updateBrokerData(eq("broker1.json"), any())
        verify(mockPirRepository).updateBrokerData(eq("broker2.json"), any())
    }

    @Test
    fun whenRemovedBrokerAtSameVersionThenSkipsProcessing() = runTest {
        val removedAt = 123456789L
        whenever(mockAssetManager.list("brokers")).thenReturn(arrayOf("broker.json"))
        whenever(mockAssetManager.open("brokers/broker.json")).thenReturn(brokerJson("Broker One", "1.0", removedAt = removedAt).byteInputStream())
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(storedBroker("Broker One", "1.0", removedAt = removedAt))

        testee.loadBundledBrokerData()

        verify(mockPirRepository, never()).updateBrokerData(any(), any())
    }

    @Test
    fun whenMultipleBrokersOnlyChangedOnesAreProcessed() = runTest {
        whenever(mockAssetManager.list("brokers")).thenReturn(arrayOf("broker1.json", "broker2.json"))
        whenever(mockAssetManager.open("brokers/broker1.json")).thenReturn(brokerJson("Broker One", "1.0").byteInputStream())
        whenever(mockAssetManager.open("brokers/broker2.json")).thenReturn(brokerJson("Broker Two", "2.0").byteInputStream())
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(storedBroker("Broker One", "1.0"))
        whenever(mockPirRepository.getBrokerForName("Broker Two")).thenReturn(storedBroker("Broker Two", "1.0"))

        testee.loadBundledBrokerData()

        verify(mockPirRepository, never()).updateBrokerData(eq("broker1.json"), any())
        verify(mockPirRepository).updateBrokerData(eq("broker2.json"), any())
    }

    // region helpers

    private fun brokerJson(
        name: String,
        version: String,
        removedAt: Long? = null,
    ): String =
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
        """.trimIndent()

    private fun storedBroker(
        name: String,
        version: String,
        removedAt: Long = 0L,
    ) = Broker(
        name = name,
        fileName = "broker.json",
        url = "https://broker.com",
        version = version,
        parent = null,
        addedDatetime = 1000L,
        removedAt = removedAt,
    )

    // endregion
}
