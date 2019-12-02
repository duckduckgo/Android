/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.referral

import com.duckduckgo.app.referral.ParsedReferrerResult.*
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * This is likely to be a fairly temporary class since it's being used to find out typical timings
 * of referrer data being made available.
 */
class DurationMeasuringReferrerRetriever(
    private val referrerStateListener: AppInstallationReferrerStateListener,
    private val durationBucketMapper: DurationBucketMapper,
    private val pixel: Pixel
) {

    suspend fun measureReferrerRetrieval() {
        val startTime = System.currentTimeMillis()
        val result = withTimeoutOrNull(MAX_WAIT_TIME_MS) {
            return@withTimeoutOrNull referrerStateListener.retrieveReferralCode()
        }

        // we are only interested in the the non-cached timings; i.e., the first time the app is opened.
        if (result?.fromCache == true) {
            Timber.v("Referrer result is from cache, so won't measure timing")
            return
        }

        val durationMs = System.currentTimeMillis() - startTime

        when (result) {
            is ReferrerFound -> sendPixelReferrerResultReceived(durationMs)
            is ReferrerNotFound -> sendPixelReferrerResultReceived(durationMs)
            is ParseFailure -> sendPixelParseError(result, durationMs)
            null -> sendPixelTimedOut(durationMs)
        }
    }

    private fun sendPixelReferrerResultReceived(durationMs: Long) {
        Timber.i("Retrieved referrer data; took ${durationMs}ms")
        val map = pixelMap(durationMs)
        pixel.fire(PixelName.APP_REFERRER_INFO_AVAILABLE, map)
    }

    private fun sendPixelParseError(result: ParseFailure, durationMs: Long) {
        Timber.w("Failed to retrieve referrer data due to ${result.reason}; took ${durationMs}ms")
        val map = pixelMap(durationMs)
        map[ERROR_KEY] = result.reason.toString()
        pixel.fire(PixelName.APP_REFERRER_INFO_UNAVAILABLE, map)
    }

    private fun sendPixelTimedOut(durationMs: Long) {
        Timber.i("Timed out waiting for referrer data after waiting ${durationMs}ms")
        val map = pixelMap(durationMs)
        pixel.fire(PixelName.APP_REFERRER_INFO_TIMEOUT, map)
    }

    private fun pixelMap(durationMs: Long): MutableMap<String, String> {
        return mutableMapOf(DURATION_KEY to durationBucketMapper.mapDurationToBucket(durationMs))
    }

    companion object {

        // if it hasn't appeared within this amount of time, there's no point in waiting any longer for it
        private const val MAX_WAIT_TIME_MS = 10_000L

        private const val DURATION_KEY = "d"
        private const val ERROR_KEY = "e"
    }
}