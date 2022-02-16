/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.macos_impl.waitlist

import android.app.TaskStackBuilder
import com.duckduckgo.app.notification.TaskStackBuilderFactory
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.macos_impl.MacOsPixelNames.MACOS_WAITLIST_NOTIFICATION_CANCELLED
import com.duckduckgo.macos_impl.MacOsPixelNames.MACOS_WAITLIST_NOTIFICATION_LAUNCHED
import com.duckduckgo.macos_impl.MacOsPixelNames.MACOS_WAITLIST_NOTIFICATION_SHOWN
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MacOsWaitlistNotificationPluginTest {

    private val mockNotification: SchedulableNotification = mock()
    private val mockTaskStackBuilderFactory: TaskStackBuilderFactory = mock()
    private val mockPixel: Pixel = mock()
    lateinit var testee: MacOsWaitlistNotificationPlugin

    @Before
    fun before() {
        testee = MacOsWaitlistNotificationPlugin(mock(), mockNotification, mockTaskStackBuilderFactory, mockPixel)
    }

    @Test
    fun whenGetSchedulableNotificationThenReturnNotification() {
        assertEquals(mockNotification, testee.getSchedulableNotification())
    }

    @Test
    fun whenOnNotificationCancelledThenPixelFired() {
        testee.onNotificationCancelled()
        verify(mockPixel).fire(MACOS_WAITLIST_NOTIFICATION_CANCELLED)
    }

    @Test
    fun whenOnNotificationShownThenPixelFired() {
        testee.onNotificationShown()
        verify(mockPixel).fire(MACOS_WAITLIST_NOTIFICATION_SHOWN)
    }

    @Test
    fun whenOnNotificationLaunchedThenPixelFired() {
        val stackBuilder: TaskStackBuilder = mock()
        whenever(mockTaskStackBuilderFactory.createTaskBuilder()).thenReturn(stackBuilder)
        whenever(stackBuilder.addNextIntentWithParentStack(any())).thenReturn(stackBuilder)
        doNothing().whenever(stackBuilder).startActivities()

        testee.onNotificationLaunched()
        verify(mockPixel).fire(MACOS_WAITLIST_NOTIFICATION_LAUNCHED)
    }
}
