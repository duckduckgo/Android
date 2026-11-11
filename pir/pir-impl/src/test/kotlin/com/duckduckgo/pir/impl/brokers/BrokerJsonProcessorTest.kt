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
import com.duckduckgo.pir.impl.PirUserUtils
import com.duckduckgo.pir.impl.brokers.BrokerJsonUpdateFailureReason.INVALID_JSON
import com.duckduckgo.pir.impl.brokers.BrokerJsonUpdateFailureReason.STORAGE_ERROR
import com.duckduckgo.pir.impl.brokers.BrokerJsonUpdateFailureReason.UNSUPPORTED_ACTION
import com.duckduckgo.pir.impl.di.PirModule
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BrokerJsonProcessorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockPirRepository: PirRepository = mock()
    private val mockPixelSender: PirPixelSender = mock()
    private val mockBrokerStepsValidator: BrokerStepsValidator = mock()
    private val mockPirUserUtils: PirUserUtils = mock {
        onBlocking { hasPirSubscription() } doReturn true
    }

    private lateinit var testee: BrokerJsonProcessor

    @Before
    fun setUp() {
        testee = RealBrokerJsonProcessor(mockPirRepository, mockPixelSender, mockPirUserUtils, mockBrokerStepsValidator)
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

        verify(mockPixelSender).reportUpdateBrokerJsonFailure(eq("broker.json"), eq(0L), anyOrNull(), eq(INVALID_JSON))
        verify(mockPirRepository, never()).updateBrokerData(any(), any())
    }

    @Test
    fun whenStoredVersionMatchesThenSkipsStoreAndEmitsNoPixels() = runTest {
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(storedBroker(version = "1.0"))

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0"))

        verify(mockPirRepository, never()).updateBrokerData(any(), any())
        verify(mockPixelSender, never()).reportUpdateBrokerJsonSuccess(any(), any(), anyOrNull())
        verify(mockPixelSender, never()).reportUpdateBrokerJsonFailure(any(), any(), anyOrNull(), any())
    }

    @Test
    fun whenVersionChangedThenStoresAndReportsSuccessPixel() = runTest {
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(storedBroker(version = "1.0"))

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "2.0"))

        verify(mockPirRepository).updateBrokerData(eq("broker.json"), any())
        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker.json"), eq(0L), anyOrNull())
    }

    @Test
    fun whenBrokerNotPreviouslyStoredThenStoresAndReportsSuccessPixel() = runTest {
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(null)

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0"))

        verify(mockPirRepository).updateBrokerData(eq("broker.json"), any())
        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker.json"), eq(0L), anyOrNull())
    }

    @Test
    fun whenGetBrokerForNameThrowsThenReportsFailurePixelWithoutRethrow() = runTest {
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenThrow(RuntimeException("DB error"))

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0"))

        verify(mockPixelSender).reportUpdateBrokerJsonFailure(eq("broker.json"), eq(0L), anyOrNull(), eq(STORAGE_ERROR))
        verify(mockPirRepository, never()).updateBrokerData(any(), any())
    }

    @Test
    fun whenUpdateBrokerDataThrowsThenReportsFailurePixelWithoutRethrow() = runTest {
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(null)
        whenever(mockPirRepository.updateBrokerData(any(), any())).thenThrow(RuntimeException("DB error"))

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0"))

        verify(mockPixelSender).reportUpdateBrokerJsonFailure(eq("broker.json"), eq(0L), anyOrNull(), eq(STORAGE_ERROR))
    }

    @Test
    fun whenBrokerHasRemovedAtThenPassesRemovedAtToSuccessPixel() = runTest {
        val removedAt = 123456789L
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(null)

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0", removedAt = removedAt))

        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker.json"), eq(removedAt), anyOrNull())
    }

    @Test
    fun whenBrokerHasRemovedAtAndStoreThrowsThenPassesRemovedAtToFailurePixel() = runTest {
        val removedAt = 123456789L
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(null)
        whenever(mockPirRepository.updateBrokerData(any(), any())).thenThrow(RuntimeException("DB error"))

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0", removedAt = removedAt))

        verify(mockPixelSender).reportUpdateBrokerJsonFailure(eq("broker.json"), eq(removedAt), anyOrNull(), eq(STORAGE_ERROR))
    }

    @Test
    fun whenFreeUserUpdatesBrokerThenSuccessPixelReportsFreeScan() = runTest {
        whenever(mockPirUserUtils.hasPirSubscription()).thenReturn(false)
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(null)

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0"))

        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker.json"), eq(0L), eq(true))
    }

    @Test
    fun whenPaidUserUpdateFailsThenFailurePixelReportsPaid() = runTest {
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenThrow(RuntimeException("DB error"))

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0"))

        verify(mockPixelSender).reportUpdateBrokerJsonFailure(eq("broker.json"), eq(0L), eq(false), eq(STORAGE_ERROR))
    }

    @Test
    fun whenSubscriptionReadFailsThenBrokerIsStoredAndPixelOmitsTier() = runTest {
        whenever(mockPirUserUtils.hasPirSubscription()).thenThrow(RuntimeException("keystore"))
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(null)

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0"))

        verify(mockPirRepository).updateBrokerData(eq("broker.json"), any())
        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker.json"), eq(0L), isNull())
    }

    @Test
    fun whenSubscriptionReadThrowsErrorThenBrokerIsStoredAndPixelOmitsTier() = runTest {
        whenever(mockPirUserUtils.hasPirSubscription()).thenThrow(ExceptionInInitializerError("crypto provider"))
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(null)

        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0"))

        verify(mockPirRepository).updateBrokerData(eq("broker.json"), any())
        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker.json"), eq(0L), isNull())
    }

    @Test
    fun whenBrokerIsStoredThenReturnsTrue() = runTest {
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(null)

        assertTrue(testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0")))
    }

    @Test
    fun whenStoredVersionMatchesThenReturnsTrue() = runTest {
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(storedBroker(version = "1.0"))

        assertTrue(testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0")))
    }

    @Test
    fun whenInvalidJsonThenReturnsFalse() = runTest {
        assertFalse(testee.processAndStoreBroker("broker.json", Buffer().writeUtf8("{ not valid json }")))
    }

    @Test
    fun whenStoringFailsThenReturnsFalse() = runTest {
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(null)
        whenever(mockPirRepository.updateBrokerData(any(), any())).thenThrow(RuntimeException("DB error"))

        assertFalse(testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0")))
    }

    @Test
    fun whenValidatingThenValidatorReceivesBrokerSteps() = runTest {
        testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "1.0", steps = VALID_STEPS))

        verify(mockBrokerStepsValidator).validate(listOf(VALID_SCAN_STEP, VALID_OPT_OUT_STEP))
    }

    @Test
    fun whenValidatorRejectsStepsThenKeepsStoredBrokerAndReportsReason() = runTest {
        val removedAt = 123456789L
        whenever(mockBrokerStepsValidator.validate(any())).thenReturn(UNSUPPORTED_ACTION)

        val result = testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "2.0", removedAt = removedAt))

        assertFalse(result)
        verify(mockPirRepository, never()).getBrokerForName(any())
        verify(mockPirRepository, never()).updateBrokerData(any(), any())
        verify(mockPixelSender, never()).reportUpdateBrokerJsonSuccess(any(), any(), anyOrNull())
        verify(mockPixelSender).reportUpdateBrokerJsonFailure(eq("broker.json"), eq(removedAt), eq(false), eq(UNSUPPORTED_ACTION))
    }

    @Test
    fun whenBrokerJsonHasUnsupportedActionThenKeepsStoredBroker() = runTest {
        val testee = RealBrokerJsonProcessor(mockPirRepository, mockPixelSender, mockPirUserUtils, realBrokerStepsValidator())
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(storedBroker(version = "1.0"))
        val unsupportedScanStep = """{"stepType":"scan","scanType":"templatedUrl","actions":[{"actionType":"newAction","id":"a1"}]}"""

        val result = testee.processAndStoreBroker(
            "broker.json",
            brokerJsonSource("Broker One", "2.0", steps = "[$unsupportedScanStep, $VALID_OPT_OUT_STEP]"),
        )

        assertFalse(result)
        verify(mockPirRepository, never()).updateBrokerData(any(), any())
        verify(mockPixelSender).reportUpdateBrokerJsonFailure(eq("broker.json"), eq(0L), eq(false), eq(UNSUPPORTED_ACTION))
    }

    @Test
    fun whenBrokerJsonHasOnlySupportedActionsThenStoresIt() = runTest {
        val testee = RealBrokerJsonProcessor(mockPirRepository, mockPixelSender, mockPirUserUtils, realBrokerStepsValidator())
        whenever(mockPirRepository.getBrokerForName("Broker One")).thenReturn(storedBroker(version = "1.0"))

        val result = testee.processAndStoreBroker("broker.json", brokerJsonSource("Broker One", "2.0", steps = VALID_STEPS))

        assertTrue(result)
        verify(mockPirRepository).updateBrokerData(eq("broker.json"), any())
        verify(mockPixelSender).reportUpdateBrokerJsonSuccess(eq("broker.json"), eq(0L), eq(false))
    }

    // endregion

    // region helpers

    private fun realBrokerStepsValidator() = RealBrokerStepsValidator(PirModule().providePirMoshi(Moshi.Builder().build()))

    private fun brokerJsonSource(
        name: String,
        version: String,
        removedAt: Long? = null,
        steps: String = "[]",
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
                "steps": $steps,
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

    companion object {
        private const val VALID_SCAN_STEP =
            """{"stepType":"scan","scanType":"templatedUrl","actions":[{"actionType":"navigate","id":"n1","url":"https://broker.com/search"}]}"""
        private const val VALID_OPT_OUT_STEP =
            """{"stepType":"optOut","optOutType":"formOptOut","actions":[{"actionType":"emailConfirmation","id":"c1","pollingTime":"30"}]}"""
        private const val VALID_STEPS = "[$VALID_SCAN_STEP, $VALID_OPT_OUT_STEP]"
    }
}
