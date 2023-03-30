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

package com.duckduckgo.app.notification.model

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.statistics.VariantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class EnableAppTpNotificationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mockNotificationsDao: NotificationDao = mock()
    private val mockVariantManager: VariantManager = mock()

    private lateinit var testee: EnableAppTpNotification

    @Before
    fun setup() {
        testee = EnableAppTpNotification(context, mockNotificationsDao, mockVariantManager)
    }

    @Test
    fun whenNotificationNotSeenThenCanShowIsTrue() = runTest {
        whenever(mockNotificationsDao.exists(any())).thenReturn(false)
        assertTrue(testee.canShow())
    }

    @Test
    fun whenNotificationAlreadySeenThenCanShowIsFalse() = runTest {
        whenever(mockNotificationsDao.exists(any())).thenReturn(true)
        assertFalse(testee.canShow())
    }

    @Test
    fun whenOneEasyStepForPrivacyNotificationEnabledThenReturnOneEasyStepForPrivacySpecification() = runTest {
        whenever(mockVariantManager.getVariant()).thenReturn(VariantManager.ACTIVE_VARIANTS.first { it.key == "zm" })

        val spec = testee.buildSpecification()

        assertTrue(spec is OneEasyStepForPrivacySpecification)
    }

    @Test
    fun whenOneEasyStepForPrivacyNotificationNotEnabledThenReturnNextLevelPrivacySpecification() = runTest {
        whenever(mockVariantManager.getVariant()).thenReturn(VariantManager.ACTIVE_VARIANTS.first { it.key == "zn" })
        val spec = testee.buildSpecification()

        assertTrue(spec is NextLevelPrivacySpecification)
    }
}
