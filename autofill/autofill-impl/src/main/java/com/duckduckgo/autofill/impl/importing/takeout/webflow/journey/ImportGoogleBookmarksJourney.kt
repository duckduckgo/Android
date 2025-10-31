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
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.DownloadError
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.ErrorParsingBookmarks
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.Unknown
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.WebAutomationError
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.WebViewError
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_CANCELLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_ERROR
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_SUCCESS
import com.duckduckgo.autofill.impl.time.TimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.logcat
import javax.inject.Inject

interface ImportGoogleBookmarksJourney {
    fun started(launchSource: String)
    fun startedWaitingForExport()
    fun stoppedWaitingForExport()
    fun finishedWithSuccess()
    fun finishedWithError(error: UserCannotImportReason)
    fun cancelled(stepReached: String)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealImportGoogleBookmarksJourney @Inject constructor(
    private val timeProvider: TimeProvider,
    private val pixel: Pixel,
    private val flowDurationBucketing: ImportGoogleBookmarksDurationBucketing,
) : ImportGoogleBookmarksJourney {

    private var wholeFlowStartTime: Long = 0
    private var totalWaitingDuration: Long = 0
    private var currentWaitingStartTime: Long? = null
    private var launchSource: String = "unknown"
    private var userIsWaitingOnExport: Boolean = false

    override fun started(launchSource: String) {
        logcat { "Bookmark-import: journey started from source: $launchSource" }
        this.launchSource = launchSource
        reset()

        val params = mapOf(LAUNCH_SOURCE_PARAM to launchSource)
        pixel.fire(pixel = AutofillPixelNames.BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_STARTED, params, type = Count)
    }

    private fun reset() {
        wholeFlowStartTime = timeProvider.currentTimeMillis()
        totalWaitingDuration = 0
        currentWaitingStartTime = null
        userIsWaitingOnExport = false
    }

    override fun startedWaitingForExport() {
        if (userIsWaitingOnExport) {
            logcat { "Bookmark-import: already waiting for export, no further action required" }
            return
        }
        userIsWaitingOnExport = true
        currentWaitingStartTime = timeProvider.currentTimeMillis()
        logcat { "Bookmark-import: user is now waiting for export" }
    }

    override fun stoppedWaitingForExport() {
        userIsWaitingOnExport = false
        currentWaitingStartTime?.let { startTime ->
            totalWaitingDuration += (timeProvider.currentTimeMillis() - startTime)
            currentWaitingStartTime = null
            logcat { "Bookmark-import: no longer waiting for export. total wait time: ${totalWaitingDuration}ms" }
        }
    }

    override fun finishedWithSuccess() {
        stoppedWaitingForExport()

        val durations = calculateDurations()
        logcat {
            "Bookmark-import: journey finished successfully, " +
                "source: $launchSource, " +
                "durationFullFlow: ${durations.fullFlow}, " +
                "durationWaitingForExport: ${durations.waitingForExport}"
        }

        val params = mapOf(
            LAUNCH_SOURCE_PARAM to launchSource,
            DURATION_FULL_FLOW_PARAM to flowDurationBucketing.bucket(durations.fullFlow),
            DURATION_WAITING_FOR_EXPORT_PARAM to flowDurationBucketing.bucket(durations.waitingForExport),
        )

        pixel.fire(pixel = BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_SUCCESS, params, type = Count)
    }

    override fun finishedWithError(error: UserCannotImportReason) {
        stoppedWaitingForExport()

        val durations = calculateDurations()
        val errorParam = error.mapToJourneyParam()

        logcat {
            "Bookmark-import: journey finished with error, " +
                "error: $errorParam, " +
                "source: $launchSource, " +
                "durationFullFlow: ${durations.fullFlow}, " +
                "durationWaitingForExport: ${durations.waitingForExport}"
        }

        val params = mapOf(
            LAUNCH_SOURCE_PARAM to launchSource,
            DURATION_FULL_FLOW_PARAM to flowDurationBucketing.bucket(durations.fullFlow),
            DURATION_WAITING_FOR_EXPORT_PARAM to flowDurationBucketing.bucket(durations.waitingForExport),
            ERROR_REASON_PARAM to errorParam,
        )

        pixel.fire(pixel = BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_ERROR, params, type = Count)
    }

    override fun cancelled(stepReached: String) {
        stoppedWaitingForExport()

        val durations = calculateDurations()
        logcat {
            "Bookmark-import: journey finished because of cancellation, " +
                "step: $stepReached, " +
                "source: $launchSource, " +
                "durationFullFlow: ${durations.fullFlow}, " +
                "durationWaitingForExport: ${durations.waitingForExport}"
        }

        val params = mapOf(
            LAUNCH_SOURCE_PARAM to launchSource,
            DURATION_FULL_FLOW_PARAM to flowDurationBucketing.bucket(durations.fullFlow),
            DURATION_WAITING_FOR_EXPORT_PARAM to flowDurationBucketing.bucket(durations.waitingForExport),
            STEP_REACHED_PARAM to stepReached,
        )

        pixel.fire(pixel = BOOKMARK_IMPORT_FROM_GOOGLE_FLOW_CANCELLED, params, type = Count)
    }

    private fun calculateDurations(): Durations {
        val durationFullFlow = (timeProvider.currentTimeMillis() - wholeFlowStartTime)
        val durationWaitingForExport = totalWaitingDuration
        return Durations(fullFlow = durationFullFlow, waitingForExport = durationWaitingForExport)
    }

    private fun UserCannotImportReason.mapToJourneyParam(): String {
        return when (this) {
            is DownloadError -> "downloadingExport"
            is ErrorParsingBookmarks -> "parsingBookmarks"
            is WebViewError -> "webView-${this.step}"
            is WebAutomationError -> "webAutomation-${this.step}"
            is Unknown -> "unknown"
        }
    }

    private data class Durations(
        val fullFlow: Long,
        val waitingForExport: Long,
    )

    companion object {
        private const val LAUNCH_SOURCE_PARAM = "launchSource"
        private const val DURATION_FULL_FLOW_PARAM = "durationFullFlow"
        private const val DURATION_WAITING_FOR_EXPORT_PARAM = "durationWaitingForExport"
        private const val STEP_REACHED_PARAM = "stepReached"
        private const val ERROR_REASON_PARAM = "errorReason"
    }
}
