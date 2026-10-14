/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.privacy.config.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat
import javax.inject.Inject

/**
 * Measures how long a privacy config download takes. Callers mark the stage boundaries, this owns the
 * clock and the reporting. Each download must start with [onDownloadStarted]; the instance holds the
 * timings of a single in-flight download.
 */
interface PrivacyConfigDownloadTelemetry {

    /** Starts the clock for a new download. Must be called before any other method on this instance. */
    fun onDownloadStarted()

    /** The remote config has been fetched and parsed. */
    fun onDownloadFinished()

    /** The fetched config has been stored and its callbacks notified. */
    fun onProcessFinished()

    /**
     * The download failed before a config could be fetched.
     * @param code the HTTP status code as a string, or "unknown" if the failure wasn't an HTTP error.
     */
    fun onDownloadFailed(code: String)
}

@ContributesBinding(AppScope::class)
class RealPrivacyConfigDownloadTelemetry @Inject constructor(
    private val pixel: Pixel,
    private val currentTimeProvider: CurrentTimeProvider,
) : PrivacyConfigDownloadTelemetry {

    private var startTimeMillis: Long = 0
    private var downloadFinishedTimeMillis: Long = 0

    override fun onDownloadStarted() {
        startTimeMillis = currentTimeProvider.elapsedRealtime()
        downloadFinishedTimeMillis = 0
    }

    override fun onDownloadFinished() {
        downloadFinishedTimeMillis = currentTimeProvider.elapsedRealtime()
    }

    override fun onProcessFinished() {
        val now = currentTimeProvider.elapsedRealtime()
        val fetchDuration = downloadFinishedTimeMillis - startTimeMillis
        val persistDuration = now - downloadFinishedTimeMillis
        val totalDuration = now - startTimeMillis

        logcat { "Privacy config downloaded: fetch=${fetchDuration}ms persist=${persistDuration}ms total=${totalDuration}ms" }

        val params = mapOf(
            PARAM_FETCH_DURATION to lowerBoundBucket(fetchDuration),
            PARAM_PERSIST_DURATION to lowerBoundBucket(persistDuration),
            PARAM_TOTAL_DURATION to lowerBoundBucket(totalDuration),
        )
        pixel.fire(PRIVACY_CONFIG_DOWNLOADED_PIXEL, params)
    }

    override fun onDownloadFailed(code: String) {
        val duration = currentTimeProvider.elapsedRealtime() - startTimeMillis
        logcat { "Privacy config download failed after ${duration}ms, code=$code" }
    }

    private fun lowerBoundBucket(durationMillis: Long): String =
        (DURATION_BUCKETS_MS.lastOrNull { it <= durationMillis } ?: 0L).toString()

    companion object {
        private const val PRIVACY_CONFIG_DOWNLOADED_PIXEL = "privacy_config_downloaded"
        private const val PARAM_FETCH_DURATION = "fetch_duration_ms_bucketed"
        private const val PARAM_PERSIST_DURATION = "persist_duration_ms_bucketed"
        private const val PARAM_TOTAL_DURATION = "total_duration_ms_bucketed"

        private val DURATION_BUCKETS_MS: List<Long> =
            listOf(0, 100, 200, 300, 400, 500, 800, 1000, 1500, 2000, 3000, 5000, 10000)
    }
}
