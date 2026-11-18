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

package com.duckduckgo.adblocking.impl.sponsorblock

import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.duckduckgo.adblocking.impl.BuildConfig
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SponsorBlockDatasetSchedulerTest {
    private val workManager: WorkManager = mock()
    private val feature: SponsorBlockFeature = mock()
    private val toggle: Toggle = mock()
    private val scheduler = SponsorBlockDatasetScheduler(workManager, feature)

    @Test
    fun whenEnabledOnStartupThenPeriodicWorkIsScheduled() {
        whenever(feature.mvpEnabled()).thenReturn(toggle)
        whenever(toggle.isEnabled()).thenReturn(true)

        scheduler.onCreate(mock<LifecycleOwner>())

        val request = argumentCaptor<PeriodicWorkRequest>()
        verify(workManager).enqueueUniquePeriodicWork(eq("sponsor_block_dataset"), eq(ExistingPeriodicWorkPolicy.UPDATE), request.capture())
        val expectedInterval = if (BuildConfig.SPONSOR_BLOCK_BASE_URL == "http://127.0.0.1:8765/sponsorblock/v1") {
            TimeUnit.MINUTES.toMillis(15)
        } else {
            TimeUnit.HOURS.toMillis(24)
        }
        assertEquals(expectedInterval, request.firstValue.workSpec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, request.firstValue.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.firstValue.workSpec.backoffPolicy)
        assertEquals(TimeUnit.MINUTES.toMillis(1), request.firstValue.workSpec.backoffDelayDuration)
    }

    @Test
    fun whenPrivacyConfigChangesThenSchedulingFollowsFeatureState() {
        whenever(feature.mvpEnabled()).thenReturn(toggle)
        whenever(toggle.isEnabled()).thenReturn(false, true, false)

        scheduler.onPrivacyConfigDownloaded()
        verify(workManager).cancelUniqueWork("sponsor_block_dataset")
        verify(workManager, never()).enqueueUniquePeriodicWork(any(), any(), any<PeriodicWorkRequest>())

        scheduler.onPrivacyConfigDownloaded()
        verify(workManager).enqueueUniquePeriodicWork(eq("sponsor_block_dataset"), eq(ExistingPeriodicWorkPolicy.UPDATE), any<PeriodicWorkRequest>())

        scheduler.onPrivacyConfigDownloaded()
        verify(workManager, org.mockito.kotlin.times(2)).cancelUniqueWork("sponsor_block_dataset")
    }
}
