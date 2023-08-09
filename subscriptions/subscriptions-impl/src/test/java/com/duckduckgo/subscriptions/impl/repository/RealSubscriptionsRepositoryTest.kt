package com.duckduckgo.subscriptions.impl.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.android.billingclient.api.Purchase
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.billing.BillingClientWrapper
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.BASIC_SUBSCRIPTION
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RealSubscriptionsRepositoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var repository: RealSubscriptionsRepository
    private val billingClient: BillingClientWrapper = mock()

    @Test
    fun whenPurchasesContainsSubscriptionReturnTrue() = runTest {
        whenever(billingClient.purchases).thenReturn(flowOf(listOf(purchaseWithSubscription())))
        repository = RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)

        repository.hasSubscription.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPurchasesDoesNotContainSubscriptionReturnFalse() = runTest {
        whenever(billingClient.purchases).thenReturn(flowOf(listOf(purchaseWithoutSubscription())))
        repository = RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)

        repository.hasSubscription.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPurchasesEmptyReturnFalse() = runTest {
        whenever(billingClient.purchases).thenReturn(flowOf(listOf()))
        repository = RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)

        repository.hasSubscription.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPurchasesExistReturnThem() = runTest {
        whenever(billingClient.purchases).thenReturn(flowOf(listOf(purchaseWithoutSubscription())))
        repository = RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)

        repository.purchases.test {
            assertTrue(awaitItem().size == 1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPurchasesEmptyReturnEmpty() = runTest {
        whenever(billingClient.purchases).thenReturn(flowOf(listOf()))
        repository = RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)

        repository.purchases.test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun purchaseWithSubscription(): Purchase {
        val product = BASIC_SUBSCRIPTION
        return Purchase(
            """
        {"purchaseToken": "token", "productIds": ["$product"]}
        """,
            "signature",
        )
    }

    private fun purchaseWithoutSubscription(): Purchase {
        val product = "test"
        return Purchase(
            """
        {"purchaseToken": "token", "productIds": ["$product"]}
        """,
            "signature",
        )
    }
}
