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

package com.duckduckgo.subscriptions.impl

import android.content.Context
import android.net.Uri
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.Product.*
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.*
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.ProductSubscriptionManager.ProductStatus.ACTIVE
import com.duckduckgo.subscriptions.impl.ProductSubscriptionManager.ProductStatus.EXPIRED
import com.duckduckgo.subscriptions.impl.ProductSubscriptionManager.ProductStatus.INELIGIBLE
import com.duckduckgo.subscriptions.impl.ProductSubscriptionManager.ProductStatus.SIGNED_OUT
import com.duckduckgo.subscriptions.impl.ProductSubscriptionManager.ProductStatus.WAITING
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RealProductSubscriptionManagerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Test
    fun `when user does not have entitlement then status returns ineligible`() = runTest {
        val subscriptions: Subscriptions = FakeSubscriptions(UNKNOWN)

        val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

        productSubscriptionManager.entitlementStatus(PIR).test {
            assertEquals(INELIGIBLE, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `when multiple entitlement status requested and is not entitled then returns ineligible`() =
        runTest {
            val subscriptions: Subscriptions = FakeSubscriptions(UNKNOWN)

            val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

            productSubscriptionManager.entitlementStatus(ITR, ROW_ITR).test {
                assertEquals(INELIGIBLE, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `when user has entitlement but subscription is inactive then status returns expired`() =
        runTest {
            val subscriptions: Subscriptions = FakeSubscriptions(INACTIVE, listOf(PIR))

            val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

            productSubscriptionManager.entitlementStatus(PIR).test {
                assertEquals(EXPIRED, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `when multiple entitlement status requested and has single entitlement with status inactive then status returns expired`() =
        runTest {
            val subscriptions: Subscriptions = FakeSubscriptions(INACTIVE, listOf(ROW_ITR))

            val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

            productSubscriptionManager.entitlementStatus(ITR, ROW_ITR).test {
                assertEquals(EXPIRED, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `when user has entitlement but subscription is expired then status returns expired`() =
        runTest {
            val subscriptions: Subscriptions = FakeSubscriptions(SubscriptionStatus.EXPIRED, listOf(PIR))

            val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

            productSubscriptionManager.entitlementStatus(PIR).test {
                assertEquals(EXPIRED, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `when multiple entitlement status requested and has single entitlement with status expired then status returns expired`() =
        runTest {
            val subscriptions: Subscriptions = FakeSubscriptions(SubscriptionStatus.EXPIRED, listOf(ROW_ITR))

            val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

            productSubscriptionManager.entitlementStatus(ITR, ROW_ITR).test {
                assertEquals(EXPIRED, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `when user has entitlement but subscription is unknown then status returns signed out`() =
        runTest {
            val subscriptions: Subscriptions = FakeSubscriptions(UNKNOWN, listOf(PIR))

            val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

            productSubscriptionManager.entitlementStatus(PIR).test {
                assertEquals(SIGNED_OUT, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `when multiple entitlement status requested and has single entitlement with status unknown then status returns signed out`() =
        runTest {
            val subscriptions: Subscriptions = FakeSubscriptions(UNKNOWN, listOf(ROW_ITR))

            val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

            productSubscriptionManager.entitlementStatus(ITR, ROW_ITR).test {
                assertEquals(SIGNED_OUT, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `when user has entitlement and subscription is auto renewable then status returns active`() =
        runTest {
            val subscriptions: Subscriptions = FakeSubscriptions(AUTO_RENEWABLE, listOf(PIR))

            val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

            productSubscriptionManager.entitlementStatus(PIR).test {
                assertEquals(ACTIVE, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `when multiple entitlement status requested and has single entitlement with status auto renewable then status returns active`() =
        runTest {
            val subscriptions: Subscriptions = FakeSubscriptions(AUTO_RENEWABLE, listOf(ROW_ITR))

            val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

            productSubscriptionManager.entitlementStatus(ITR, ROW_ITR).test {
                assertEquals(ACTIVE, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `when user has entitlement and subscription is not auto renewable then status returns active`() =
        runTest {
            val subscriptions: Subscriptions = FakeSubscriptions(NOT_AUTO_RENEWABLE, listOf(PIR))

            val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

            productSubscriptionManager.entitlementStatus(PIR).test {
                assertEquals(ACTIVE, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `when multiple entitlement status requested and has single entitlement with status not auto renewable then status returns active`() =
        runTest {
            val subscriptions: Subscriptions = FakeSubscriptions(NOT_AUTO_RENEWABLE, listOf(ROW_ITR))

            val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

            productSubscriptionManager.entitlementStatus(ITR, ROW_ITR).test {
                assertEquals(ACTIVE, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `when user has entitlement and subscription is grace period then status returns active`() =
        runTest {
            val subscriptions: Subscriptions = FakeSubscriptions(GRACE_PERIOD, listOf(PIR))

            val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

            productSubscriptionManager.entitlementStatus(PIR).test {
                assertEquals(ACTIVE, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `when multiple entitlement status requested and has single entitlement with status grace period then status returns active`() =
        runTest {
            val subscriptions: Subscriptions = FakeSubscriptions(GRACE_PERIOD, listOf(ROW_ITR))

            val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

            productSubscriptionManager.entitlementStatus(ITR, ROW_ITR).test {
                assertEquals(ACTIVE, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `when user has entitlement and subscription is waiting then status returns waiting`() =
        runTest {
            val subscriptions: Subscriptions = FakeSubscriptions(SubscriptionStatus.WAITING, listOf(PIR))

            val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

            productSubscriptionManager.entitlementStatus(PIR).test {
                assertEquals(WAITING, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `when multiple entitlement status requested and has single entitlement with status waiting then status returns waiting`() =
        runTest {
            val subscriptions: Subscriptions = FakeSubscriptions(SubscriptionStatus.WAITING, listOf(ROW_ITR))

            val productSubscriptionManager = RealProductSubscriptionManager(subscriptions)

            productSubscriptionManager.entitlementStatus(ITR, ROW_ITR).test {
                assertEquals(WAITING, awaitItem())
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

    override suspend fun getAvailableProducts(): Set<Product> = emptySet()

    override fun shouldLaunchPrivacyProForUrl(url: String): Boolean = false

    override fun launchPrivacyPro(
        context: Context,
        uri: Uri?,
    ) {
        // no-op
    }

    override fun isPrivacyProUrl(uri: Uri): Boolean = false

    override suspend fun isFreeTrialEligible(): Boolean = false
}
