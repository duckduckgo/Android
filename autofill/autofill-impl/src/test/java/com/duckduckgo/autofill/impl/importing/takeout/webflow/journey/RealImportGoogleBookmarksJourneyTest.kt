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

package com.duckduckgo.autofill.impl.importing.takeout.webflow.journey

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.WebAutomationError
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.WebViewError
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_CANCELLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_ERROR
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_STARTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_SUCCESS
import com.duckduckgo.autofill.impl.time.TimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealImportGoogleBookmarksJourneyTest {

    private val mockTimeProvider: TimeProvider = mock()
    private val pixel: Pixel = mock()

    private val durationCaptor = argumentCaptor<Long>()
    private val pixelParamsCaptor = argumentCaptor<Map<String, String>>()
    private val flowDurationBucketing: ImportGoogleBookmarksDurationBucketing = mock()

    private val testee = RealImportGoogleBookmarksJourney(
        timeProvider = mockTimeProvider,
        pixel = pixel,
        flowDurationBucketing = flowDurationBucketing,
    )

    @Before
    fun setup() {
        whenever(flowDurationBucketing.bucket(anyOrNull())).thenReturn(FULL_FLOW_BUCKET, WAITING_FOR_EXPORT_BUCKET)
        whenever(mockTimeProvider.currentTimeMillis()).thenReturn(0L)
    }

    @Test
    fun whenStartedThenPixelIsCorrect() = runTest {
        testee.started(LAUNCH_SOURCE)

        verifyPixelFired(BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_STARTED)
        val params = pixelParamsCaptor.firstValue
        assertEquals(LAUNCH_SOURCE, params[LAUNCH_SOURCE_PARAM])
    }

    @Test
    fun whenFinishedSuccessfullyThenFullFlowDurationCorrect() = runTest {
        startJourney()
        atTime(5_000) { testee.finishedWithSuccess() }
        assertEquals(5_000, captureDurations().fullFlow)
    }

    @Test
    fun whenFinishedWithErrorThenFullFlowDurationCorrect() = runTest {
        startJourney()
        atTime(100) { testee.finishedWithError(WebViewError(A_STEP)) }
        assertEquals(100, captureDurations().fullFlow)
    }

    @Test
    fun whenFinishedWithCancellationThenFullFlowDurationCorrect() = runTest {
        startJourney()
        atTime(200) { testee.cancelled(A_STEP) }
        assertEquals(200, captureDurations().fullFlow)
    }

    @Test
    fun whenRestartedThenFullFlowDurationUsesRestartTimeCorrectly() = runTest {
        startJourney()
        atTime(100) { startJourney() } // restart
        atTime(400) { testee.finishedWithSuccess() }
        assertEquals(300, captureDurations().fullFlow)
    }

    @Test
    fun whenFinishedWithNoWaitingForExportTimeThenDurationIs0() = runTest {
        startJourney()
        testee.finishedWithSuccess()
        assertEquals(0L, captureDurations().waitingForExport)
    }

    @Test
    fun whenFinishedWithSingleWaitingForExportPeriodThenDurationIsCorrect() = runTest {
        startJourney()

        testee.startedWaitingForExport() // start waiting
        atTime(2_500) { testee.stoppedWaitingForExport() } // stop waiting

        testee.finishedWithSuccess()
        assertEquals(2_500, captureDurations().waitingForExport)
    }

    @Test
    fun whenFinishedWithMultipleWaitingForExportPeriodsThenDurationIsCorrectlySummed() = runTest {
        startJourney()

        atTime(0) { testee.startedWaitingForExport() } // start waiting
        atTime(100) { testee.stoppedWaitingForExport() } // stop waiting after 100ms

        atTime(2_000) { testee.startedWaitingForExport() } // start waiting again
        atTime(3_000) { testee.stoppedWaitingForExport() } // stop waiting after 1s

        testee.finishedWithSuccess()
        assertEquals(1_100, captureDurations().waitingForExport)
    }

    @Test
    fun whenStartedWaitingForExportCalledMultipleTimesWithoutStoppingThenOnlyFirstCallStartsWaiting() = runTest {
        startJourney()

        atTime(0) { testee.startedWaitingForExport() } // first call, should start waiting
        atTime(100) { testee.startedWaitingForExport() } // second call, should be ignored
        atTime(200) { testee.startedWaitingForExport() } // third call, should be ignored
        atTime(500) { testee.stoppedWaitingForExport() } // stop waiting after 500ms

        testee.finishedWithSuccess()
        assertEquals(500, captureDurations().waitingForExport)
    }

    @Test
    fun whenStoppedWaitingForExportCalledWithoutPriorNowWaitingThenDurationRemainsZero() = runTest {
        startJourney()

        atTime(100) { testee.stoppedWaitingForExport() } // called without starting waiting

        testee.finishedWithSuccess()
        assertEquals(0L, captureDurations().waitingForExport)
    }

    @Test
    fun whenFinishedWithSuccessThenSuccessPixelFiredWithCorrectParameters() = runTest {
        startJourney()
        testee.finishedWithSuccess()

        verifyPixelFired(BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_SUCCESS)

        val params = pixelParamsCaptor.firstValue
        assertEquals(LAUNCH_SOURCE, params[LAUNCH_SOURCE_PARAM])
        assertEquals(FULL_FLOW_BUCKET, params[DURATION_FULL_FLOW_PARAM])
        assertEquals(WAITING_FOR_EXPORT_BUCKET, params[DURATION_WAITING_FOR_EXPORT_PARAM])
    }

    @Test
    fun whenFinishedWithCancellationThenCancelledPixelFiredWithCorrectParameters() = runTest {
        startJourney()
        testee.cancelled(A_STEP)

        verifyPixelFired(BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_CANCELLED)

        val params = pixelParamsCaptor.firstValue
        assertEquals(LAUNCH_SOURCE, params[LAUNCH_SOURCE_PARAM])
        assertEquals(FULL_FLOW_BUCKET, params[DURATION_FULL_FLOW_PARAM])
        assertEquals(WAITING_FOR_EXPORT_BUCKET, params[DURATION_WAITING_FOR_EXPORT_PARAM])
        assertEquals(A_STEP, params[STEP_REACHED_PARAM])
    }

    @Test
    fun whenFinishedWithErrorThenErrorPixelFiredWithCorrectParameters() = runTest {
        startJourney()
        testee.finishedWithError(AN_ERROR_REASON)

        verifyPixelFired(BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_ERROR)

        val params = pixelParamsCaptor.firstValue
        assertEquals(LAUNCH_SOURCE, params[LAUNCH_SOURCE_PARAM])
        assertEquals(FULL_FLOW_BUCKET, params[DURATION_FULL_FLOW_PARAM])
        assertEquals(WAITING_FOR_EXPORT_BUCKET, params[DURATION_WAITING_FOR_EXPORT_PARAM])
        assertEquals(AN_ERROR_REASON_PARAM, params[ERROR_REASON_PARAM])
    }

    @Test
    fun whenFinishedWithWebAutomationErrorThenErrorPixelFiredWithCorrectParameters() = runTest {
        startJourney()
        testee.finishedWithError(WebAutomationError(A_STEP))

        verifyPixelFired(BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_ERROR)

        val params = pixelParamsCaptor.firstValue
        assertEquals("${ERROR_REASON_AUTOMATION_PARAM}-${A_STEP}", params[ERROR_REASON_PARAM])
    }

    private fun verifyPixelFired(pixelName: Pixel.PixelName) {
        verify(pixel).fire(
            pixel = eq(pixelName),
            parameters = pixelParamsCaptor.capture(),
            encodedParameters = anyOrNull(),
            type = eq(Count),
        )
    }

    fun startJourney() {
        testee.started(LAUNCH_SOURCE)
    }

    private fun captureDurations(): Durations {
        verify(flowDurationBucketing, times(2)).bucket(durationCaptor.capture())

        val fullFlowDuration = durationCaptor.allValues[0]
        val waitingForExportDuration = durationCaptor.allValues[1]
        return Durations(fullFlow = fullFlowDuration, waitingForExport = waitingForExportDuration)
    }

    private fun atTime(
        time: Long,
        function: () -> Unit,
    ) {
        whenever(mockTimeProvider.currentTimeMillis()).thenReturn(time)
        function()
    }

    private data class Durations(
        val fullFlow: Long,
        val waitingForExport: Long,
    )

    private companion object {
        private const val FULL_FLOW_BUCKET = "full-flow-bucket"
        private const val WAITING_FOR_EXPORT_BUCKET = "waiting-for-export-bucket"
        private const val LAUNCH_SOURCE = "testLaunchSource"
        private const val LAUNCH_SOURCE_PARAM = "launchSource"
        private const val DURATION_FULL_FLOW_PARAM = "durationFullFlow"
        private const val DURATION_WAITING_FOR_EXPORT_PARAM = "durationWaitingForExport"
        private const val STEP_REACHED_PARAM = "stepReached"
        private const val ERROR_REASON_PARAM = "errorReason"
        private val AN_ERROR_REASON = UserCannotImportReason.ErrorParsingBookmarks
        private const val AN_ERROR_REASON_PARAM = "parsingBookmarks"
        private const val ERROR_REASON_AUTOMATION_PARAM = "webAutomation"

        private const val A_STEP = "aStep"
    }
}
