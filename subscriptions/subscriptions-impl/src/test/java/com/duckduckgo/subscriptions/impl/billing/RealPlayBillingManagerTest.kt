package com.duckduckgo.subscriptions.impl.billing

import android.app.Activity
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.testing.TestLifecycleOwner
import app.cash.turbine.test
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PurchaseHistoryRecord
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.BASIC_SUBSCRIPTION
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LIST_OF_PRODUCTS
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN
import com.duckduckgo.subscriptions.impl.billing.BillingError.BILLING_UNAVAILABLE
import com.duckduckgo.subscriptions.impl.billing.BillingError.NETWORK_ERROR
import com.duckduckgo.subscriptions.impl.billing.FakeBillingClientAdapter.FakeMethodInvocation.Connect
import com.duckduckgo.subscriptions.impl.billing.FakeBillingClientAdapter.FakeMethodInvocation.GetSubscriptions
import com.duckduckgo.subscriptions.impl.billing.FakeBillingClientAdapter.FakeMethodInvocation.GetSubscriptionsPurchaseHistory
import com.duckduckgo.subscriptions.impl.billing.FakeBillingClientAdapter.FakeMethodInvocation.LaunchBillingFlow
import com.duckduckgo.subscriptions.impl.billing.PurchaseState.Canceled
import com.duckduckgo.subscriptions.impl.billing.PurchaseState.InProgress
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealPlayBillingManagerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val billingClientAdapter = FakeBillingClientAdapter()

    private lateinit var processLifecycleOwner: TestLifecycleOwner

    private val subject = RealPlayBillingManager(
        coroutineScope = coroutineRule.testScope,
        pixelSender = mock(),
        billingClient = billingClientAdapter,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
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
        billingClientAdapter.billingInitResult = BillingInitResult.Failure(BILLING_UNAVAILABLE)

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

        assertEquals(1, subject.getProducts().size)
        assertEquals("test-sub", subject.getProducts().single().productId)
    }

    @Test
    fun `when service not ready before launching billing flow then attempts to connect`() = runTest {
        processLifecycleOwner.currentState = RESUMED
        billingClientAdapter.connected = false
        billingClientAdapter.launchBillingFlowResult = LaunchBillingFlowResult.Success
        billingClientAdapter.methodInvocations.clear()

        val productDetails: ProductDetails = subject.getProducts().single()
        val externalId = "external_id"

        subject.purchaseState.test {
            expectNoEvents()

            subject.launchBillingFlow(activity = mock(), planId = MONTHLY_PLAN, externalId)

            assertEquals(InProgress, awaitItem())
        }

        billingClientAdapter.verifyConnectInvoked()
        billingClientAdapter.verifyLaunchBillingFlowInvoked(productDetails, offerToken = "monthly_offer_token", externalId)
    }

    @Test
    fun `when can't connect to service then launching billing flow is cancelled`() = runTest {
        billingClientAdapter.billingInitResult = BillingInitResult.Failure(BILLING_UNAVAILABLE)
        processLifecycleOwner.currentState = RESUMED
        billingClientAdapter.launchBillingFlowResult = LaunchBillingFlowResult.Failure
        billingClientAdapter.methodInvocations.clear()

        val externalId = "external_id"

        subject.purchaseState.test {
            expectNoEvents()

            subject.launchBillingFlow(activity = mock(), planId = MONTHLY_PLAN, externalId)

            assertEquals(Canceled, awaitItem())
        }

        billingClientAdapter.verifyConnectInvoked()
        billingClientAdapter.verifyLaunchBillingFlowNotInvoked()
    }

    @Test
    fun `when service disconnected then attempt to connect`() = runTest {
        processLifecycleOwner.currentState = RESUMED
        billingClientAdapter.methodInvocations.clear()

        // simulate service disconnection
        billingClientAdapter.connected = false
        billingClientAdapter.disconnectionListener?.invoke()

        billingClientAdapter.verifyConnectInvoked()
    }

    @Test
    fun `when connect fails with recoverable error then retry with exponential backoff`() = runTest {
        billingClientAdapter.billingInitResult = BillingInitResult.Failure(NETWORK_ERROR)

        processLifecycleOwner.currentState = RESUMED

        val incrementalDelays = generateSequence(1.seconds) { (it * 4).coerceAtMost(5.minutes) }.iterator()

        repeat(times = 6) { attemptIndex ->
            billingClientAdapter.verifyConnectInvoked(times = 1 + attemptIndex)

            advanceTimeBy(incrementalDelays.next())
            runCurrent()
        }
    }

    @Test
    fun `when launch billing flow then retrieves ProductDetails for provided plan id`() = runTest {
        processLifecycleOwner.currentState = RESUMED
        billingClientAdapter.launchBillingFlowResult = LaunchBillingFlowResult.Success

        val productDetails: ProductDetails = subject.getProducts().single()
        val offerDetails = productDetails.subscriptionOfferDetails!!.first()
        val externalId = "external_id"

        subject.purchaseState.test {
            expectNoEvents()

            subject.launchBillingFlow(activity = mock(), planId = offerDetails.basePlanId, externalId)

            assertEquals(InProgress, awaitItem())
        }

        billingClientAdapter.verifyLaunchBillingFlowInvoked(productDetails, offerToken = offerDetails.offerToken, externalId)
    }
}

class FakeBillingClientAdapter : BillingClientAdapter {

    var connected = false
    val methodInvocations = mutableListOf<FakeMethodInvocation>()

    var subscriptions: List<ProductDetails> = listOf(
        mock {
            whenever(it.productId).thenReturn(BASIC_SUBSCRIPTION)

            val monthlyOffer: ProductDetails.SubscriptionOfferDetails = mock { offer ->
                whenever(offer.basePlanId).thenReturn(MONTHLY_PLAN)
                whenever(offer.offerToken).thenReturn("monthly_offer_token")
            }

            val yearlyOffer: ProductDetails.SubscriptionOfferDetails = mock { offer ->
                whenever(offer.basePlanId).thenReturn(YEARLY_PLAN)
                whenever(offer.offerToken).thenReturn("yearly_offer_token")
            }

            whenever(it.subscriptionOfferDetails).thenReturn(listOf(monthlyOffer, yearlyOffer))
        },
    )

    var subscriptionsPurchaseHistory: List<PurchaseHistoryRecord> = emptyList()
    var launchBillingFlowResult: LaunchBillingFlowResult = LaunchBillingFlowResult.Failure
    var billingInitResult: BillingInitResult = BillingInitResult.Success

    var purchasesListener: ((PurchasesUpdateResult) -> Unit)? = null
    var disconnectionListener: (() -> Unit)? = null

    override val ready: Boolean
        get() = connected

    override suspend fun connect(
        purchasesListener: (PurchasesUpdateResult) -> Unit,
        disconnectionListener: () -> Unit,
    ): BillingInitResult {
        methodInvocations.add(Connect)

        when (billingInitResult) {
            BillingInitResult.Success -> {
                connected = true
                this.purchasesListener = purchasesListener
                this.disconnectionListener = disconnectionListener
            }
            is BillingInitResult.Failure -> {
                connected = false
                this.purchasesListener = null
                this.disconnectionListener = null
            }
        }

        return billingInitResult
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
        return launchBillingFlowResult
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

    fun verifyLaunchBillingFlowInvoked(
        productDetails: ProductDetails,
        offerToken: String,
        externalId: String,
        times: Int = 1,
    ) {
        val invocations = methodInvocations
            .filterIsInstance<LaunchBillingFlow>()
            .filter { invocation ->
                invocation.productDetails == productDetails &&
                    invocation.offerToken == offerToken &&
                    invocation.externalId == externalId
            }
        assertEquals(times, invocations.count())
    }

    fun verifyLaunchBillingFlowNotInvoked() {
        assertTrue(methodInvocations.filterIsInstance<LaunchBillingFlow>().isEmpty())
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
