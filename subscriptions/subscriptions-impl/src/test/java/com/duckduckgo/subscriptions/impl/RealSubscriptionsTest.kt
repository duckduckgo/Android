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

package com.duckduckgo.subscriptions.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.auth.Entitlement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RealSubscriptionsTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockSubscriptionsManager: SubscriptionsManager = mock()
    private lateinit var subscriptions: RealSubscriptions

    @Before
    fun before() {
        subscriptions = RealSubscriptions(mockSubscriptionsManager)
    }

    @Test
    fun whenSubscriptionDataSucceedsThenReturnAccessToken() = runTest {
        whenever(mockSubscriptionsManager.getAccessToken()).thenReturn(AccessToken.Success("accessToken"))
        val result = subscriptions.getAccessToken()
        assertEquals("accessToken", result)
    }

    @Test
    fun whenSubscriptionDataFailsThenReturnNull() = runTest {
        whenever(mockSubscriptionsManager.getAccessToken()).thenReturn(AccessToken.Failure("error"))
        assertNull(subscriptions.getAccessToken())
    }

    @Test
    fun whenSubscriptionDataHasEntitlementThenReturnTrue() = runTest {
        whenever(mockSubscriptionsManager.getSubscriptionData()).thenReturn(
            SubscriptionsData.Success("email", "externalId", listOf(Entitlement("id", "name", "product"))),
        )
        assertTrue(subscriptions.hasEntitlement("product"))
    }

    @Test
    fun whenSubscriptionDataFailsThenReturnFalse() = runTest {
        whenever(mockSubscriptionsManager.getSubscriptionData()).thenReturn(SubscriptionsData.Failure("error"))

        assertFalse(subscriptions.hasEntitlement("product"))
    }
}
