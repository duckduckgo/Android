/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.windows.impl

import com.duckduckgo.app.notification.TaskStackBuilderFactory
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.windows.impl.WindowsPixelNames.WINDOWS_WAITLIST_NOTIFICATION_CANCELLED
import com.duckduckgo.windows.impl.WindowsPixelNames.WINDOWS_WAITLIST_NOTIFICATION_SHOWN
import com.duckduckgo.windows.impl.waitlist.WindowsWaitlistNotificationPlugin
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class WindowsWaitlistNotificationPluginTest {

    private val mockNotification: SchedulableNotification = mock()
    private val mockTaskStackBuilderFactory: TaskStackBuilderFactory = mock()
    private val mockPixel: Pixel = mock()
    lateinit var testee: WindowsWaitlistNotificationPlugin

    @Before
    fun before() {
        testee = WindowsWaitlistNotificationPlugin(mock(), mockNotification, mockTaskStackBuilderFactory, mockPixel)
    }

    @Test
    fun whenGetSchedulableNotificationThenReturnNotification() {
        assertEquals(mockNotification, testee.getSchedulableNotification())
    }

    @Test
    fun whenOnNotificationCancelledThenPixelFired() {
        testee.onNotificationCancelled()
        verify(mockPixel).fire(WINDOWS_WAITLIST_NOTIFICATION_CANCELLED)
    }

    @Test
    fun whenOnNotificationShownThenPixelFired() {
        testee.onNotificationShown()
        verify(mockPixel).fire(WINDOWS_WAITLIST_NOTIFICATION_SHOWN)
    }
}
