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

package com.duckduckgo.duckchat.impl.responding

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.responding.RespondingServiceStartFailure.FOREGROUND_FAILED
import com.duckduckgo.duckchat.impl.responding.RespondingServiceStartFailure.FOREGROUND_NOT_ALLOWED
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class DuckAiRespondingServiceTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val keepServiceRunningFlow = MutableStateFlow(true)
    private val supervisor: RespondingServiceSupervisor = mock {
        on { keepServiceRunning } doReturn keepServiceRunningFlow
    }
    private val appBuildConfig: AppBuildConfig = mock()

    // built without onCreate, which would run AndroidInjection
    private val service = Robolectric.buildService(DuckAiRespondingService::class.java).get()

    @Before
    fun setUp() {
        whenever(appBuildConfig.sdkInt).thenReturn(34)
        service.supervisor = supervisor
        service.appBuildConfig = appBuildConfig
        service.dispatcherProvider = coroutineRule.testDispatcherProvider
    }

    @Test
    fun `when started then enters foreground with progress notification`() {
        service.onStartCommand(null, 0, 1)
        advance()

        assertEquals(9200, shadowOf(service).lastForegroundNotificationId)
        assertFalse(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `when keepServiceRunning false at start then enters foreground before stopping itself`() {
        keepServiceRunningFlow.value = false

        service.onStartCommand(null, 0, 1)
        assertEquals(9200, shadowOf(service).lastForegroundNotificationId)
        assertFalse(shadowOf(service).isStoppedBySelf)
        advance()

        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `when keepServiceRunning turns false later then stops itself`() {
        service.onStartCommand(null, 0, 1)
        advance()

        keepServiceRunningFlow.value = false
        advance()

        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `when repeat start refused after earlier start then reports without stopping`() {
        service.onStartCommand(null, 0, 1)
        advance()
        whenever(appBuildConfig.sdkInt).thenThrow(IllegalStateException())

        service.onStartCommand(null, 0, 2)
        advance()

        verify(supervisor).onStartFailed(FOREGROUND_NOT_ALLOWED)
        assertFalse(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `when repeat start arrives after keepServiceRunning turned false then stops with its startId`() {
        service.onStartCommand(null, 0, 1)
        advance()
        keepServiceRunningFlow.value = false

        service.onStartCommand(null, 0, 2)

        assertTrue(shadowOf(service).isStoppedBySelf)
        assertEquals(2, shadowOf(service).stopSelfId)
    }

    @Test
    fun `when repeat start refused then later stop uses its startId`() {
        service.onStartCommand(null, 0, 1)
        advance()
        whenever(appBuildConfig.sdkInt).thenThrow(IllegalStateException())
        service.onStartCommand(null, 0, 2)

        keepServiceRunningFlow.value = false
        advance()

        assertEquals(2, shadowOf(service).stopSelfId)
    }

    @Test
    fun `when startForeground throws IllegalStateException then reports not allowed and stops`() {
        whenever(appBuildConfig.sdkInt).thenThrow(IllegalStateException())

        service.onStartCommand(null, 0, 1)

        verify(supervisor).onStartFailed(FOREGROUND_NOT_ALLOWED)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `when startForeground throws other exception then reports failed and stops`() {
        whenever(appBuildConfig.sdkInt).thenThrow(RuntimeException())

        service.onStartCommand(null, 0, 1)

        verify(supervisor).onStartFailed(FOREGROUND_FAILED)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `when notification setup throws then still enters foreground without stopping`() {
        val failingService = spy(service)
        doThrow(RuntimeException()).whenever(failingService).getSystemService(Context.NOTIFICATION_SERVICE)

        failingService.onStartCommand(null, 0, 1)
        advance()

        assertEquals(9200, shadowOf(failingService).lastForegroundNotificationId)
        assertFalse(shadowOf(failingService).isStoppedBySelf)
        verify(supervisor, never()).onStartFailed(any())
    }

    @Test
    fun `when both timeout overloads fire then timeout reported once and service stops`() {
        service.onTimeout(1)
        service.onTimeout(1, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)

        verify(supervisor, times(1)).onServiceTimedOut()
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `when started then returns not sticky`() {
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1))
    }

    @Test
    fun `when started then channel is silent and secret`() {
        service.onStartCommand(null, 0, 1)

        val channel = service.getSystemService(NotificationManager::class.java).getNotificationChannel("duck_ai_responding")
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals(Notification.VISIBILITY_SECRET, channel.lockscreenVisibility)
        assertFalse(channel.canShowBadge())
    }

    private fun advance() = coroutineRule.testScope.testScheduler.advanceUntilIdle()
}
