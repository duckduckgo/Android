package com.duckduckgo.subscriptions.impl.billing

import android.app.Activity
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.testing.TestLifecycleOwner
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PurchaseHistoryRecord
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.BASIC_SUBSCRIPTION
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LIST_OF_PRODUCTS
import com.duckduckgo.subscriptions.impl.billing.FakeBillingClientAdapter.FakeMethodInvocation.Connect
import com.duckduckgo.subscriptions.impl.billing.FakeBillingClientAdapter.FakeMethodInvocation.GetSubscriptions
import com.duckduckgo.subscriptions.impl.billing.FakeBillingClientAdapter.FakeMethodInvocation.GetSubscriptionsPurchaseHistory
import com.duckduckgo.subscriptions.impl.billing.FakeBillingClientAdapter.FakeMethodInvocation.LaunchBillingFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealPlayBillingManagerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val billingClientAdapter = FakeBillingClientAdapter()

    private lateinit var processLifecycleOwner: TestLifecycleOwner

    private val subject = RealPlayBillingManager(
        coroutineScope = coroutineRule.testScope,
        pixelSender = mock(),
        billingClient = billingClientAdapter,
    )

    @Before
    fun setUp() {
        processLifecycleOwner = TestLifecycleOwner(initialState = INITIALIZED)
        processLifecycleOwner.lifecycle.addObserver(subject)
    }

    @Test
    fun `when process created then connects to billing service and loads data`() = runTest {
        processLifecycleOwner.currentState = CREATED

        billingClientAdapter.verifyConnectInvoked()
        billingClientAdapter.verifyGetSubscriptionsInvoked(productIds = LIST_OF_PRODUCTS)
        billingClientAdapter.verifyGetSubscriptionPurchaseHistoryInvoked()
    }

    @Test
    fun `when connection failed then does not attempt loading anything`() = runTest {
        billingClientAdapter.canConnect = false

        processLifecycleOwner.currentState = CREATED

        billingClientAdapter.verifyConnectInvoked()
        billingClientAdapter.verifyGetSubscriptionsInvoked(times = 0)
        billingClientAdapter.verifyGetSubscriptionPurchaseHistoryInvoked(times = 0)
    }

    @Test
    fun `when connected then returns products`() = runTest {
        billingClientAdapter.subscriptions = listOf(
            mock {
                whenever(it.productId).thenReturn("test-sub")
            },
        )

        processLifecycleOwner.currentState = RESUMED

        assertEquals(1, subject.products.size)
        assertEquals("test-sub", subject.products.single().productId)
    }
}

class FakeBillingClientAdapter : BillingClientAdapter {

    var canConnect = true
    var connected = false
    val methodInvocations = mutableListOf<FakeMethodInvocation>()

    var subscriptions: List<ProductDetails> = listOf(
        mock {
            whenever(it.productId).thenReturn(BASIC_SUBSCRIPTION)
        },
    )

    var subscriptionsPurchaseHistory: List<PurchaseHistoryRecord> = emptyList()

    var purchasesListener: ((PurchasesUpdateResult) -> Unit)? = null
    var disconnectionListener: (() -> Unit)? = null

    override val ready: Boolean
        get() = connected

    override suspend fun connect(
        purchasesListener: (PurchasesUpdateResult) -> Unit,
        disconnectionListener: () -> Unit,
    ): BillingInitResult {
        methodInvocations.add(Connect)
        return if (canConnect) {
            connected = true
            this.purchasesListener = purchasesListener
            this.disconnectionListener = disconnectionListener
            BillingInitResult.Success
        } else {
            connected = false
            BillingInitResult.Failure
        }
    }

    override suspend fun getSubscriptions(productIds: List<String>): SubscriptionsResult {
        methodInvocations.add(GetSubscriptions(productIds))
        return if (ready) {
            SubscriptionsResult.Success(subscriptions)
        } else {
            SubscriptionsResult.Failure()
        }
    }

    override suspend fun getSubscriptionsPurchaseHistory(): SubscriptionsPurchaseHistoryResult {
        methodInvocations.add(GetSubscriptionsPurchaseHistory)
        return if (ready) {
            SubscriptionsPurchaseHistoryResult.Success(subscriptionsPurchaseHistory)
        } else {
            SubscriptionsPurchaseHistoryResult.Failure
        }
    }

    override suspend fun launchBillingFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String,
        externalId: String,
    ): LaunchBillingFlowResult {
        methodInvocations.add(LaunchBillingFlow(productDetails, offerToken, externalId))
        return LaunchBillingFlowResult.Failure
    }

    fun verifyConnectInvoked(times: Int = 1) {
        val invocations = methodInvocations.filterIsInstance<Connect>()
        assertEquals(times, invocations.count())
    }

    fun verifyGetSubscriptionsInvoked(productIds: List<String>? = null, times: Int = 1) {
        val invocations = methodInvocations
            .filterIsInstance<GetSubscriptions>()
            .filter { productIds == null || it.productIds == productIds }
        assertEquals(times, invocations.count())
    }

    fun verifyGetSubscriptionPurchaseHistoryInvoked(times: Int = 1) {
        val invocations = methodInvocations.filterIsInstance<GetSubscriptionsPurchaseHistory>()
        assertEquals(times, invocations.count())
    }

    sealed class FakeMethodInvocation {
        data object Connect : FakeMethodInvocation()
        data class GetSubscriptions(val productIds: List<String>) : FakeMethodInvocation()
        data object GetSubscriptionsPurchaseHistory : FakeMethodInvocation()

        data class LaunchBillingFlow(
            val productDetails: ProductDetails,
            val offerToken: String,
            val externalId: String,
        ) : FakeMethodInvocation()
    }
}
