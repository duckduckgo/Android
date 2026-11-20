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

import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SegmentSkipDatasetSchedulerTest {
    @get:Rule val coroutineRule = CoroutineTestRule()
    private val workManager: WorkManager = mock()
    private val feature: SegmentSkipFeature = mock()
    private val toggle: Toggle = mock()
    private val preferences: SegmentSkipUserPreferences = mock()
    private val freshness: SegmentSkipDatasetFreshness = mock()
    private val binary: SegmentSkipBinaryStore = mock()
    private val mode = MutableStateFlow(SegmentSkipMode.OFF)
    private val enabled = MutableStateFlow(true)
    private lateinit var scheduler: SegmentSkipDatasetScheduler

    @Before fun setup() {
        whenever(feature.mvpEnabled()).thenReturn(toggle)
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(toggle.enabled()).thenReturn(enabled)
        whenever(preferences.modeFlow()).thenReturn(mode)
        runBlocking { whenever(freshness.generation()).thenReturn(0L) }
        scheduler = SegmentSkipDatasetScheduler(workManager, feature, preferences, freshness, binary, coroutineRule.testScope)
    }

    private fun startScheduler() {
        scheduler.onCreate(mock<LifecycleOwner>())
        coroutineRule.testScope.testScheduler.runCurrent()
    }

    @Test fun enablingSchedulesConstrainedWorkAndSwitchingEnabledModesDoesNot() {
        startScheduler()
        mode.value = SegmentSkipMode.ALWAYS_ASK
        coroutineRule.testScope.testScheduler.runCurrent()
        val request = argumentCaptor<OneTimeWorkRequest>()
        verify(workManager).enqueueUniqueWork(eq("sponsor_dataset_enable"), eq(ExistingWorkPolicy.KEEP), request.capture())
        assertEquals(NetworkType.CONNECTED, request.firstValue.workSpec.constraints.requiredNetworkType)
        val periodic = argumentCaptor<PeriodicWorkRequest>()
        verify(workManager).enqueueUniquePeriodicWork(eq("sponsor_dataset"), eq(ExistingPeriodicWorkPolicy.UPDATE), periodic.capture())
        assertEquals(TimeUnit.HOURS.toMillis(1), periodic.firstValue.workSpec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, periodic.firstValue.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, periodic.firstValue.workSpec.backoffPolicy)
        assertEquals(TimeUnit.MINUTES.toMillis(1), periodic.firstValue.workSpec.backoffDelayDuration)
        mode.value = SegmentSkipMode.ALWAYS_SKIP
        coroutineRule.testScope.testScheduler.runCurrent()
        verify(workManager, times(1)).enqueueUniqueWork(eq("sponsor_dataset_enable"), any(), any<OneTimeWorkRequest>())
        mode.value = SegmentSkipMode.OFF
        coroutineRule.testScope.testScheduler.runCurrent()
        verify(workManager, times(2)).cancelUniqueWork("sponsor_dataset_enable")
        verify(workManager, times(2)).cancelUniqueWork("sponsor_dataset_catch_up")
    }

    @Test fun appOpenEnqueuesCatchUpOnlyWhenStaleAndEnabled() = runTest {
        startScheduler()
        whenever(freshness.isStale(binary)).thenReturn(true)
        scheduler.onStart(mock<LifecycleOwner>())
        coroutineRule.testScope.testScheduler.runCurrent()
        verify(workManager, never()).enqueueUniqueWork(eq("sponsor_dataset_catch_up"), any(), any<OneTimeWorkRequest>())
        mode.value = SegmentSkipMode.ALWAYS_ASK
        coroutineRule.testScope.testScheduler.runCurrent()
        scheduler.onStart(mock<LifecycleOwner>())
        coroutineRule.testScope.testScheduler.runCurrent()
        val request = argumentCaptor<OneTimeWorkRequest>()
        verify(workManager).enqueueUniqueWork(eq("sponsor_dataset_catch_up"), eq(ExistingWorkPolicy.KEEP), request.capture())
        assertEquals(NetworkType.CONNECTED, request.firstValue.workSpec.constraints.requiredNetworkType)
        whenever(freshness.isStale(binary)).thenReturn(false)
        scheduler.onStart(mock<LifecycleOwner>())
        coroutineRule.testScope.testScheduler.runCurrent()
        verify(workManager, times(1)).enqueueUniqueWork(eq("sponsor_dataset_catch_up"), any(), any<OneTimeWorkRequest>())
    }

    @Test fun storedEnabledModeSchedulesPeriodicAndStaleCatchUpAtStartup() = runTest {
        mode.value = SegmentSkipMode.ALWAYS_SKIP
        whenever(freshness.isStale(binary)).thenReturn(true)
        startScheduler()
        verify(workManager).enqueueUniquePeriodicWork(eq("sponsor_dataset"), eq(ExistingPeriodicWorkPolicy.UPDATE), any<PeriodicWorkRequest>())
        verify(workManager, never()).enqueueUniqueWork(eq("sponsor_dataset_enable"), any(), any<OneTimeWorkRequest>())
        scheduler.onStart(mock<LifecycleOwner>())
        coroutineRule.testScope.testScheduler.runCurrent()
        verify(workManager).enqueueUniqueWork(eq("sponsor_dataset_catch_up"), eq(ExistingWorkPolicy.KEEP), any<OneTimeWorkRequest>())
    }

    @Test fun featureFlagChangesCancelAndResumeWorkForStoredEnabledMode() {
        mode.value = SegmentSkipMode.ALWAYS_SKIP
        enabled.value = false
        whenever(toggle.isEnabled()).thenReturn(false)
        startScheduler()
        verify(workManager).cancelUniqueWork("sponsor_dataset")
        verify(workManager).cancelUniqueWork("sponsor_dataset_enable")
        verify(workManager).cancelUniqueWork("sponsor_dataset_catch_up")
        verify(workManager, never()).enqueueUniquePeriodicWork(any(), any(), any<PeriodicWorkRequest>())
        whenever(toggle.isEnabled()).thenReturn(true)
        enabled.value = true
        coroutineRule.testScope.testScheduler.runCurrent()
        verify(workManager).enqueueUniquePeriodicWork(eq("sponsor_dataset"), eq(ExistingPeriodicWorkPolicy.UPDATE), any<PeriodicWorkRequest>())
        verify(workManager).enqueueUniqueWork(eq("sponsor_dataset_enable"), eq(ExistingWorkPolicy.KEEP), any<OneTimeWorkRequest>())
        whenever(toggle.isEnabled()).thenReturn(false)
        enabled.value = false
        coroutineRule.testScope.testScheduler.runCurrent()
        verify(workManager, times(2)).cancelUniqueWork("sponsor_dataset")
        verify(workManager, times(2)).cancelUniqueWork("sponsor_dataset_enable")
        verify(workManager, times(2)).cancelUniqueWork("sponsor_dataset_catch_up")
    }
}
