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

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.responding.RespondingServiceStartFailure.FOREGROUND_NOT_ALLOWED
import com.duckduckgo.duckchat.impl.responding.RespondingServiceStartFailure.LATE_REQUEST_NOT_ALLOWED
import com.duckduckgo.duckchat.impl.responding.RespondingServiceStartFailure.REQUEST_FAILED
import com.duckduckgo.duckchat.impl.responding.RespondingServiceStartFailure.REQUEST_NOT_ALLOWED
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DuckAiRespondingServiceControllerImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val activityManager: ActivityManager = mock {
        on { isBackgroundRestricted } doReturn false
    }
    private val context: Context = mock {
        on { packageName } doReturn "com.duckduckgo.mobile.android"
        on { getSystemService(ActivityManager::class.java) } doReturn activityManager
    }
    private val sharedPreferencesProvider: SharedPreferencesProvider = mock {
        on { getSharedPreferences(any(), any(), any()) } doReturn InMemorySharedPreferences()
    }
    private val store = DuckAiRespondingServiceRunStore(sharedPreferencesProvider)
    private val pixels: DuckChatPixels = mock()

    private val testee = DuckAiRespondingServiceControllerImpl(
        context = context,
        runStore = store,
        pixels = lazyOf(pixels),
        dispatchers = coroutineRule.testDispatcherProvider,
        appScope = coroutineRule.testScope,
    )

    @Test
    fun `when app closes with keepAliveOnLeave enabled then starts service and reports start`() {
        testee.setKeepAliveOnLeave(true)
        testee.onClose()

        val intent = argumentCaptor<Intent>()
        verify(context).startForegroundService(intent.capture())
        assertEquals(DuckAiRespondingService::class.java.name, intent.firstValue.component?.className)
        verify(pixels).reportRespondingServiceStarted(false)
        assertTrue(testee.keepServiceRunning.value)
        assertEquals(InterruptedRun(restricted = false), takeRun())
    }

    @Test
    fun `when app closes with keepAliveOnLeave disabled then nothing starts`() {
        testee.onClose()

        verify(context, never()).startForegroundService(any())
        assertFalse(testee.keepServiceRunning.value)
        assertNull(takeRun())
    }

    @Test
    fun `when restricted at start then start and run marked restricted`() {
        whenever(activityManager.isBackgroundRestricted).thenReturn(true)

        testee.setKeepAliveOnLeave(true)
        testee.onClose()

        verify(pixels).reportRespondingServiceStarted(true)
        assertEquals(InterruptedRun(restricted = true), takeRun())
    }

    @Test
    fun `when request not allowed then failure pixel and flag cleared`() {
        whenever(context.startForegroundService(any())).doThrow(IllegalStateException())

        testee.setKeepAliveOnLeave(true)
        testee.onClose()

        verify(pixels).reportRespondingServiceStartFailed(REQUEST_NOT_ALLOWED)
        verify(pixels, never()).reportRespondingServiceStarted(any())
        assertNull(takeRun())
        verify(context, never()).stopService(any())
    }

    @Test
    fun `when request throws other exception then request failed pixel`() {
        whenever(context.startForegroundService(any())).doThrow(RuntimeException())

        testee.setKeepAliveOnLeave(true)
        testee.onClose()

        verify(pixels).reportRespondingServiceStartFailed(REQUEST_FAILED)
        assertNull(takeRun())
    }

    @Test
    fun `when app returns then keepServiceRunning false flag cleared and service not stopped`() {
        testee.setKeepAliveOnLeave(true)
        testee.onClose()
        testee.onOpen(false)

        assertFalse(testee.keepServiceRunning.value)
        assertNull(takeRun())
        verify(context, never()).stopService(any())
    }

    @Test
    fun `when keep alive disabled from background then keepServiceRunning false flag cleared and service not stopped`() {
        testee.setKeepAliveOnLeave(true)
        testee.onClose()
        testee.setKeepAliveOnLeave(false)
        advance()

        assertFalse(testee.keepServiceRunning.value)
        assertNull(takeRun())
        verify(context, never()).stopService(any())
    }

    @Test
    fun `when keep alive turned back on before main runs then keepServiceRunning stays true`() {
        testee.setKeepAliveOnLeave(true)
        testee.onClose()
        testee.setKeepAliveOnLeave(false)
        testee.setKeepAliveOnLeave(true)
        advance()

        assertTrue(testee.keepServiceRunning.value)
        assertEquals(InterruptedRun(restricted = false), takeRun())
        verify(context, never()).stopService(any())
    }

    @Test
    fun `when enabled after leave then tries late start`() {
        testee.onClose()
        testee.setKeepAliveOnLeave(true)
        advance()

        verify(context, times(1)).startForegroundService(any())
        verify(pixels).reportRespondingServiceStarted(false)
        assertTrue(testee.keepServiceRunning.value)
    }

    @Test
    fun `when late start refused then late request not allowed pixel`() {
        whenever(context.startForegroundService(any())).doThrow(IllegalStateException())

        testee.onClose()
        testee.setKeepAliveOnLeave(true)
        advance()

        verify(pixels).reportRespondingServiceStartFailed(LATE_REQUEST_NOT_ALLOWED)
        assertNull(takeRun())
    }

    @Test
    fun `when enabled again in same leave then no second late start`() {
        whenever(context.startForegroundService(any())).doThrow(IllegalStateException())
        testee.onClose()
        testee.setKeepAliveOnLeave(true)
        advance()

        testee.setKeepAliveOnLeave(false)
        testee.setKeepAliveOnLeave(true)
        advance()

        verify(context, times(1)).startForegroundService(any())
    }

    @Test
    fun `when enabled while app open then nothing starts until leave`() {
        testee.onOpen(false)
        testee.setKeepAliveOnLeave(true)
        advance()

        verify(context, never()).startForegroundService(any())
        assertFalse(testee.keepServiceRunning.value)
    }

    @Test
    fun `when enabled after return then no late start`() {
        testee.onClose()
        testee.onOpen(false)
        testee.setKeepAliveOnLeave(true)
        advance()

        verify(context, never()).startForegroundService(any())
    }

    @Test
    fun `when leaving again after return then starts again`() {
        testee.setKeepAliveOnLeave(true)
        testee.onClose()
        testee.onOpen(false)
        testee.onClose()

        verify(context, times(2)).startForegroundService(any())
        assertTrue(testee.keepServiceRunning.value)
        assertEquals(InterruptedRun(restricted = false), takeRun())
    }

    @Test
    fun `when service times out then timed out pixel and flag cleared`() {
        testee.setKeepAliveOnLeave(true)
        testee.onClose()
        testee.onServiceTimedOut()

        verify(pixels, times(1)).reportRespondingServiceTimedOut()
        assertNull(takeRun())
        verify(context, never()).stopService(any())
    }

    @Test
    fun `when timeout arrives after app returned then no timed out pixel`() {
        testee.setKeepAliveOnLeave(true)
        testee.onClose()
        testee.onOpen(false)

        testee.onServiceTimedOut()

        verify(pixels, never()).reportRespondingServiceTimedOut()
    }

    @Test
    fun `when timeout arrives after keep alive disabled then no timed out pixel`() {
        testee.setKeepAliveOnLeave(true)
        testee.onClose()
        testee.setKeepAliveOnLeave(false)
        advance()

        testee.onServiceTimedOut()

        verify(pixels, never()).reportRespondingServiceTimedOut()
    }

    @Test
    fun `when startForeground fails then failure pixel and flag cleared`() {
        testee.setKeepAliveOnLeave(true)
        testee.onClose()
        testee.onStartFailed(FOREGROUND_NOT_ALLOWED)

        verify(pixels).reportRespondingServiceStartFailed(FOREGROUND_NOT_ALLOWED)
        assertNull(takeRun())
        verify(context, never()).stopService(any())
    }

    @Test
    fun `when interrupted run found on process create then interrupted pixel sent`() {
        store.markRunning(true)

        testee.onCreate(mock())
        advance()

        verify(pixels).reportRespondingServiceInterrupted(true)
        assertNull(takeRun())
    }

    @Test
    fun `when no interrupted run on process create then no interrupted pixel`() {
        testee.onCreate(mock())
        advance()

        verify(pixels, never()).reportRespondingServiceInterrupted(any())
    }

    @Test
    fun `when app opens before interrupted run is read then interrupted pixel still sent`() {
        store.markRunning(false)

        testee.onCreate(mock())
        testee.onOpen(true)
        advance()

        verify(pixels).reportRespondingServiceInterrupted(false)
        assertNull(takeRun())
    }

    @Test
    fun `when service starts before interrupted run is read then only previous run reported`() {
        store.markRunning(true)

        testee.onCreate(mock())
        testee.setKeepAliveOnLeave(true)
        testee.onClose()
        advance()

        verify(pixels).reportRespondingServiceInterrupted(true)
        assertEquals(InterruptedRun(restricted = false), takeRun())
    }

    private fun takeRun(): InterruptedRun? {
        advance()
        return store.takeInterruptedRun()
    }

    private fun advance() = coroutineRule.testScope.testScheduler.advanceUntilIdle()
}
