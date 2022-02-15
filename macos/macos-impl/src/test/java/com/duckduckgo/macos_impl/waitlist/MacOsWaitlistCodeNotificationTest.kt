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

import android.content.Context
import com.duckduckgo.app.notification.NotificationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class MacOsWaitlistCodeNotificationTest {
    private val mockContext: Context = mock()
    private val mockNotificationRepository: NotificationRepository = mock()
    private val mockMacOsWaitlistManager: MacOsWaitlistManager = mock()

    private lateinit var testee: MacOsWaitlistCodeNotification

    @Before
    fun before() {
        testee = MacOsWaitlistCodeNotification(mockContext, mockNotificationRepository, mockMacOsWaitlistManager)
    }

    @Test
    fun whenNotificationNotSeenAndSendNotificationIsTrueThenReturnTrue() = runTest {
        whenever(mockNotificationRepository.exists(any())).thenReturn(false)
        whenever(mockMacOsWaitlistManager.isNotificationEnabled()).thenReturn(true)
        assertTrue(testee.canShow())
    }

    @Test
    fun whenNotificationNotSeenAndSendNotificationIsFalseThenReturnFalse() = runTest {
        whenever(mockNotificationRepository.exists(any())).thenReturn(false)
        whenever(mockMacOsWaitlistManager.isNotificationEnabled()).thenReturn(false)
        assertFalse(testee.canShow())
    }

    @Test
    fun whenNotificationSeenThenReturnFalse() = runTest {
        whenever(mockNotificationRepository.exists(any())).thenReturn(true)
        whenever(mockMacOsWaitlistManager.isNotificationEnabled()).thenReturn(true)
        assertFalse(testee.canShow())
    }
}
