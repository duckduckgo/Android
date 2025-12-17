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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.service.DbpService
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RealBrokerDataDownloaderTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testee: RealBrokerDataDownloader
    private val mockDbpService: DbpService = mock()
    private val mockPirRepository: PirRepository = mock()
    private val mockContext: Context = mock()
    private val mockPixelSender: PirPixelSender = mock()

    @Before
    fun setUp() {
        whenever(mockContext.filesDir).thenReturn(tempFolder.root)

        testee = RealBrokerDataDownloader(
            dbpService = mockDbpService,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            pirRepository = mockPirRepository,
            context = mockContext,
            pirPixelSender = mockPixelSender,
        )
    }

    @Test
    fun whenDownloadBrokerDataWithEmptyListThenDoNothing() = runTest {
        val brokersToUpdate = emptyList<String>()

        testee.downloadBrokerData(brokersToUpdate)

        verify(mockDbpService, never()).getBrokerJsonFiles()
        verify(mockPirRepository, never()).updateBrokerData(any(), any())
    }

    @Test
    fun whenDownloadBrokerDataWithValidZipThenUpdateRepository() = runTest {
        val brokersToUpdate = listOf("broker1.json", "broker2.json")
        val broker1Json = """
            {
                "name": "Broker One",
                "url": "https://broker1.com",
                "version": "1.0",
                "parent": null,
                "addedDatetime": 1000,
                "optOutUrl": "https://broker1.com/optout",
                "steps": [],
                "schedulingConfig": {
                    "retryError": 24,
                    "confirmOptOutScan": 7,
                    "maintenanceScan": 30,
                    "maxAttempts": 3
                },
                "removedAt": null
            }
        """.trimIndent()
        val broker2Json = """
            {
                "name": "Broker Two",
                "url": "https://broker2.com",
                "version": "2.0",
                "parent": null,
                "addedDatetime": 2000,
                "optOutUrl": "https://broker2.com/optout",
                "steps": [],
                "schedulingConfig": {
                    "retryError": 24,
                    "confirmOptOutScan": 7,
                    "maintenanceScan": 30,
                    "maxAttempts": 3
                },
                "removedAt": null
            }
        """.trimIndent()

        val zipBytes = createZipWithFiles(
            mapOf(
                "data/broker1.json" to broker1Json,
                "data/broker2.json" to broker2Json,
            ),
        )
        val responseBody = zipBytes.toResponseBody()

        whenever(mockDbpService.getBrokerJsonFiles()).thenReturn(responseBody)

        testee.downloadBrokerData(brokersToUpdate)

        verify(mockDbpService).getBrokerJsonFiles()
        verify(mockPirRepository).updateBrokerData(eq("broker1.json"), any())
        verify(mockPirRepository).updateBrokerData(eq("broker2.json"), any())
        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker1.json"), any())
        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker2.json"), any())
    }

    @Test
    fun whenDownloadBrokerDataWithMalformedJsonThenReportFailure() = runTest {
        val brokersToUpdate = listOf("broker1.json")
        val malformedJson = """{ "invalid": json }"""

        val zipBytes = createZipWithFiles(
            mapOf("data/broker1.json" to malformedJson),
        )
        val responseBody = zipBytes.toResponseBody()

        whenever(mockDbpService.getBrokerJsonFiles()).thenReturn(responseBody)

        testee.downloadBrokerData(brokersToUpdate)

        verify(mockDbpService).getBrokerJsonFiles()
        verify(mockPirRepository, never()).updateBrokerData(eq("broker1.json"), any())
        verify(mockPixelSender).reportUpdateBrokerJsonFailure(eq("broker1.json"), eq(0L))
    }

    @Test
    fun whenDownloadBrokerDataWithPartialMatchThenOnlyUpdateMatchingBrokers() = runTest {
        val brokersToUpdate = listOf("broker1.json")
        val broker1Json = """
            {
                "name": "Broker One",
                "url": "https://broker1.com",
                "version": "1.0",
                "parent": null,
                "addedDatetime": 1000,
                "optOutUrl": "https://broker1.com/optout",
                "steps": [],
                "schedulingConfig": {
                    "retryError": 24,
                    "confirmOptOutScan": 7,
                    "maintenanceScan": 30,
                    "maxAttempts": 3
                },
                "removedAt": null
            }
        """.trimIndent()
        val broker2Json = """
            {
                "name": "Broker Two",
                "url": "https://broker2.com",
                "version": "2.0",
                "parent": null,
                "addedDatetime": 2000,
                "optOutUrl": "https://broker2.com/optout",
                "steps": [],
                "schedulingConfig": {
                    "retryError": 24,
                    "confirmOptOutScan": 7,
                    "maintenanceScan": 30,
                    "maxAttempts": 3
                },
                "removedAt": null
            }
        """.trimIndent()

        val zipBytes = createZipWithFiles(
            mapOf(
                "data/broker1.json" to broker1Json,
                "data/broker2.json" to broker2Json,
            ),
        )
        val responseBody = zipBytes.toResponseBody()

        whenever(mockDbpService.getBrokerJsonFiles()).thenReturn(responseBody)

        testee.downloadBrokerData(brokersToUpdate)

        verify(mockPirRepository).updateBrokerData(eq("broker1.json"), any())
        verify(mockPirRepository, never()).updateBrokerData(eq("broker2.json"), any())
        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker1.json"), any())
        verify(mockPixelSender, never()).reportUpdateBrokerJsonSuccess(eq("broker2.json"), any())
    }

    @Test
    fun whenDownloadBrokerDataAndRepositoryThrowsExceptionThenReportFailure() = runTest {
        val brokersToUpdate = listOf("broker1.json")
        val broker1Json = """
            {
                "name": "Broker One",
                "url": "https://broker1.com",
                "version": "1.0",
                "parent": null,
                "addedDatetime": 1000,
                "optOutUrl": "https://broker1.com/optout",
                "steps": [],
                "schedulingConfig": {
                    "retryError": 24,
                    "confirmOptOutScan": 7,
                    "maintenanceScan": 30,
                    "maxAttempts": 3
                },
                "removedAt": null
            }
        """.trimIndent()

        val zipBytes = createZipWithFiles(
            mapOf("data/broker1.json" to broker1Json),
        )
        val responseBody = zipBytes.toResponseBody()

        whenever(mockDbpService.getBrokerJsonFiles()).thenReturn(responseBody)
        whenever(mockPirRepository.updateBrokerData(any(), any())).thenThrow(RuntimeException("Database error"))

        testee.downloadBrokerData(brokersToUpdate)

        verify(mockPirRepository).updateBrokerData(eq("broker1.json"), any())
        verify(mockPixelSender).reportUpdateBrokerJsonFailure(eq("broker1.json"), any())
    }

    @Test
    fun whenDownloadBrokerDataWithBrokerHavingRemovedAtThenReportCorrectRemovedAt() = runTest {
        val brokersToUpdate = listOf("broker1.json")
        val removedAtMs = 123456789L
        val broker1Json = """
            {
                "name": "Broker One",
                "url": "https://broker1.com",
                "version": "1.0",
                "parent": null,
                "addedDatetime": 1000,
                "optOutUrl": "https://broker1.com/optout",
                "steps": [],
                "schedulingConfig": {
                    "retryError": 24,
                    "confirmOptOutScan": 7,
                    "maintenanceScan": 30,
                    "maxAttempts": 3
                },
                "removedAt": $removedAtMs
            }
        """.trimIndent()

        val zipBytes = createZipWithFiles(
            mapOf("data/broker1.json" to broker1Json),
        )
        val responseBody = zipBytes.toResponseBody()

        whenever(mockDbpService.getBrokerJsonFiles()).thenReturn(responseBody)

        testee.downloadBrokerData(brokersToUpdate)

        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker1.json"), eq(removedAtMs))
    }

    private fun createZipWithFiles(files: Map<String, String>): ByteArray {
        val outputStream = ByteArrayOutputStream()
        ZipOutputStream(outputStream).use { zipOut ->
            files.forEach { (path, content) ->
                val entry = ZipEntry(path)
                zipOut.putNextEntry(entry)
                zipOut.write(content.toByteArray())
                zipOut.closeEntry()
            }
        }
        return outputStream.toByteArray()
    }
}
