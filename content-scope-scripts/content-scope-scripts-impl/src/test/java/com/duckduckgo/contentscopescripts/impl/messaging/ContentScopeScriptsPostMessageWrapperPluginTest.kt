package com.duckduckgo.contentscopescripts.impl.messaging

import android.webkit.WebView
import com.duckduckgo.contentscopescripts.impl.CoreContentScopeScripts
import com.duckduckgo.contentscopescripts.impl.WebViewCompatContentScopeScripts
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.SubscriptionEvent
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.js.messaging.api.WebMessaging
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ContentScopeScriptsPostMessageWrapperPluginTest {
    private val mockWebMessaging: WebMessaging = mock()
    private val mockJsHelper: JsMessageHelper = mock()
    private val mockCoreContentScopeScripts: CoreContentScopeScripts = mock()
    private val mockWebViewCompatContentScopeScripts: WebViewCompatContentScopeScripts = mock()
    private val mockWebView: WebView = mock()
    private val mockJsonObject: JSONObject = mock()
    private val subscriptionEventData =
        SubscriptionEventData(
            featureName = "testFeature",
            subscriptionName = "testSubscription",
            params = mockJsonObject,
        )
    private val subscriptionEvent =
        SubscriptionEvent(
            context = "contentScopeScripts",
            featureName = "testFeature",
            subscriptionName = "testSubscription",
            params = mockJsonObject,
        )

    val testee =
        ContentScopeScriptsPostMessageWrapperPlugin(
            webMessaging = mockWebMessaging,
            jsMessageHelper = mockJsHelper,
            coreContentScopeScripts = mockCoreContentScopeScripts,
            webViewCompatContentScopeScripts = mockWebViewCompatContentScopeScripts,
        )

    @Before
    fun setup() {
        whenever(mockCoreContentScopeScripts.callbackName).thenReturn("callbackName")
        whenever(mockCoreContentScopeScripts.secret).thenReturn("secret")
        whenever(mockWebMessaging.context).thenReturn("contentScopeScripts")
        whenever(mockJsonObject.toString()).thenReturn("{}")
    }

    @Test
    fun whenWebViewCompatContentScopeScriptsIsEnabledThenPostMessageToWebMessagingPlugin() =
        runTest {
            whenever(mockWebViewCompatContentScopeScripts.isEnabled()).thenReturn(true)

            testee.postMessage(subscriptionEventData, mockWebView)

        verify(mockWebMessaging).postMessage(mockWebView, subscriptionEventData)
    }

    @Test
    fun whenWebViewCompatContentScopeScriptsIsNotEnabledThenPostMessageToContentScopeScriptsJsMessaging() =
        runTest {
            whenever(mockWebViewCompatContentScopeScripts.isEnabled()).thenReturn(false)

            testee.postMessage(subscriptionEventData, mockWebView)

            verify(mockJsHelper).sendSubscriptionEvent(
                eq(subscriptionEvent),
                eq("callbackName"),
                eq("secret"),
                eq(mockWebView),
            )
        }
}
