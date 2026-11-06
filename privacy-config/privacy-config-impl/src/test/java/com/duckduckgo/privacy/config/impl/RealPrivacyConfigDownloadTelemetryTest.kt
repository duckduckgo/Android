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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.CurrentTimeProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealPrivacyConfigDownloadTelemetryTest {

    private val pixel: Pixel = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock()

    private val testee = RealPrivacyConfigDownloadTelemetry(pixel, currentTimeProvider)

    @Test
    fun whenDownloadCompletesThenFirePixelWithBucketedDurations() {
        givenElapsedRealtimes(1_000L, 1_313L, 1_800L)

        testee.onDownloadStarted()
        testee.onDownloadFinished()
        testee.onProcessFinished()

        verify(pixel).fire(
            "privacy_config_downloaded",
            mapOf(
                "fetch_duration_ms_bucketed" to "300",
                "persist_duration_ms_bucketed" to "400",
                "total_duration_ms_bucketed" to "800",
            ),
        )
    }

    @Test
    fun whenDurationMatchesABucketBoundaryThenThatBucketIsUsed() {
        givenElapsedRealtimes(0L, 300L, 1_000L)

        testee.onDownloadStarted()
        testee.onDownloadFinished()
        testee.onProcessFinished()

        verify(pixel).fire(
            "privacy_config_downloaded",
            mapOf(
                "fetch_duration_ms_bucketed" to "300",
                "persist_duration_ms_bucketed" to "500",
                "total_duration_ms_bucketed" to "1000",
            ),
        )
    }

    @Test
    fun whenStagesAreFasterThanTheSmallestBucketThenZeroIsUsed() {
        givenElapsedRealtimes(0L, 20L, 40L)

        testee.onDownloadStarted()
        testee.onDownloadFinished()
        testee.onProcessFinished()

        verify(pixel).fire(
            "privacy_config_downloaded",
            mapOf(
                "fetch_duration_ms_bucketed" to "0",
                "persist_duration_ms_bucketed" to "0",
                "total_duration_ms_bucketed" to "0",
            ),
        )
    }

    @Test
    fun whenStagesExceedTheLargestBucketThenTopBucketIsUsed() {
        givenElapsedRealtimes(0L, 12_000L, 40_000L)

        testee.onDownloadStarted()
        testee.onDownloadFinished()
        testee.onProcessFinished()

        verify(pixel).fire(
            "privacy_config_downloaded",
            mapOf(
                "fetch_duration_ms_bucketed" to "10000",
                "persist_duration_ms_bucketed" to "10000",
                "total_duration_ms_bucketed" to "10000",
            ),
        )
    }

    private fun givenElapsedRealtimes(vararg values: Long) {
        whenever(currentTimeProvider.elapsedRealtime()).thenReturn(values.first(), *values.drop(1).toTypedArray())
    }
}
