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

package com.duckduckgo.app.startup_metrics.impl.lifecycle

import android.app.Activity
import android.os.Bundle
import com.duckduckgo.app.startup_metrics.impl.StartupType
import com.duckduckgo.app.startup_metrics.impl.android.ProcessStartupTimeProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StartupTypeDetectionLifecycleObserverTest {
    private lateinit var observer: StartupTypeDetectionLifecycleObserver
    private lateinit var processStartupTimeProvider: ProcessStartupTimeProvider
    private lateinit var mockActivity: Activity
    private lateinit var mockActivity2: Activity

    @Before
    fun setup() {
        processStartupTimeProvider = mock()
        mockActivity = mock()
        mockActivity2 = mock()

        whenever(mockActivity.isChangingConfigurations).thenReturn(false)
        whenever(mockActivity2.isChangingConfigurations).thenReturn(false)

        observer = StartupTypeDetectionLifecycleObserver(processStartupTimeProvider)
    }

    @Test
    fun `when first activity created then detects COLD start`() {
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(100L)

        observer.onActivityCreated(mockActivity, null)

        assertEquals(StartupType.COLD, observer.getDetectedStartupType())
        verify(processStartupTimeProvider, never()).resetToCurrentTime()
    }

    @Test
    fun `when savedInstanceState is not null then still detects startup type correctly`() {
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(100L)
        val savedState: Bundle = mock()

        observer.onActivityCreated(mockActivity, savedState)

        assertEquals(StartupType.COLD, observer.getDetectedStartupType())
    }

    @Test
    fun `when COLD start then onCreate followed by onStart preserves COLD type`() {
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(100L)

        observer.onActivityCreated(mockActivity, null)
        observer.onActivityStarted(mockActivity)

        assertEquals(StartupType.COLD, observer.getDetectedStartupType())
    }

    @Test
    fun `when app backgrounded then foregrounded then detects WARM start`() {
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(100L)

        // Foreground the app
        observer.onActivityCreated(mockActivity, null)
        observer.onActivityStarted(mockActivity)

        // Assert
        assertEquals(StartupType.COLD, observer.getDetectedStartupType())

        // Background the app
        observer.onActivityStopped(mockActivity)
        observer.onActivityDestroyed(mockActivity)

        // Foreground again
        observer.onActivityCreated(mockActivity, null)

        // Assert
        assertEquals(StartupType.WARM, observer.getDetectedStartupType())
        verify(processStartupTimeProvider).resetToCurrentTime()
    }

    @Test
    fun `when activity resumed without onCreate then detects WARM start`() {
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(100L)
        observer.onActivityCreated(mockActivity, null)
        observer.onActivityStarted(mockActivity)

        // Activity goes to stopped (but not destroyed)
        observer.onActivityStopped(mockActivity)

        // Activity started without onCreate
        observer.onActivityStarted(mockActivity)

        // Assert - Pre-API 35: report as WARM (includes HOT)
        assertEquals(StartupType.WARM, observer.getDetectedStartupType())
        verify(processStartupTimeProvider).resetToCurrentTime()
    }

    @Test
    fun `when second activity created then does not reset baseline`() {
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(100L)
        observer.onActivityCreated(mockActivity, null)
        observer.onActivityStarted(mockActivity)

        // Act - Create second activity while first is still active
        observer.onActivityCreated(mockActivity2, null)

        // Assert - Baseline not reset (not first activity in session)
        assertEquals(StartupType.COLD, observer.getDetectedStartupType())
        verify(processStartupTimeProvider, never()).resetToCurrentTime()
    }

    @Test
    fun `when multiple activities then only first stopped does not background app`() {
        // Two activities running
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(100L)
        observer.onActivityCreated(mockActivity, null)
        observer.onActivityStarted(mockActivity)
        observer.onActivityCreated(mockActivity2, null)
        observer.onActivityStarted(mockActivity2)

        // Stop first activity (second still running)
        observer.onActivityStopped(mockActivity)

        // App not considered backgrounded (one activity still started)
        // Startup type remains COLD
        assertEquals(StartupType.COLD, observer.getDetectedStartupType())
        verify(processStartupTimeProvider, never()).resetToCurrentTime()
    }

    @Test
    fun `when multiple complete sessions then each session detected correctly`() {
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(100L)

        // Session 1: COLD
        observer.onActivityCreated(mockActivity, null)
        observer.onActivityStarted(mockActivity)
        assertEquals(StartupType.COLD, observer.getDetectedStartupType())
        observer.onActivityStopped(mockActivity)
        observer.onActivityDestroyed(mockActivity)

        // Session 2: WARM
        observer.onActivityCreated(mockActivity, null)
        observer.onActivityStarted(mockActivity)
        assertEquals(StartupType.WARM, observer.getDetectedStartupType())
        observer.onActivityStopped(mockActivity)
        observer.onActivityDestroyed(mockActivity)

        // Session 3: WARM
        observer.onActivityCreated(mockActivity, null)
        observer.onActivityStarted(mockActivity)
        assertEquals(StartupType.WARM, observer.getDetectedStartupType())
    }
}
