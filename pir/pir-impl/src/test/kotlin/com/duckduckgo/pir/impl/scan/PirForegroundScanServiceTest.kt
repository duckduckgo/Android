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

package com.duckduckgo.pir.impl.scan

import android.app.Notification
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.pir.impl.PirFeatureDataCleaner
import com.duckduckgo.pir.impl.checker.PirEligibility
import com.duckduckgo.pir.impl.checker.PirRunMode
import com.duckduckgo.pir.impl.checker.PirWorkHandler
import com.duckduckgo.pir.impl.notifications.PirNotificationManager
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.scheduling.PirExecutionType
import com.duckduckgo.pir.impl.scheduling.PirJobsRunner
import com.duckduckgo.pir.impl.wideevents.PirScanWideEvent
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class PirForegroundScanServiceTest {

    private val pirJobsRunner: PirJobsRunner = mock()
    private val pirNotificationManager: PirNotificationManager = mock()
    private val pirWorkHandler: PirWorkHandler = mock {
        onBlocking { canRunPir() } doReturn flowOf(PirEligibility.Enabled(PirRunMode.SCAN_AND_OPT_OUT))
    }

    // built without onCreate, which would run AndroidInjection
    private val service = Robolectric.buildService(PirForegroundScanService::class.java).get()

    @Before
    fun setUp() {
        whenever(pirNotificationManager.createScanStatusNotification(any(), any())).thenReturn(Notification())

        service.pirJobsRunner = pirJobsRunner
        service.pirNotificationManager = pirNotificationManager
        service.pirWorkHandler = pirWorkHandler
        service.pirFeatureDataCleaner = mock<PirFeatureDataCleaner>()
        service.pirPixelSender = mock<PirPixelSender>()
        service.pirScanWideEvent = mock<PirScanWideEvent>()
    }

    @Test
    fun `test destroying a service recreated only to show the notification does not stop the shared runner`() {
        service.onStartCommand(PirForegroundScanService.intentForShowScanNotification(service), 0, 1)
        service.onDestroy()

        verify(pirJobsRunner, never()).stop()
    }

    @Test
    fun `test show intent with no run in progress stops the recreated service without showing a notification`() {
        service.onStartCommand(PirForegroundScanService.intentForShowScanNotification(service), 0, 1)

        assertTrue(shadowOf(service).isStoppedBySelf)
        verify(pirNotificationManager, never()).createScanStatusNotification(any(), any())
    }

    @Test
    fun `test destroying the service during a run stops the runner`() {
        service.onStartCommand(PirForegroundScanService.intentFor(service, PirExecutionType.MANUAL_INITIAL), 0, 1)
        service.onDestroy()

        verify(pirJobsRunner).stop()
    }

    @Test
    fun `test show intent during a run shows the notification again without starting another run`() {
        wheneverBlocking { pirJobsRunner.runEligibleJobs(any(), any(), any()) }.doSuspendableAnswer { awaitCancellation() }
        service.onStartCommand(PirForegroundScanService.intentFor(service, PirExecutionType.MANUAL_INITIAL), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        service.onStartCommand(PirForegroundScanService.intentForShowScanNotification(service), 0, 2)
        shadowOf(Looper.getMainLooper()).idle()

        verifyBlocking(pirJobsRunner, times(1)) { runEligibleJobs(any(), any(), any()) }
        verify(pirNotificationManager, times(2)).createScanStatusNotification(any(), any())
        assertFalse(shadowOf(service).isStoppedBySelf)
    }
}
