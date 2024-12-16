/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.pir

import android.content.Context
import android.net.Uri
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.pir.PirSubscriptionManager.PirStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RealPirSubscriptionManagerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Test
    fun `when user does not have pir entitlement then pir status returns ineligible`() = runTest {
        val subscriptions: Subscriptions = FakeSubscriptions(SubscriptionStatus.UNKNOWN)

        val pirSubscriptionManager = RealPirSubscriptionManager(subscriptions)

        pirSubscriptionManager.pirStatus().test {
            assertEquals(PirStatus.INELIGIBLE, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `when user has pir entitlement but subscription is inactive then pir status returns expired`() = runTest {
        val subscriptions: Subscriptions = FakeSubscriptions(SubscriptionStatus.INACTIVE, listOf(Product.PIR))

        val pirSubscriptionManager = RealPirSubscriptionManager(subscriptions)

        pirSubscriptionManager.pirStatus().test {
            assertEquals(PirStatus.EXPIRED, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `when user has pir entitlement but subscription is expired then pir status returns expired`() = runTest {
        val subscriptions: Subscriptions = FakeSubscriptions(SubscriptionStatus.EXPIRED, listOf(Product.PIR))

        val pirSubscriptionManager = RealPirSubscriptionManager(subscriptions)

        pirSubscriptionManager.pirStatus().test {
            assertEquals(PirStatus.EXPIRED, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `when user has pir entitlement but subscription is unknown then pir status returns signed out`() = runTest {
        val subscriptions: Subscriptions = FakeSubscriptions(SubscriptionStatus.UNKNOWN, listOf(Product.PIR))

        val pirSubscriptionManager = RealPirSubscriptionManager(subscriptions)

        pirSubscriptionManager.pirStatus().test {
            assertEquals(PirStatus.SIGNED_OUT, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `when user has pir entitlement and subscription is auto renewable then pir status returns active`() = runTest {
        val subscriptions: Subscriptions = FakeSubscriptions(SubscriptionStatus.AUTO_RENEWABLE, listOf(Product.PIR))

        val pirSubscriptionManager = RealPirSubscriptionManager(subscriptions)

        pirSubscriptionManager.pirStatus().test {
            assertEquals(PirStatus.ACTIVE, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `when user has pir entitlement and subscription is not auto renewable then pir status returns active`() = runTest {
        val subscriptions: Subscriptions = FakeSubscriptions(SubscriptionStatus.NOT_AUTO_RENEWABLE, listOf(Product.PIR))

        val pirSubscriptionManager = RealPirSubscriptionManager(subscriptions)

        pirSubscriptionManager.pirStatus().test {
            assertEquals(PirStatus.ACTIVE, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `when user has pir entitlement and subscription is grace period then pir status returns active`() = runTest {
        val subscriptions: Subscriptions = FakeSubscriptions(SubscriptionStatus.GRACE_PERIOD, listOf(Product.PIR))

        val pirSubscriptionManager = RealPirSubscriptionManager(subscriptions)

        pirSubscriptionManager.pirStatus().test {
            assertEquals(PirStatus.ACTIVE, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `when user has pir entitlement and subscription is waiting then pir status returns waiting`() = runTest {
        val subscriptions: Subscriptions = FakeSubscriptions(SubscriptionStatus.WAITING, listOf(Product.PIR))

        val pirSubscriptionManager = RealPirSubscriptionManager(subscriptions)

        pirSubscriptionManager.pirStatus().test {
            assertEquals(PirStatus.WAITING, awaitItem())
            awaitComplete()
        }
    }

}

private class FakeSubscriptions(
    private val subscriptionStatus: SubscriptionStatus,
    private val entitlements: List<Product> = emptyList(),
) : Subscriptions {

    override suspend fun isSignedIn(): Boolean = true

    override suspend fun getAccessToken(): String = "fake_access_token"

    override fun getEntitlementStatus(): Flow<List<Product>> = flowOf(entitlements)

    override suspend fun isEligible(): Boolean = true

    override suspend fun getSubscriptionStatus(): SubscriptionStatus = subscriptionStatus

    override fun shouldLaunchPrivacyProForUrl(url: String): Boolean = false

    override fun launchPrivacyPro(
        context: Context,
        uri: Uri?
    ) {
        // no-op
    }

    override fun isPrivacyProUrl(uri: Uri): Boolean = false
}
