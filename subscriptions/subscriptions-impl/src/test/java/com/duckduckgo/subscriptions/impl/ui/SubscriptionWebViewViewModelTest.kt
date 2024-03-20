package com.duckduckgo.subscriptions.impl.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetPWaitlistInvitedScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist
import com.duckduckgo.subscriptions.impl.CurrentPurchase
import com.duckduckgo.subscriptions.impl.JSONObjectAdapter
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.SubscriptionOffer
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.impl.SubscriptionsChecker
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Companion
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView.Success
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.SubscriptionOptionsJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SubscriptionWebViewViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
    private val jsonAdapter: JsonAdapter<SubscriptionOptionsJson> = moshi.adapter(SubscriptionOptionsJson::class.java)
    private val subscriptionsManager: SubscriptionsManager = mock()
    private val networkProtectionWaitlist: NetworkProtectionWaitlist = mock()
    private val subscriptionsChecker: SubscriptionsChecker = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private val privacyProFeature = FakeFeatureToggleFactory.create(PrivacyProFeature::class.java, FakeToggleStore())

    private lateinit var viewModel: SubscriptionWebViewViewModel

    @Before
    fun setup() = runTest {
        whenever(networkProtectionWaitlist.getScreenForCurrentState()).thenReturn(NetPWaitlistInvitedScreenNoParams)
        viewModel = SubscriptionWebViewViewModel(
            coroutineTestRule.testDispatcherProvider,
            subscriptionsManager,
            subscriptionsChecker,
            networkProtectionWaitlist,
            pixelSender,
            privacyProFeature,
        )
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
    fun whenPurchaseStateFailedThenSendCanceledMessage() = runTest {
        val flowTest: MutableSharedFlow<CurrentPurchase> = MutableSharedFlow()
        whenever(subscriptionsManager.currentPurchaseState).thenReturn(flowTest)
        viewModel.start()

        viewModel.commands().test {
            flowTest.emit(CurrentPurchase.Failure("test"))

            val result = awaitItem()
            assertTrue(result is Command.SendJsEvent)
            assertEquals("{\"type\":\"canceled\"}", (result as Command.SendJsEvent).event.params.toString())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPurchaseStateCanceledThenSendCanceledMessage() = runTest {
        val flowTest: MutableSharedFlow<CurrentPurchase> = MutableSharedFlow()
        whenever(subscriptionsManager.currentPurchaseState).thenReturn(flowTest)
        viewModel.start()

        viewModel.commands().test {
            flowTest.emit(CurrentPurchase.Canceled)

            val result = awaitItem()
            assertTrue(result is Command.SendJsEvent)
            assertEquals("{\"type\":\"canceled\"}", (result as Command.SendJsEvent).event.params.toString())

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
    fun whenBackToSettingsActivateSuccessThenCommandSent() = runTest {
        viewModel.commands().test {
            viewModel.processJsCallbackMessage("test", "backToSettingsActivateSuccess", "id", JSONObject("{}"))
            assertTrue(awaitItem() is Command.BackToSettingsActivateSuccess)
        }
    }

    @Test
    fun whenGetSubscriptionOptionsThenSendCommand() = runTest {
        privacyProFeature.allowPurchase().setEnabled(Toggle.State(enable = true))

        whenever(subscriptionsManager.getSubscriptionOffer()).thenReturn(
            SubscriptionOffer(
                monthlyPlanId = "monthly",
                monthlyFormattedPrice = "$1",
                yearlyPlanId = "yearly",
                yearlyFormattedPrice = "$10",
            ),
        )

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
    fun whenGetSubscriptionsAndNoSubscriptionOfferThenSendCommandWithEmptyData() = runTest {
        privacyProFeature.allowPurchase().setEnabled(Toggle.State(enable = true))

        viewModel.commands().test {
            viewModel.processJsCallbackMessage("test", "getSubscriptionOptions", "id", JSONObject("{}"))

            val result = awaitItem()
            assertTrue(result is Command.SendResponseToJs)

            val response = (result as Command.SendResponseToJs).data
            assertEquals("id", response.id)
            assertEquals("test", response.featureName)
            assertEquals("getSubscriptionOptions", response.method)

            val params = jsonAdapter.fromJson(response.params.toString())!!
            assertEquals(0, params.options.size)
            assertEquals(0, params.features.size)
        }
    }

    @Test
    fun whenGetSubscriptionsAndToggleOffThenSendCommandWithEmptyData() = runTest {
        privacyProFeature.allowPurchase().setEnabled(Toggle.State(enable = false))
        whenever(subscriptionsManager.getSubscriptionOffer()).thenReturn(
            SubscriptionOffer(
                monthlyPlanId = "monthly",
                monthlyFormattedPrice = "$1",
                yearlyPlanId = "yearly",
                yearlyFormattedPrice = "$10",
            ),
        )

        viewModel.commands().test {
            viewModel.processJsCallbackMessage("test", "getSubscriptionOptions", "id", JSONObject("{}"))

            val result = awaitItem()
            assertTrue(result is Command.SendResponseToJs)

            val response = (result as Command.SendResponseToJs).data
            assertEquals("id", response.id)
            assertEquals("test", response.featureName)
            assertEquals("getSubscriptionOptions", response.method)

            val params = jsonAdapter.fromJson(response.params.toString())!!
            assertEquals(0, params.options.size)
            assertEquals(0, params.features.size)
        }
    }

    @Test
    fun whenActivateSubscriptionAndSubscriptionActiveThenNoCommandSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        viewModel.commands().test {
            expectNoEvents()
        }
    }

    @Test
    fun whenActivateSubscriptionAndSubscriptionInactiveThenCommandSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(INACTIVE)
        viewModel.commands().test {
            viewModel.processJsCallbackMessage("test", "activateSubscription", null, null)
            assertTrue(awaitItem() is Command.RestoreSubscription)
        }
    }

    @Test
    fun whenFeatureSelectedAndNoDataThenCommandNotSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(EXPIRED)
        viewModel.commands().test {
            viewModel.processJsCallbackMessage("test", "featureSelected", null, null)
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun whenFeatureSelectedAndInvalidDataThenCommandNotSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(EXPIRED)
        viewModel.commands().test {
            viewModel.processJsCallbackMessage("test", "featureSelected", null, JSONObject("{}"))
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun whenFeatureSelectedAndInvalidFeatureThenCommandNotSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(EXPIRED)
        viewModel.commands().test {
            viewModel.processJsCallbackMessage("test", "featureSelected", null, JSONObject("""{"feature":"test"}"""))
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun whenFeatureSelectedAndFeatureIsNetPThenCommandSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(EXPIRED)
        viewModel.commands().test {
            viewModel.processJsCallbackMessage(
                "test",
                "featureSelected",
                null,
                JSONObject("""{"feature":"${SubscriptionsConstants.NETP}"}"""),
            )
            assertTrue(awaitItem() is Command.GoToNetP)
        }
    }

    @Test
    fun whenFeatureSelectedAndFeatureIsItrThenCommandSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(EXPIRED)
        viewModel.commands().test {
            viewModel.processJsCallbackMessage(
                "test",
                "featureSelected",
                null,
                JSONObject("""{"feature":"${SubscriptionsConstants.ITR}"}"""),
            )
            assertTrue(awaitItem() is Command.GoToITR)
        }
    }

    @Test
    fun whenFeatureSelectedAndFeatureIsPirThenCommandSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(EXPIRED)
        viewModel.commands().test {
            viewModel.processJsCallbackMessage(
                "test",
                "featureSelected",
                null,
                JSONObject("""{"feature":"${SubscriptionsConstants.PIR}"}"""),
            )
            assertTrue(awaitItem() is Command.GoToPIR)
        }
    }

    @Test
    fun whenSubscriptionSelectedThenPixelIsSent() = runTest {
        viewModel.processJsCallbackMessage(
            featureName = "test",
            method = "subscriptionSelected",
            id = "id",
            data = JSONObject("""{"id":"myId"}"""),
        )
        verify(pixelSender).reportOfferSubscribeClick()
    }

    @Test
    fun whenRestorePurchaseClickedThenPixelIsSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(EXPIRED)
        viewModel.processJsCallbackMessage(
            featureName = "test",
            method = "activateSubscription",
            id = null,
            data = null,
        )
        verify(pixelSender).reportOfferRestorePurchaseClick()
    }

    @Test
    fun whenAddEmailClickedAndInPurchaseFlowThenPixelIsSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(subscriptionsManager.currentPurchaseState).thenReturn(flowOf(CurrentPurchase.Success))
        viewModel.start()

        viewModel.processJsCallbackMessage(
            featureName = "test",
            method = "subscriptionsWelcomeAddEmailClicked",
            id = null,
            data = null,
        )
        verify(pixelSender).reportOnboardingAddDeviceClick()
    }

    @Test
    fun whenAddEmailClickedAndNotInPurchaseFlowThenPixelIsNotSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)

        viewModel.processJsCallbackMessage(
            featureName = "test",
            method = "subscriptionsWelcomeAddEmailClicked",
            id = null,
            data = null,
        )
        verifyNoInteractions(pixelSender)
    }

    @Test
    fun whenFeatureSelectedAndFeatureIsNetPAndInPurchaseFlowThenPixelIsSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(subscriptionsManager.currentPurchaseState).thenReturn(flowOf(CurrentPurchase.Success))
        viewModel.start()

        viewModel.processJsCallbackMessage(
            featureName = "test",
            method = "featureSelected",
            id = null,
            data = JSONObject("""{"feature":"${SubscriptionsConstants.NETP}"}"""),
        )
        verify(pixelSender).reportOnboardingVpnClick()
    }

    @Test
    fun whenFeatureSelectedAndFeatureIsNetPAndNotInPurchaseFlowThenPixelIsNotSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)

        viewModel.processJsCallbackMessage(
            featureName = "test",
            method = "featureSelected",
            id = null,
            data = JSONObject("""{"feature":"${SubscriptionsConstants.NETP}"}"""),
        )
        verifyNoInteractions(pixelSender)
    }

    @Test
    fun whenFeatureSelectedAndFeatureIsItrAndInPurchaseFlowThenPixelIsSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(subscriptionsManager.currentPurchaseState).thenReturn(flowOf(CurrentPurchase.Success))
        viewModel.start()

        viewModel.processJsCallbackMessage(
            featureName = "test",
            method = "featureSelected",
            id = null,
            data = JSONObject("""{"feature":"${SubscriptionsConstants.ITR}"}"""),
        )
        verify(pixelSender).reportOnboardingIdtrClick()
    }

    @Test
    fun whenFeatureSelectedAndFeatureIsItrAndNotInPurchaseFlowThenPixelIsNotSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)

        viewModel.processJsCallbackMessage(
            featureName = "test",
            method = "featureSelected",
            id = null,
            data = JSONObject("""{"feature":"${SubscriptionsConstants.ITR}"}"""),
        )
        verifyNoInteractions(pixelSender)
    }

    @Test
    fun whenFeatureSelectedAndFeatureIsPirAndInPurchaseFlowThenPixelIsSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(subscriptionsManager.currentPurchaseState).thenReturn(flowOf(CurrentPurchase.Success))
        viewModel.start()

        viewModel.processJsCallbackMessage(
            featureName = "test",
            method = "featureSelected",
            id = null,
            data = JSONObject("""{"feature":"${SubscriptionsConstants.PIR}"}"""),
        )
        verify(pixelSender).reportOnboardingPirClick()
    }

    @Test
    fun whenFeatureSelectedAndFeatureIsPirAndNotInPurchaseFlowThenPixelIsNotSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)

        viewModel.processJsCallbackMessage(
            featureName = "test",
            method = "featureSelected",
            id = null,
            data = JSONObject("""{"feature":"${SubscriptionsConstants.PIR}"}"""),
        )
        verifyNoInteractions(pixelSender)
    }

    @Test
    fun whenSubscriptionsWelcomeFaqClickedAndInPurchaseFlowThenPixelIsSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(subscriptionsManager.currentPurchaseState).thenReturn(flowOf(CurrentPurchase.Success))
        viewModel.start()

        viewModel.processJsCallbackMessage(
            featureName = "test",
            method = "subscriptionsWelcomeFaqClicked",
            id = null,
            data = null,
        )
        verify(pixelSender).reportOnboardingFaqClick()
    }

    @Test
    fun whenSubscriptionsWelcomeFaqClickedAndNotInPurchaseFlowThenPixelIsNotSent() = runTest {
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)

        viewModel.processJsCallbackMessage(
            featureName = "test",
            method = "subscriptionsWelcomeFaqClicked",
            id = null,
            data = null,
        )
        verifyNoInteractions(pixelSender)
    }
}
