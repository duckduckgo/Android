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
package com.duckduckgo.adblocking.impl.segmentskip

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SegmentSkipDatasetFreshnessTest {
    @get:Rule val coroutineRule = CoroutineTestRule()
    private val clock: CurrentTimeProvider = mock()
    private val binary: SegmentSkipBinaryStore = mock()
    private val dataStore = PreferenceDataStoreFactory.create(scope = coroutineRule.testScope) {
        File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "segment_skip_freshness_test.preferences_pb")
            .also { it.delete() }
    }
    private val freshness = SegmentSkipDatasetFreshness(dataStore, clock)

    @Test fun freshnessRequiresAValidDatasetAndRecentNonFutureCheck() = runTest {
        val day = TimeUnit.HOURS.toMillis(24)
        whenever(binary.revision()).thenReturn(1L)
        whenever(clock.currentTimeMillis()).thenReturn(day * 2)
        assertTrue(freshness.isStale(binary))
        freshness.recordSuccess()
        assertFalse(SegmentSkipDatasetFreshness(dataStore, clock).isStale(binary))
        whenever(clock.currentTimeMillis()).thenReturn(day * 3 - 1)
        assertFalse(freshness.isStale(binary))
        whenever(clock.currentTimeMillis()).thenReturn(day * 3)
        assertTrue(freshness.isStale(binary))
        whenever(clock.currentTimeMillis()).thenReturn(day)
        assertTrue(freshness.isStale(binary))
        whenever(clock.currentTimeMillis()).thenReturn(day * 2)
        whenever(binary.revision()).thenReturn(0L)
        assertTrue(freshness.isStale(binary))
    }
}
