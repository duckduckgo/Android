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

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealPirForegroundScanServiceStarterTest {

    private val context: Context = mock {
        on { packageName } doReturn "com.duckduckgo.mobile.android"
    }
    private val monitor: PirForegroundScanServiceMonitor = mock()

    private val testee = RealPirForegroundScanServiceStarter(
        context = context,
        pirForegroundScanServiceMonitor = monitor,
    )

    @Test
    fun `test does not start the service when no scan is running`() {
        whenever(monitor.isRunning()).thenReturn(false)

        testee.showScanNotificationIfRunning()

        verify(context, never()).startService(any())
        verify(context, never()).startForegroundService(any())
    }

    @Test
    fun `test asks the running service to show its notification`() {
        whenever(monitor.isRunning()).thenReturn(true)

        testee.showScanNotificationIfRunning()

        val intentCaptor = argumentCaptor<Intent>()
        verify(context).startService(intentCaptor.capture())
        verify(context, never()).startForegroundService(any())
        val expected = PirForegroundScanService.intentForShowScanNotification(context)
        assertEquals(expected.action, intentCaptor.firstValue.action)
    }

    @Test
    fun `test does not crash when the service cannot be started`() {
        whenever(monitor.isRunning()).thenReturn(true)
        whenever(context.startService(any())).thenThrow(IllegalStateException("background start not allowed"))

        testee.showScanNotificationIfRunning()
    }
}
