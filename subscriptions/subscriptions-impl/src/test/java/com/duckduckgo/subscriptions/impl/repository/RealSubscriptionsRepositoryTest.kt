package com.duckduckgo.subscriptions.impl.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.billing.BillingClientWrapper
import com.duckduckgo.subscriptions.impl.billing.RealBillingClientWrapper.Companion.BASIC_SUBSCRIPTION
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun whenProductDetailsExistAndMatchBasicSubscriptionIdThenReturnIt() = runTest {
        emitProducts(BASIC_SUBSCRIPTION)
        repository = RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)

        repository.subscriptionDetails.test {
            assertTrue(awaitItem().productId == BASIC_SUBSCRIPTION)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProductDetailsDoNotExistThenExpectNoEvents() = runTest {
        repository = RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)

        repository.subscriptionDetails.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProductDetailsExistAndDoNotMatchBasicSubscriptionIdThenExpectNoEvents() = runTest {
        emitProducts("test")
        repository = RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)

        repository.subscriptionDetails.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSubscriptionDetailsExistThenReturnMapById() = runTest {
        emitProducts(BASIC_SUBSCRIPTION)
        repository = RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)

        repository.offerDetails.test {
            assertTrue(awaitItem().size == 1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSubscriptionDetailsDoNotExistThenMapEmpty() = runTest {
        repository = RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)

        repository.offerDetails.test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseRecordHistoryContainsSubscriptionsReturnMostRecent() = runTest {
        val testFlow: MutableStateFlow<List<PurchaseHistoryRecord>> = MutableStateFlow(listOf())
        val firstPurchase = purchaseRecordWithSubscription(1)
        val lastPurchase = purchaseRecordWithSubscription(2)
        whenever(billingClient.products).thenReturn(flowOf())
        whenever(billingClient.purchaseHistory).thenReturn(testFlow)
        testFlow.emit(listOf(firstPurchase, lastPurchase))

        repository = RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)

        repository.lastPurchaseHistoryRecord.test {
            assertTrue(awaitItem()?.purchaseTime == 2L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseRecordHistoryEmptyReturnNull() = runTest {
        val testFlow: MutableStateFlow<List<PurchaseHistoryRecord>> = MutableStateFlow(listOf())
        whenever(billingClient.products).thenReturn(flowOf())
        whenever(billingClient.purchaseHistory).thenReturn(testFlow)

        repository = RealSubscriptionsRepository(billingClient, coroutineRule.testDispatcherProvider, coroutineRule.testScope)

        repository.lastPurchaseHistoryRecord.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun getProductDetails(productId: String = BASIC_SUBSCRIPTION): ProductDetails {
        val productDetails: ProductDetails = mock()
        val subscriptionOfferDetails: SubscriptionOfferDetails = mock()
        whenever(subscriptionOfferDetails.basePlanId).thenReturn("basePlanId")
        whenever(productDetails.productId).thenReturn(productId)
        whenever(productDetails.name).thenReturn("name")
        whenever(productDetails.description).thenReturn("description")
        whenever(productDetails.subscriptionOfferDetails).thenReturn(listOf(subscriptionOfferDetails))
        return productDetails
    }
    private suspend fun emitProducts(productId: String = BASIC_SUBSCRIPTION) {
        val testFlow: MutableStateFlow<Map<String, ProductDetails>> = MutableStateFlow(mapOf())
        whenever(billingClient.products).thenReturn(testFlow)
        testFlow.emit(mapOf(productId to getProductDetails(productId)))
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

    private fun purchaseRecordWithSubscription(time: Int): PurchaseHistoryRecord {
        return PurchaseHistoryRecord(
            """
        {"purchaseToken": "token", "productId": "test", "purchaseTime":$time, "quantity":1}
        """,
            "signature",
        )
    }
}
