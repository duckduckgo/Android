/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.notification.vpnreminder

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VpnReminderNotificationTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationDao: NotificationDao = mock()
    private val subscriptions: Subscriptions = mock()
    private val networkProtectionState: NetworkProtectionState = mock()
    private val dispatcherProvider: DispatcherProvider = coroutineRule.testDispatcherProvider

    private lateinit var testee: VpnReminderNotification

    @Before
    fun before() {
        testee = VpnReminderNotification(
            context,
            notificationDao,
            subscriptions,
            networkProtectionState,
            dispatcherProvider,
        )
    }

    @Test
    fun whenNotificationAlreadySeenThenCanShowIsFalse() = runTest {
        whenever(notificationDao.exists(any())).thenReturn(true)

        assertFalse(testee.canShow())
    }

    @Test
    fun whenSubscriptionActiveAndVpnDisabledThenCanShowIsTrue() = runTest {
        whenever(notificationDao.exists(any())).thenReturn(false)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(networkProtectionState.isEnabled()).thenReturn(false)

        assertTrue(testee.canShow())
    }

    @Test
    fun whenSubscriptionActiveAndVpnEnabledThenCanShowIsFalse() = runTest {
        whenever(notificationDao.exists(any())).thenReturn(false)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        assertFalse(testee.canShow())
    }

    @Test
    fun whenSubscriptionNotActiveThenCanShowIsFalse() = runTest {
        whenever(notificationDao.exists(any())).thenReturn(false)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.EXPIRED)
        whenever(networkProtectionState.isEnabled()).thenReturn(false)

        assertFalse(testee.canShow())
    }
}
