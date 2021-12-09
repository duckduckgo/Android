/*
 * Copyright (c) 2019 DuckDuckGo
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

@file:Suppress("RemoveExplicitTypeArguments")

package com.duckduckgo.app.notification.model

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClearDataNotificationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationsDao: NotificationDao = mock()
    private val settingsDataStore: SettingsDataStore = mock()

    private lateinit var testee: ClearDataNotification

    @Before
    fun before() {
        testee = ClearDataNotification(context, notificationsDao, settingsDataStore)
    }

    @Test
    fun whenNotificationNotSeenAndOptionNotSetThenCanShowIsTrue() = runBlocking<Unit> {
        whenever(notificationsDao.exists(any())).thenReturn(false)
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_NONE)
        assertTrue(testee.canShow())
    }

    @Test
    fun whenNotificationNotSeenButOptionAlreadySetThenCanShowIsFalse() = runBlocking<Unit> {
        whenever(notificationsDao.exists(any())).thenReturn(false)
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_TABS_ONLY)
        assertFalse(testee.canShow())
    }

    @Test
    fun whenNotificationAlreadySeenAndOptionNotSetThenCanShowIsFalse() = runBlocking<Unit> {
        whenever(notificationsDao.exists(any())).thenReturn(true)
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_NONE)
        assertFalse(testee.canShow())
    }
}
