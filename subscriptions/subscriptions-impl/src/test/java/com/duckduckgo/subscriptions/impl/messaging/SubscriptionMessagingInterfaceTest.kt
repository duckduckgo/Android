package com.duckduckgo.subscriptions.impl.messaging

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.JsRequestResponse
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.AccessTokenResult
import com.duckduckgo.subscriptions.impl.AuthTokenResult
import com.duckduckgo.subscriptions.impl.SubscriptionsChecker
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.Subscription
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SubscriptionMessagingInterfaceTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private val webView: WebView = mock()
    private val jsMessageHelper: JsMessageHelper = mock()
    private val subscriptionsManager: SubscriptionsManager = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private val subscriptionsChecker: SubscriptionsChecker = mock()
    private val messagingInterface = SubscriptionMessagingInterface(
        subscriptionsManager,
        jsMessageHelper,
        coroutineRule.testDispatcherProvider,
        coroutineRule.testScope,
        pixelSender,
        subscriptionsChecker,
    )

    private val callback = object : JsMessageCallback() {
        var counter = 0
        var id: String? = null

        override fun process(featureName: String, method: String, id: String?, data: JSONObject?) {
            this.id = id
            counter++
        }
    }

    @Test
    fun whenProcessUnknownMessageDoNothing() = runTest {
        givenInterfaceIsRegistered()

        messagingInterface.process("", "secret")

        verifyNoInteractions(jsMessageHelper)
        verify(webView, never()).evaluateJavascript(any(), any())
    }

    @Test
    fun whenProcessUnknownSecretDoNothing() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"backToSettings","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "secret")

        verifyNoInteractions(jsMessageHelper)
        verify(webView, never()).evaluateJavascript(any(), any())
    }

    @Test
    fun whenProcessNoUrlDoNothing() = runTest {
        messagingInterface.register(webView, callback)

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getSubscription","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
        verify(webView, never()).evaluateJavascript(any(), any())
    }

    @Test
    fun whenProcessInterfaceNotRegisteredDoNothing() = runTest {
        whenever(webView.url).thenReturn("https://duckduckgo.com/test")

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getSubscription","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
        verify(webView, never()).evaluateJavascript(any(), any())
    }

    @Test
    fun whenProcessIfMethodDoesNotMatchDoNothing() = runTest {
        givenInterfaceIsRegistered()
        givenAuthTokenIsSuccess()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"test","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
    }

    @Test
    fun whenProcessAndGetSubscriptionsMessageIfActiveThenReturnResponse() = runTest {
        givenInterfaceIsRegistered()
        givenAuthTokenIsSuccess()
        givenSubscriptionIsActive()

        val expected = JsRequestResponse.Success(
            context = "subscriptionPages",
            featureName = "useSubscription",
            method = "getSubscription",
            id = "myId",
            result = JSONObject("""{ "token":"authToken"}"""),
        )

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getSubscription","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        val captor = argumentCaptor<JsRequestResponse>()
        verify(jsMessageHelper).sendJsResponse(captor.capture(), eq(CALLBACK_NAME), eq(SECRET), eq(webView))
        val jsMessage = captor.firstValue

        assertTrue(jsMessage is JsRequestResponse.Success)
        checkEquals(expected, jsMessage)
    }

    @Test
    fun whenProcessAndGetSubscriptionsMessageIfNotActiveThenReturnResponse() = runTest {
        givenInterfaceIsRegistered()
        givenAuthTokenIsSuccess()

        val expected = JsRequestResponse.Success(
            context = "subscriptionPages",
            featureName = "useSubscription",
            method = "getSubscription",
            id = "myId",
            result = JSONObject("""{ "token":"authToken"}"""),
        )

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getSubscription","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        val captor = argumentCaptor<JsRequestResponse>()
        verify(jsMessageHelper).sendJsResponse(captor.capture(), eq(CALLBACK_NAME), eq(SECRET), eq(webView))
        val jsMessage = captor.firstValue

        assertTrue(jsMessage is JsRequestResponse.Success)
        checkEquals(expected, jsMessage)
    }

    @Test
    fun whenProcessAndGetSubscriptionsMessageErrorThenReturnResponse() = runTest {
        givenInterfaceIsRegistered()
        givenAuthTokenIsFailure()

        val expected = JsRequestResponse.Success(
            context = "subscriptionPages",
            featureName = "useSubscription",
            method = "getSubscription",
            id = "myId",
            result = JSONObject("""{ }"""),
        )

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getSubscription","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        val captor = argumentCaptor<JsRequestResponse>()
        verify(jsMessageHelper).sendJsResponse(captor.capture(), eq(CALLBACK_NAME), eq(SECRET), eq(webView))
        val jsMessage = captor.firstValue

        assertTrue(jsMessage is JsRequestResponse.Success)
        checkEquals(expected, jsMessage)
    }

    @Test
    fun whenProcessAndGetSubscriptionsMessageIfTokenExpiredThenReturnResponse() = runTest {
        givenInterfaceIsRegistered()
        givenAuthTokenIsExpired()

        val expected = JsRequestResponse.Success(
            context = "subscriptionPages",
            featureName = "useSubscription",
            method = "getSubscription",
            id = "myId",
            result = JSONObject("""{ "token":"authToken"}"""),
        )

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getSubscription","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        val captor = argumentCaptor<JsRequestResponse>()
        verify(jsMessageHelper).sendJsResponse(captor.capture(), eq(CALLBACK_NAME), eq(SECRET), eq(webView))
        val jsMessage = captor.firstValue

        assertTrue(jsMessage is JsRequestResponse.Success)
        checkEquals(expected, jsMessage)
    }

    @Test
    fun whenProcessAndGetSubscriptionsIfFeatureNameDoesNotMatchDoNothing() = runTest {
        givenInterfaceIsRegistered()
        givenAuthTokenIsSuccess()

        val message = """
            {"context":"subscriptionPages","featureName":"test","method":"getSubscription","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
    }

    @Test
    fun whenProcessAndGetSubscriptionIfNoIdDoNothing() = runTest {
        givenInterfaceIsRegistered()
        givenAuthTokenIsSuccess()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getSubscription", "params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
    }

    @Test
    fun whenProcessAndGetAccessTokenThenReturnResponse() = runTest {
        givenInterfaceIsRegistered()
        givenAccessTokenIsSuccess()

        val expected = JsRequestResponse.Success(
            context = "subscriptionPages",
            featureName = "useSubscription",
            method = "getAccessToken",
            id = "myId",
            result = JSONObject("""{ token:"accessToken" }"""),
        )

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getAccessToken","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        val captor = argumentCaptor<JsRequestResponse>()
        verify(jsMessageHelper).sendJsResponse(captor.capture(), eq(CALLBACK_NAME), eq(SECRET), eq(webView))
        val jsMessage = captor.firstValue

        assertTrue(jsMessage is JsRequestResponse.Success)
        checkEquals(expected, jsMessage)
    }

    @Test
    fun whenProcessAndGetAccessTokenMessageErrorThenReturnResponse() = runTest {
        givenInterfaceIsRegistered()
        givenAccessTokenIsFailure()

        val expected = JsRequestResponse.Success(
            context = "subscriptionPages",
            featureName = "useSubscription",
            method = "getAccessToken",
            id = "myId",
            result = JSONObject("""{ }"""),
        )

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getAccessToken","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        val captor = argumentCaptor<JsRequestResponse>()
        verify(jsMessageHelper).sendJsResponse(captor.capture(), eq(CALLBACK_NAME), eq(SECRET), eq(webView))
        val jsMessage = captor.firstValue

        assertTrue(jsMessage is JsRequestResponse.Success)
        checkEquals(expected, jsMessage)
    }

    @Test
    fun whenProcessAndGetAccessTokenIfNoIdDoNothing() = runTest {
        givenInterfaceIsRegistered()
        givenAuthTokenIsSuccess()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getAccessToken","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
    }

    @Test
    fun whenProcessAndBackToSettingsIfFeatureNameDoesNotMatchDoNothing() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","id":"myId","method":"backToSettings","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(0, callback.counter)
    }

    @Test
    fun whenProcessAndBackToSettingsThenCallbackExecuted() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","id":"myId","method":"backToSettings","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(1, callback.counter)
    }

    @Test
    fun whenProcessAndSetSubscriptionMessageIfFeatureNameDoesNotMatchDoNothing() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","method":"setSubscription","params":{"token":"authToken"}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(subscriptionsManager)
        verifyNoInteractions(pixelSender)
    }

    @Test
    fun whenProcessAndSetSubscriptionMessageThenAuthenticate() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"setSubscription","params":{"token":"authToken"}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(subscriptionsManager).exchangeAuthToken("authToken")
        verify(pixelSender).reportRestoreUsingEmailSuccess()
        verify(pixelSender).reportSubscriptionActivated()
        assertEquals(0, callback.counter)
    }

    @Test
    fun whenProcessAndSetSubscriptionMessageAndNoTokenThenDoNothing() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"setSubscription","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(subscriptionsManager)
        verifyNoInteractions(pixelSender)
    }

    @Test
    fun whenProcessAndGetSubscriptionOptionsMessageIfFeatureNameDoesNotMatchDoNothing() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","method":"getSubscriptionOptions","id":"id","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(0, callback.counter)
    }

    @Test
    fun whenProcessAndGetSubscriptionOptionsMessageThenCallbackCalled() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getSubscriptionOptions","id":"id","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(1, callback.counter)
    }

    @Test
    fun whenProcessAndGetSubscriptionOptionsMessageAndNoIdThenDoNothing() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getSubscriptionOptions","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(1, callback.counter)
        assertNull(callback.id)
    }

    @Test
    fun whenProcessAndSubscriptionSelectedMessageIfFeatureNameDoesNotMatchDoNothing() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","method":"subscriptionSelected","id":"id","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(0, callback.counter)
    }

    @Test
    fun whenProcessAndSubscriptionSelectedMessageThenCallbackCalled() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionSelected","id":"id","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(1, callback.counter)
    }

    @Test
    fun whenProcessAndActivateSubscriptionIfFeatureNameDoesNotMatchDoNothing() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","id":"myId","method":"activateSubscription","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(0, callback.counter)
    }

    @Test
    fun whenProcessAndActivateSubscriptionThenCallbackExecuted() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","id":"myId","method":"activateSubscription","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(1, callback.counter)
    }

    @Test
    fun whenProcessAndFeatureSelectedIfFeatureNameDoesNotMatchDoNothing() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","id":"myId","method":"featureSelected","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(0, callback.counter)
    }

    @Test
    fun whenProcessAndFeatureSelectedThenCallbackExecuted() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"featureSelected","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(1, callback.counter)
    }

    @Test
    fun whenProcessAndFeatureSelectedMessageIfUrlNotInAllowListedDomainsThenDoNothing() = runTest {
        messagingInterface.register(webView, callback)
        whenever(webView.url).thenReturn("https://duckduckgo.example.com")

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"featureSelected","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(0, callback.counter)
    }

    @Test
    fun whenProcessAndBackToSettingsActivateSuccessIfFeatureNameDoesNotMatchDoNothing() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","id":"myId","method":"backToSettingsActivateSuccess","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(0, callback.counter)
    }

    @Test
    fun whenProcessAndBackToSettingsActiveSuccessThenCallbackExecuted() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","id":"myId","method":"backToSettingsActivateSuccess","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(1, callback.counter)
    }

    @Test
    fun whenProcessAndMonthlyPriceClickedThenPixelSent() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsMonthlyPriceClicked","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(pixelSender).reportMonthlyPriceClick()
        verifyNoMoreInteractions(pixelSender)
    }

    @Test
    fun whenProcessAndYearlyPriceClickedThenPixelSent() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsYearlyPriceClicked","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(pixelSender).reportYearlyPriceClick()
        verifyNoMoreInteractions(pixelSender)
    }

    @Test
    fun whenProcessAndAddEmailSuccessThenPixelSent() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsAddEmailSuccess","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(pixelSender).reportAddEmailSuccess()
        verifyNoMoreInteractions(pixelSender)
    }

    @Test
    fun whenProcessAndFaqClickedThenCallbackExecuted() = runTest {
        val jsMessageCallback: JsMessageCallback = mock()
        messagingInterface.register(webView, jsMessageCallback)
        whenever(webView.url).thenReturn("https://duckduckgo.com/test")

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsWelcomeFaqClicked","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(jsMessageCallback).process(eq("useSubscription"), eq("subscriptionsWelcomeFaqClicked"), any(), any())
    }

    @Test
    fun whenProcessAndAddEmailClickedThenCallbackExecuted() = runTest {
        val jsMessageCallback: JsMessageCallback = mock()
        messagingInterface.register(webView, jsMessageCallback)
        whenever(webView.url).thenReturn("https://duckduckgo.com/test")

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsWelcomeAddEmailClicked","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(jsMessageCallback).process(eq("useSubscription"), eq("subscriptionsWelcomeAddEmailClicked"), any(), any())
    }

    @Test
    fun whenProcessAndAddEmailSuccessAnIsSignedInUsingAuthV2AThenAccessTokenIsRefreshed() = runTest {
        givenInterfaceIsRegistered()
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(true)

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsAddEmailSuccess","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(subscriptionsManager).refreshAccessToken()
    }

    @Test
    fun whenProcessAndEditEmailSuccessAnIsSignedInUsingAuthV2AThenAccessTokenIsRefreshed() = runTest {
        givenInterfaceIsRegistered()
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(true)

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsEditEmailSuccess","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(subscriptionsManager).refreshAccessToken()
    }

    @Test
    fun whenProcessAndAddEmailSuccessAnIsNotSignedInUsingAuthV2AThenAccessTokenIsNotRefreshed() = runTest {
        givenInterfaceIsRegistered()
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(false)

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsAddEmailSuccess","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(subscriptionsManager, never()).refreshAccessToken()
    }

    @Test
    fun whenProcessAndEditEmailSuccessAnIsNotSignedInUsingAuthV2AThenAccessTokenIsNotRefreshed() = runTest {
        givenInterfaceIsRegistered()
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(false)

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsEditEmailSuccess","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(subscriptionsManager, never()).refreshAccessToken()
    }

    private fun givenInterfaceIsRegistered() {
        messagingInterface.register(webView, callback)
        whenever(webView.url).thenReturn("https://duckduckgo.com/test")
    }

    private suspend fun givenSubscriptionIsActive() {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = "productId",
                startedAt = 10000L,
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "google",
            ),
        )
    }

    private suspend fun givenAuthTokenIsSuccess() {
        whenever(subscriptionsManager.getAuthToken()).thenReturn(AuthTokenResult.Success(authToken = "authToken"))
    }

    private suspend fun givenAuthTokenIsFailure() {
        whenever(subscriptionsManager.getAuthToken()).thenReturn(AuthTokenResult.Failure.UnknownError)
    }

    private suspend fun givenAuthTokenIsExpired() {
        whenever(subscriptionsManager.getAuthToken()).thenReturn(AuthTokenResult.Failure.TokenExpired(authToken = "authToken"))
    }

    private suspend fun givenAccessTokenIsSuccess() {
        whenever(subscriptionsManager.getAccessToken()).thenReturn(AccessTokenResult.Success(accessToken = "accessToken"))
    }

    private suspend fun givenAccessTokenIsFailure() {
        whenever(subscriptionsManager.getAccessToken()).thenReturn(AccessTokenResult.Failure(message = "something happened"))
    }

    private fun checkEquals(expected: JsRequestResponse, actual: JsRequestResponse) {
        if (expected is JsRequestResponse.Success && actual is JsRequestResponse.Success) {
            assertEquals(expected.id, actual.id)
            assertEquals(expected.context, actual.context)
            assertEquals(expected.featureName, actual.featureName)
            assertEquals(expected.method, actual.method)
            assertEquals(expected.result.toString(), actual.result.toString())
        } else if (expected is JsRequestResponse.Error && actual is JsRequestResponse.Error) {
            assertEquals(expected.id, actual.id)
            assertEquals(expected.context, actual.context)
            assertEquals(expected.featureName, actual.featureName)
            assertEquals(expected.method, actual.method)
            assertEquals(expected.error, actual.error)
        } else {
            assertTrue(false)
        }
    }

    companion object {
        private const val CALLBACK_NAME = "messageCallback"
        private const val SECRET = "duckduckgo-android-messaging-secret"
    }
}
