package com.duckduckgo.subscriptions.impl.messaging

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.JsRequestResponse
import com.duckduckgo.subscriptions.impl.AccessTokenResult
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
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

@RunWith(AndroidJUnit4::class)
class ItrMessagingInterfaceTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private val webView: WebView = mock()
    private val jsMessageHelper: JsMessageHelper = mock()
    private val subscriptionsManager: SubscriptionsManager = mock()
    private val messagingInterface = ItrMessagingInterface(
        subscriptionsManager,
        jsMessageHelper,
        coroutineRule.testDispatcherProvider,
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

        verifyNoInteractions()
    }

    @Test
    fun whenProcessUnknownSecretDoNothing() = runTest {
        givenInterfaceIsRegistered()

        val message = """
            {"context":"identityTheftRestorationPages","featureName":"useIdentityTheftRestoration","method":"getAccessToken","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "secret")

        verifyNoInteractions()
    }

    @Test
    fun whenProcessNoUrlDoNothing() = runTest {
        messagingInterface.register(webView, callback)

        val message = """
            {"context":"identityTheftRestorationPages","featureName":"useIdentityTheftRestoration","method":"getAccessToken","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions()
    }

    @Test
    fun whenProcessInterfaceNotRegisteredDoNothing() = runTest {
        whenever(webView.url).thenReturn("https://duckduckgo.com/test")

        val message = """
            {"context":"identityTheftRestorationPages","featureName":"useIdentityTheftRestoration","method":"getAccessToken","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions()
    }

    @Test
    fun whenProcessIfMethodDoesNotMatchDoNothing() = runTest {
        givenInterfaceIsRegistered()
        givenAccessTokenIsSuccess()

        val message = """
            {"context":"identityTheftRestorationPages","featureName":"useIdentityTheftRestoration","method":"test","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions()
    }

    @Test
    fun whenProcessAndGetAccessTokenIfNoIdDoNothing() = runTest {
        givenInterfaceIsRegistered()
        givenAccessTokenIsSuccess()

        val message = """
            {"context":"identityTheftRestorationPages","featureName":"useIdentityTheftRestoration","method":"getAccessToken","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions(jsMessageHelper)
    }

    @Test
    fun whenProcessAndGetAccessTokenMessageThenReturnResponse() = runTest {
        givenInterfaceIsRegistered()
        givenAccessTokenIsSuccess()

        val expected = JsRequestResponse.Success(
            context = "identityTheftRestorationPages",
            featureName = "useIdentityTheftRestoration",
            method = "getAccessToken",
            id = "myId",
            result = JSONObject("""{ "token":"accessToken"}"""),
        )

        val message = """
            {"context":"identityTheftRestorationPages","featureName":"useIdentityTheftRestoration","method":"getAccessToken","id":"myId","params":{}}
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
            context = "identityTheftRestorationPages",
            featureName = "useIdentityTheftRestoration",
            method = "getAccessToken",
            id = "myId",
            result = JSONObject("""{ }"""),
        )

        val message = """
            {"context":"identityTheftRestorationPages","featureName":"useIdentityTheftRestoration","method":"getAccessToken","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        val captor = argumentCaptor<JsRequestResponse>()
        verify(jsMessageHelper).sendJsResponse(captor.capture(), eq(CALLBACK_NAME), eq(SECRET), eq(webView))
        val jsMessage = captor.firstValue

        assertTrue(jsMessage is JsRequestResponse.Success)
        checkEquals(expected, jsMessage)
    }

    @Test
    fun whenProcessAndGetAccessTokenMessageIfUrlNotInAllowListedDomainsThenDoNothing() = runTest {
        messagingInterface.register(webView, callback)
        whenever(webView.url).thenReturn("https://duckduckgo.example.com")
        givenAccessTokenIsSuccess()

        val message = """
            {"context":"identityTheftRestorationPages","featureName":"useIdentityTheftRestoration","method":"getAccessToken","id":"myId","params":{}}
        """.trimIndent()

        messagingInterface.process(message, "duckduckgo-android-messaging-secret")

        verifyNoInteractions()
    }

    private fun verifyNoInteractions() {
        verifyNoInteractions(jsMessageHelper)
        verifyNoInteractions(subscriptionsManager)
        verify(webView, never()).evaluateJavascript(any(), any())
    }

    private fun givenInterfaceIsRegistered() {
        messagingInterface.register(webView, callback)
        whenever(webView.url).thenReturn("https://duckduckgo.com/test")
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
