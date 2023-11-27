package com.duckduckgo.subscriptions.impl.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.android.billingclient.api.ProductDetails.PricingPhase
import com.android.billingclient.api.ProductDetails.PricingPhases
import com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.CurrentPurchase
import com.duckduckgo.subscriptions.impl.JSONObjectAdapter
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.SubscriptionsRepository
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Companion
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView.Success
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.SubscriptionOptionsJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SubscriptionWebViewViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
    private val jsonAdapter: JsonAdapter<SubscriptionOptionsJson> = moshi.adapter(SubscriptionOptionsJson::class.java)
    private val subscriptionsManager: SubscriptionsManager = mock()
    private val subscriptionsRepository: SubscriptionsRepository = mock()

    private lateinit var viewModel: SubscriptionWebViewViewModel

    @Before
    fun setup() {
        viewModel = SubscriptionWebViewViewModel(coroutineTestRule.testDispatcherProvider, subscriptionsManager, subscriptionsRepository)
    }

    @Test
    fun whenPurchaseStateChangesThenReturnCorrectState() = runTest {
        val flowTest: MutableSharedFlow<CurrentPurchase> = MutableSharedFlow()
        whenever(subscriptionsManager.currentPurchaseState).thenReturn(flowTest)
        viewModel.start()

        viewModel.currentPurchaseViewState.test {
            assertTrue(awaitItem().purchaseState is PurchaseStateView.Inactive)
            flowTest.emit(CurrentPurchase.Failure("test"))
            assertTrue(awaitItem().purchaseState is PurchaseStateView.Failure)

            flowTest.emit(CurrentPurchase.Success)
            val success = awaitItem().purchaseState
            assertTrue(success is Success)
            assertEquals(Companion.PURCHASE_COMPLETED_FEATURE_NAME, (success as Success).subscriptionEventData.featureName)
            assertEquals(Companion.PURCHASE_COMPLETED_SUBSCRIPTION_NAME, success.subscriptionEventData.subscriptionName)
            assertNotNull(success.subscriptionEventData.params)
            assertEquals("completed", success.subscriptionEventData.params!!.getString("type"))

            flowTest.emit(CurrentPurchase.InProgress)
            assertTrue(awaitItem().purchaseState is PurchaseStateView.InProgress)

            flowTest.emit(CurrentPurchase.Recovered)
            assertTrue(awaitItem().purchaseState is PurchaseStateView.Recovered)

            flowTest.emit(CurrentPurchase.PreFlowInProgress)
            assertTrue(awaitItem().purchaseState is PurchaseStateView.InProgress)

            flowTest.emit(CurrentPurchase.PreFlowFinished)
            assertTrue(awaitItem().purchaseState is PurchaseStateView.Inactive)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSubscriptionSelectedAndIdInObjectEmptyThenReturnFailure() = runTest {
        val json = """
            {"id":""}
        """.trimIndent()

        viewModel.currentPurchaseViewState.test {
            assertTrue(awaitItem().purchaseState is PurchaseStateView.Inactive)
            viewModel.processJsCallbackMessage("test", "subscriptionSelected", "id", JSONObject(json))
            assertTrue(awaitItem().purchaseState is PurchaseStateView.Failure)
        }
    }

    @Test
    fun whenSubscriptionSelectedAndIdIsInObjectNullThenReturnFailure() = runTest {
        viewModel.currentPurchaseViewState.test {
            assertTrue(awaitItem().purchaseState is PurchaseStateView.Inactive)
            viewModel.processJsCallbackMessage("test", "subscriptionSelected", "id", JSONObject("{}"))
            assertTrue(awaitItem().purchaseState is PurchaseStateView.Failure)
        }
    }

    @Test
    fun whenSubscriptionSelectedThenSendCommandWithCorrectId() = runTest {
        val json = """
            {"id":"myId"}
        """.trimIndent()
        viewModel.commands().test {
            viewModel.processJsCallbackMessage("test", "subscriptionSelected", "id", JSONObject(json))
            val result = awaitItem()
            assertTrue(result is Command.SubscriptionSelected)
            assertEquals("myId", (result as Command.SubscriptionSelected).id)
        }
    }

    @Test
    fun whenBackToSettingsThenCommandSent() = runTest {
        viewModel.commands().test {
            viewModel.processJsCallbackMessage("test", "backToSettings", "id", JSONObject("{}"))
            assertTrue(awaitItem() is Command.BackToSettings)
        }
    }

    @Test
    fun whenGetSubscriptionOptionsThenSendCommand() = runTest {
        val monthly = getSubscriptionOfferDetails("monthly")
        val yearly = getSubscriptionOfferDetails("yearly")
        whenever(subscriptionsRepository.offerDetail()).thenReturn(mapOf(MONTHLY_PLAN to monthly, YEARLY_PLAN to yearly))

        viewModel.commands().test {
            viewModel.processJsCallbackMessage("test", "getSubscriptionOptions", "id", JSONObject("{}"))
            val result = awaitItem()
            assertTrue(result is Command.SendResponseToJs)
            val response = (result as Command.SendResponseToJs).data

            val params = jsonAdapter.fromJson(response.params.toString())
            assertEquals("id", response.id)
            assertEquals("test", response.featureName)
            assertEquals("getSubscriptionOptions", response.method)
            assertEquals("yearly", params?.options?.first()?.id)
            assertEquals("monthly", params?.options?.last()?.id)
        }
    }

    @Test
    fun whenActivateSubscriptionThenCommandSent() = runTest {
        viewModel.commands().test {
            viewModel.processJsCallbackMessage("test", "activateSubscription", null, null)
            assertTrue(awaitItem() is Command.ActivateOnAnotherDevice)
        }
    }

    private fun getSubscriptionOfferDetails(planId: String): SubscriptionOfferDetails {
        val subscriptionOfferDetails: SubscriptionOfferDetails = mock()
        whenever(subscriptionOfferDetails.basePlanId).thenReturn(planId)
        val phase: PricingPhase = mock()
        val phases: PricingPhases = mock()
        whenever(phase.formattedPrice).thenReturn("$1.1")
        whenever(phases.pricingPhaseList).thenReturn(listOf(phase))
        whenever(subscriptionOfferDetails.pricingPhases).thenReturn(phases)
        return subscriptionOfferDetails
    }
}
