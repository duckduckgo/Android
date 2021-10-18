/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.notification.model

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.email.db.EmailDataStore
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class EmailWaitlistCodeNotificationTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mockNotificationsDao: NotificationDao = mock()
    private val mockEmailDataStore: EmailDataStore = mock()

    private lateinit var testee: EmailWaitlistCodeNotification

    @Before
    fun before() {
        testee = EmailWaitlistCodeNotification(context, mockNotificationsDao, mockEmailDataStore)
    }

    @Test
    fun whenNotificationNotSeenAndSendNotificationIsTrueThenReturnTrue() = coroutineTestRule.runBlocking {
        whenever(mockNotificationsDao.exists(any())).thenReturn(false)
        whenever(mockEmailDataStore.sendNotification).thenReturn(true)
        assertTrue(testee.canShow())
    }

    @Test
    fun whenNotificationNotSeenAndSendNotificationIsFalseThenReturnFalse() = coroutineTestRule.runBlocking {
        whenever(mockNotificationsDao.exists(any())).thenReturn(false)
        whenever(mockEmailDataStore.sendNotification).thenReturn(false)
        assertFalse(testee.canShow())
    }

    @Test
    fun whenNotificationSeenThenReturnFalse() = coroutineTestRule.runBlocking {
        whenever(mockNotificationsDao.exists(any())).thenReturn(true)
        whenever(mockEmailDataStore.sendNotification).thenReturn(true)
        assertFalse(testee.canShow())
    }
}
