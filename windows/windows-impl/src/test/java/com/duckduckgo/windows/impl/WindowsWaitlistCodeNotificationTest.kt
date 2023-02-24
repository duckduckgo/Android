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

import android.content.Context
import com.duckduckgo.app.notification.NotificationRepository
import com.duckduckgo.windows.impl.waitlist.WindowsWaitlistCodeNotification
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class WindowsWaitlistCodeNotificationTest {
    private val mockContext: Context = mock()
    private val mockNotificationRepository: NotificationRepository = mock()

    private lateinit var testee: WindowsWaitlistCodeNotification

    @Before
    fun before() {
        testee = WindowsWaitlistCodeNotification(mockContext, mockNotificationRepository)
    }

    @Test
    fun whenNotificationNotSeenThenReturnTrue() = runTest {
        whenever(mockNotificationRepository.exists(any())).thenReturn(false)
        assertTrue(testee.canShow())
    }

    @Test
    fun whenNotificationSeenThenReturnFalse() = runTest {
        whenever(mockNotificationRepository.exists(any())).thenReturn(true)
        assertFalse(testee.canShow())
    }
}
