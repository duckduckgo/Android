package com.duckduckgo.subscriptions.impl.messaging

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.JsRequestResponse
import com.duckduckgo.subscriptions.impl.AuthToken
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SubscriptionMessagingInterfaceTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private val webView: WebView = mock()
    private val jsMessageHelper: JsMessageHelper = mock()
    private val subscriptionsManager: SubscriptionsManager = mock()
    private val messagingInterface = SubscriptionMessagingInterface(
        subscriptionsManager,
        jsMessageHelper,
        coroutineRule.testDispatcherProvider,
        TestScope(),
    )

    private val callback = object : JsMessageCallback() {
        var counter = 0

        override fun process(featureName: String, method: String, id: String, data: JSONObject) {
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
        whenever(webView.url).thenReturn("https://abrown.duckduckgo.com")

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
    fun whenProcessAndGetSubscriptionsMessageThenReturnResponse() = runTest {
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
    fun whenProcessAndGetSubscriptionsIfNoIdDoNothing() = runTest {
        givenInterfaceIsRegistered()
        givenAuthTokenIsSuccess()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"getSubscription", "params":{}}
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

        verifyNoInteractions(jsMessageHelper)
        assertEquals(0, callback.counter)
    }

    @Test
    fun whenProcessAndBackToSettingsThenCallbackExecutedAndNotResponseSent() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","id":"myId","method":"backToSettings","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
        assertEquals(1, callback.counter)
    }

    @Test
    fun whenProcessAndBackToSettingsIfIdDoesNotExistThenDoNothing() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","method":"backToSettings","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
        assertEquals(0, callback.counter)
    }

    @Test
    fun whenProcessAndSetSubscriptionMessageIfFeatureNameDoesNotMatchDoNothing() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"test","method":"setSubscription","params":{"token":"authToken"}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(subscriptionsManager)
    }

    @Test
    fun whenProcessAndSetSubscriptionMessageThenAuthenticate() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"setSubscription","params":{"token":"authToken"}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verify(subscriptionsManager).authenticate("authToken")
    }

    @Test
    fun whenProcessAndSetSubscriptionMessageAndNoTokenThenDoNothing() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"subscriptionPages","featureName":"useSubscription","method":"setSubscription","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(subscriptionsManager)
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

        assertEquals(0, callback.counter)
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

    private fun givenInterfaceIsRegistered() {
        messagingInterface.register(webView, callback)
        whenever(webView.url).thenReturn("https://abrown.duckduckgo.com")
    }

    private suspend fun givenAuthTokenIsSuccess() {
        whenever(subscriptionsManager.getAuthToken()).thenReturn(AuthToken.Success(authToken = "authToken"))
    }

    private suspend fun givenAuthTokenIsFailure() {
        whenever(subscriptionsManager.getAuthToken()).thenReturn(AuthToken.Failure(message = "something happened"))
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
