package com.duckduckgo.subscriptions.impl.messaging

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.Subscription
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealSubscriptionsJSHelperTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockSubscriptionsManager: SubscriptionsManager = mock()

    private val testee = RealSubscriptionsJSHelper(mockSubscriptionsManager)

    private val featureName = "subscriptions"

    @Test
    fun whenMethodIsUnknownThenReturnNull() = runTest {
        val method = "unknownMethod"
        val id = "123"

        val result = testee.processJsCallbackMessage(featureName, method, id, null)

        assertNull(result)
    }

    @Test
    fun whenHandshakeRequestAndIdIsNullThenReturnNull() = runTest {
        val method = "handshake"

        val result = testee.processJsCallbackMessage(featureName, method, null, null)

        assertNull(result)
    }

    @Test
    fun whenHandshakeRequestThenReturnJsCallbackDataWithAvailableMessages() = runTest {
        val method = "handshake"
        val id = "123"

        val result = testee.processJsCallbackMessage(featureName, method, id, null)

        val jsonPayload = JSONObject().apply {
            put("availableMessages", JSONArray().put("subscriptionDetails"))
            put("platform", "android")
        }

        val expected = JsCallbackData(jsonPayload, featureName, method, id)

        assertEquals(expected.id, result!!.id)
        assertEquals(expected.method, result.method)
        assertEquals(expected.featureName, result.featureName)
        assertEquals(expected.params.toString(), result.params.toString())
    }

    @Test
    fun givenAnActiveSubscriptionWhenSubscriptionDetailsRequestThenReturnJsCallbackDataWithIsSubscribedFalse() = runTest {
        val method = "subscriptionDetails"
        val id = "123"

        whenever(mockSubscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.YEARLY_PLAN_US,
                billingPeriod = MONTHLY,
                startedAt = 1709052033000L,
                expiresOrRenewsAt = 1711557633000L,
                status = AUTO_RENEWABLE,
                platform = "Google",
                activeOffers = listOf(),
            ),
        )

        val result = testee.processJsCallbackMessage(featureName, method, id, null)

        val jsonPayload = JSONObject().apply {
            put("isSubscribed", true)
            put("billingPeriod", "Monthly")
            put("startedAt", 1709052033000L)
            put("expiresOrRenewsAt", 1711557633000L)
            put("paymentPlatform", "Google")
            put("status", "Auto-Renewable")
        }

        val expected = JsCallbackData(jsonPayload, featureName, method, id)

        assertEquals(expected.id, result!!.id)
        assertEquals(expected.method, result.method)
        assertEquals(expected.featureName, result.featureName)
        assertEquals(expected.params.toString(), result.params.toString())
    }

    @Test
    fun givenNoSubscriptionWhenSubscriptionDetailsRequestThenReturnJsCallbackDataWithSubscriptionDetails() = runTest {
        val method = "subscriptionDetails"
        val id = "123"

        whenever(mockSubscriptionsManager.getSubscription()).thenReturn(null)

        val result = testee.processJsCallbackMessage(featureName, method, id, null)

        val jsonPayload = JSONObject().apply {
            put("isSubscribed", false)
        }

        val expected = JsCallbackData(jsonPayload, featureName, method, id)

        assertEquals(expected.id, result!!.id)
        assertEquals(expected.method, result.method)
        assertEquals(expected.featureName, result.featureName)
        assertEquals(expected.params.toString(), result.params.toString())
    }

    @Test
    fun givenAnExpiredSubscriptionWhenSubscriptionDetailsRequestThenReturnJsCallbackDataWithSubscriptionDetails() = runTest {
        val method = "subscriptionDetails"
        val id = "123"

        whenever(mockSubscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = SubscriptionsConstants.YEARLY_PLAN_US,
                billingPeriod = YEARLY,
                startedAt = 1709052033000L,
                expiresOrRenewsAt = 1711557633000L,
                status = EXPIRED,
                platform = "stripe",
                activeOffers = listOf(),
            ),
        )

        val result = testee.processJsCallbackMessage(featureName, method, id, null)

        val jsonPayload = JSONObject().apply {
            put("isSubscribed", false)
            put("billingPeriod", "Yearly")
            put("startedAt", 1709052033000L)
            put("expiresOrRenewsAt", 1711557633000L)
            put("paymentPlatform", "stripe")
            put("status", "Expired")
        }

        val expected = JsCallbackData(jsonPayload, featureName, method, id)

        assertEquals(expected.id, result!!.id)
        assertEquals(expected.method, result.method)
        assertEquals(expected.featureName, result.featureName)
        assertEquals(expected.params.toString(), result.params.toString())
    }
}
