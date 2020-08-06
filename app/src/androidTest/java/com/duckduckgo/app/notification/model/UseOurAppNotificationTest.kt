/*
 * Copyright (c) 2020 DuckDuckGo
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
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UseOurAppNotificationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationsDao: NotificationDao = mock()
    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockAddToHomeCapabilityDetector: AddToHomeCapabilityDetector = mock()

    private lateinit var testee: UseOurAppNotification

    @Before
    fun before() {
        testee = UseOurAppNotification(context, notificationsDao, mockSettingsDataStore, mockAddToHomeCapabilityDetector)
    }

    @Test
    fun whenNotificationNotSeenAndHideTipsIsFalseAndAddToHomeSupportedThenCanShowIsTrue() = runBlocking {
        whenever(mockAddToHomeCapabilityDetector.isAddToHomeSupported()).thenReturn(true)
        whenever(notificationsDao.exists(any())).thenReturn(false)
        whenever(mockSettingsDataStore.hideTips).thenReturn(false)
        assertTrue(testee.canShow())
    }

    @Test
    fun whenNotificationAlreadySeenThenCanShowIsFalse() = runBlocking {
        whenever(notificationsDao.exists(any())).thenReturn(true)
        whenever(mockAddToHomeCapabilityDetector.isAddToHomeSupported()).thenReturn(true)
        whenever(mockSettingsDataStore.hideTips).thenReturn(false)
        assertFalse(testee.canShow())
    }

    @Test
    fun whenHideTipsIsTrueThenCanShowIsFalse() = runBlocking {
        whenever(notificationsDao.exists(any())).thenReturn(false)
        whenever(mockAddToHomeCapabilityDetector.isAddToHomeSupported()).thenReturn(true)
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        assertFalse(testee.canShow())
    }

    @Test
    fun whenAddToHomeNotSupportedThenCanShowIsFalse() = runBlocking {
        whenever(notificationsDao.exists(any())).thenReturn(false)
        whenever(mockAddToHomeCapabilityDetector.isAddToHomeSupported()).thenReturn(false)
        whenever(mockSettingsDataStore.hideTips).thenReturn(false)
        assertFalse(testee.canShow())
    }
}
