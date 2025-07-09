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
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.SubscriptionsChecker
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.Subscription
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private val privacyProFeature: PrivacyProFeature = mock()
    private val messagingInterface = SubscriptionMessagingInterface(
        subscriptionsManager,
        jsMessageHelper,
        coroutineRule.testDispatcherProvider,
        coroutineRule.testScope,
        pixelSender,
        subscriptionsChecker,
        privacyProFeature,
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
    fun `when process unknown message do nothing`() = runTest {
        givenInterfaceIsRegistered()

        messagingInterface.process("", "secret")

        verifyNoInteractions(jsMessageHelper)
        verify(webView, never()).evaluateJavascript(any(), any())
    }

    @Test
    fun `when process unknown secret do nothing`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"backToSettings","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "secret")

        verifyNoInteractions(jsMessageHelper)
        verify(webView, never()).evaluateJavascript(any(), any())
    }

    @Test
    fun `when process no url do nothing`() = runTest {
        messagingInterface.register(webView, callback)

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getSubscription","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
        verify(webView, never()).evaluateJavascript(any(), any())
    }

    @Test
    fun `when process interface not registered do nothing`() = runTest {
        whenever(webView.url).thenReturn("https://duckduckgo.com/test")

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getSubscription","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
        verify(webView, never()).evaluateJavascript(any(), any())
    }

    @Test
    fun `when process if method does not match do nothing`() = runTest {
        givenInterfaceIsRegistered()
        givenAuthTokenIsSuccess()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"test","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
    }

    @Test
    fun `when process and get subscriptions message if active then return response`() = runTest {
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
    fun `when process and get subscriptions message if not active then return response`() = runTest {
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
    fun `when process and get subscriptions message error then return response`() = runTest {
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
    fun `when process and get subscriptions message if token expired then return response`() = runTest {
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
    fun `when process and get subscriptions if feature name does not match do nothing`() = runTest {
        givenInterfaceIsRegistered()
        givenAuthTokenIsSuccess()

        val message = """
            {"context":"subscriptionPages","featureName":"test","method":"getSubscription","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
    }

    @Test
    fun `when process and get subscription if no id do nothing`() = runTest {
        givenInterfaceIsRegistered()
        givenAuthTokenIsSuccess()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getSubscription", "params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
    }

    @Test
    fun `when process and get access token then return response`() = runTest {
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
    fun `when process and get access token message error then return response`() = runTest {
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
    fun `when process and get access token if no id do nothing`() = runTest {
        givenInterfaceIsRegistered()
        givenAuthTokenIsSuccess()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getAccessToken","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
    }

    @Test
    fun `when process and back to settings if feature name does not match do nothing`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","id":"myId","method":"backToSettings","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(0, callback.counter)
    }

    @Test
    fun `when process and back to settings then callback executed`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","id":"myId","method":"backToSettings","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(1, callback.counter)
    }

    @Test
    fun `when process and set subscription message if feature name does not match do nothing`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","method":"setSubscription","params":{"token":"authToken"}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(subscriptionsManager)
        verifyNoInteractions(pixelSender)
    }

    @Test
    fun `when process and set subscription message then authenticate`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"setSubscription","params":{"token":"authToken"}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(subscriptionsManager).signInV1("authToken")
        verify(pixelSender).reportRestoreUsingEmailSuccess()
        verify(pixelSender).reportSubscriptionActivated()
        assertEquals(0, callback.counter)
    }

    @Test
    fun `when process and set subscription message and no token then do nothing`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"setSubscription","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(subscriptionsManager)
        verifyNoInteractions(pixelSender)
    }

    @Test
    fun `when process and setAuthTokens message then authenticate`() = runTest {
        givenInterfaceIsRegistered()

        val params = """{"accessToken":"accessToken","refreshToken":"refreshToken"}"""
        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"setAuthTokens","params":$params}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(subscriptionsManager).signInV2(accessToken = "accessToken", refreshToken = "refreshToken")
        verify(pixelSender).reportRestoreUsingEmailSuccess()
        verify(pixelSender).reportSubscriptionActivated()
        assertEquals(0, callback.counter)
    }

    @Test
    fun `when process and setAuthTokens message and no tokens then do nothing`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"setAuthTokens","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(subscriptionsManager)
        verifyNoInteractions(pixelSender)
    }

    @Test
    fun `when process and get subscription options message if feature name does not match do nothing`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","method":"getSubscriptionOptions","id":"id","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(0, callback.counter)
    }

    @Test
    fun `when process and get subscription options message then callback called`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getSubscriptionOptions","id":"id","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(1, callback.counter)
    }

    @Test
    fun `when process and get subscription options message and no id then do nothing`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getSubscriptionOptions","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(1, callback.counter)
        assertNull(callback.id)
    }

    @Test
    fun `when process and subscription selected message if feature name does not match do nothing`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","method":"subscriptionSelected","id":"id","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(0, callback.counter)
    }

    @Test
    fun `when process and subscription selected message then callback called`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionSelected","id":"id","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(1, callback.counter)
    }

    @Test
    fun `when process and activate subscription if feature name does not match do nothing`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","id":"myId","method":"activateSubscription","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(0, callback.counter)
    }

    @Test
    fun `when process and activate subscription then callback executed`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","id":"myId","method":"activateSubscription","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(1, callback.counter)
    }

    @Test
    fun `when process and feature selected if feature name does not match do nothing`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","id":"myId","method":"featureSelected","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(0, callback.counter)
    }

    @Test
    fun `when process and feature selected then callback executed`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"featureSelected","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(1, callback.counter)
    }

    @Test
    fun `when process and feature selected message if url not in allow listed domains then do nothing`() = runTest {
        messagingInterface.register(webView, callback)
        whenever(webView.url).thenReturn("https://duckduckgo.example.com")

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"featureSelected","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(0, callback.counter)
    }

    @Test
    fun `when process and back to settings activate success if feature name does not match do nothing`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","id":"myId","method":"backToSettingsActivateSuccess","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(0, callback.counter)
    }

    @Test
    fun `when process and back to settings active success then callback executed`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","id":"myId","method":"backToSettingsActivateSuccess","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        assertEquals(1, callback.counter)
    }

    @Test
    fun `when process and monthly price clicked then pixel sent`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsMonthlyPriceClicked","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(pixelSender).reportMonthlyPriceClick()
        // verifyNoMoreInteractions(pixelSender) Add it back when Free Trials experiment is removed
    }

    @Test
    fun `when process and yearly price clicked then pixel sent`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsYearlyPriceClicked","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(pixelSender).reportYearlyPriceClick()
        // verifyNoMoreInteractions(pixelSender) Add it back when Free Trials experiment is removed
    }

    @Test
    fun `when process and add email success then pixel sent`() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsAddEmailSuccess","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(pixelSender).reportAddEmailSuccess()
        verifyNoMoreInteractions(pixelSender)
    }

    @Test
    fun `when process and faq clicked then callback executed`() = runTest {
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
    fun `when process and add email clicked then callback executed`() = runTest {
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
    fun `when process and add email success and is signed in using auth v2 then access token is refreshed`() = runTest {
        givenInterfaceIsRegistered()
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(true)

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsAddEmailSuccess","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(subscriptionsManager).refreshAccessToken()
    }

    @Test
    fun `when process and edit email success and is signed in using auth v2 then access token is refreshed`() = runTest {
        givenInterfaceIsRegistered()
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(true)

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsEditEmailSuccess","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(subscriptionsManager).refreshAccessToken()
    }

    @Test
    fun `when process and remove email success and is signed in using auth v2 then access token is refreshed`() = runTest {
        givenInterfaceIsRegistered()
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(true)

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsRemoveEmailSuccess","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(subscriptionsManager).refreshAccessToken()
    }

    @Test
    fun `when process and add email success an is not signed in using auth v2 then access token is not refreshed`() = runTest {
        givenInterfaceIsRegistered()
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(false)

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsAddEmailSuccess","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(subscriptionsManager, never()).refreshAccessToken()
    }

    @Test
    fun `when process and edit email success and is not signed in using auth v2 then access token is not refreshed`() = runTest {
        givenInterfaceIsRegistered()
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(false)

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsEditEmailSuccess","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(subscriptionsManager, never()).refreshAccessToken()
    }

    @Test
    fun `when process and remove email success and is not signed in using auth v2 then access token is not refreshed`() = runTest {
        givenInterfaceIsRegistered()
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(false)

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"subscriptionsRemoveEmailSuccess","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(subscriptionsManager, never()).refreshAccessToken()
    }

    @Test
    fun `when process and get auth access token then return response`() = runTest {
        givenInterfaceIsRegistered()
        givenAccessTokenIsSuccess()

        val expected = JsRequestResponse.Success(
            context = "subscriptionPages",
            featureName = "useSubscription",
            method = "getAuthAccessToken",
            id = "myId",
            result = JSONObject("""{"accessToken":"accessToken"}"""),
        )

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getAuthAccessToken","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        val captor = argumentCaptor<JsRequestResponse>()
        verify(jsMessageHelper).sendJsResponse(captor.capture(), eq(CALLBACK_NAME), eq(SECRET), eq(webView))
        val jsMessage = captor.firstValue

        assertTrue(jsMessage is JsRequestResponse.Success)
        checkEquals(expected, jsMessage)
    }

    @Test
    fun `when process and get auth access token message error then return response`() = runTest {
        givenInterfaceIsRegistered()
        givenAccessTokenIsFailure()

        val expected = JsRequestResponse.Success(
            context = "subscriptionPages",
            featureName = "useSubscription",
            method = "getAuthAccessToken",
            id = "myId",
            result = JSONObject("""{}"""),
        )

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getAuthAccessToken","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        val captor = argumentCaptor<JsRequestResponse>()
        verify(jsMessageHelper).sendJsResponse(captor.capture(), eq(CALLBACK_NAME), eq(SECRET), eq(webView))
        val jsMessage = captor.firstValue

        assertTrue(jsMessage is JsRequestResponse.Success)
        checkEquals(expected, jsMessage)
    }

    @Test
    fun `when process and get auth access token if no id do nothing`() = runTest {
        givenInterfaceIsRegistered()
        givenAccessTokenIsSuccess()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getAuthAccessToken","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
    }

    @Test
    fun `when process and get feature config and messaging enabled then return response with feature flags`() = runTest {
        givenInterfaceIsRegistered()
        givenSubscriptionMessaging(enabled = true)
        givenAuthV2(enabled = true)

        val expected = JsRequestResponse.Success(
            context = "subscriptionPages",
            featureName = "useSubscription",
            method = "getFeatureConfig",
            id = "myId",
            result = JSONObject("""{"useSubscriptionsAuthV2":true}"""),
        )

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getFeatureConfig","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        val captor = argumentCaptor<JsRequestResponse>()
        verify(jsMessageHelper).sendJsResponse(captor.capture(), eq(CALLBACK_NAME), eq(SECRET), eq(webView))
        val jsMessage = captor.firstValue

        assertTrue(jsMessage is JsRequestResponse.Success)
        checkEquals(expected, jsMessage)
    }

    @Test
    fun `when process and get feature config and messaging enabled but auth v2 disabled then return response with auth v2 false`() = runTest {
        givenInterfaceIsRegistered()
        givenSubscriptionMessaging(enabled = true)
        givenAuthV2(enabled = false)

        val expected = JsRequestResponse.Success(
            context = "subscriptionPages",
            featureName = "useSubscription",
            method = "getFeatureConfig",
            id = "myId",
            result = JSONObject("""{"useSubscriptionsAuthV2":false}"""),
        )

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getFeatureConfig","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        val captor = argumentCaptor<JsRequestResponse>()
        verify(jsMessageHelper).sendJsResponse(captor.capture(), eq(CALLBACK_NAME), eq(SECRET), eq(webView))
        val jsMessage = captor.firstValue

        assertTrue(jsMessage is JsRequestResponse.Success)
        checkEquals(expected, jsMessage)
    }

    @Test
    fun `when process and get feature config and messaging disabled then do nothing`() = runTest {
        givenInterfaceIsRegistered()
        givenSubscriptionMessaging(enabled = false)

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getFeatureConfig","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
    }

    @Test
    fun `when process and get feature config if no id do nothing`() = runTest {
        givenInterfaceIsRegistered()
        givenSubscriptionMessaging(enabled = true)

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getFeatureConfig","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
    }

    private fun givenInterfaceIsRegistered() {
        messagingInterface.register(webView, callback)
        whenever(webView.url).thenReturn("https://duckduckgo.com/test")
    }

    private suspend fun givenSubscriptionIsActive() {
        whenever(subscriptionsManager.getSubscription()).thenReturn(
            Subscription(
                productId = "productId",
                billingPeriod = "Monthly",
                startedAt = 10000L,
                expiresOrRenewsAt = 10000L,
                status = AUTO_RENEWABLE,
                platform = "google",
                activeOffers = listOf(),
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

    private fun givenSubscriptionMessaging(enabled: Boolean) {
        val subscriptionMessagingToggle = mock<com.duckduckgo.feature.toggles.api.Toggle>()
        whenever(subscriptionMessagingToggle.isEnabled()).thenReturn(enabled)
        whenever(privacyProFeature.enableNewSubscriptionMessages()).thenReturn(subscriptionMessagingToggle)
    }

    private fun givenAuthV2(enabled: Boolean) {
        val v2SubscriptionFlow = mock<com.duckduckgo.feature.toggles.api.Toggle>()
        whenever(v2SubscriptionFlow.isEnabled()).thenReturn(enabled)
        whenever(privacyProFeature.enableSubscriptionFlowsV2()).thenReturn(v2SubscriptionFlow)
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
