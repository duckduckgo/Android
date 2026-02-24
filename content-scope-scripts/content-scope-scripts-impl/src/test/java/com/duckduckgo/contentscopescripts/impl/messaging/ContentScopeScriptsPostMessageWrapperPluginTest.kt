package com.duckduckgo.contentscopescripts.impl.messaging

import android.webkit.WebView
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.WebMessageListener
import com.duckduckgo.contentscopescripts.impl.CoreContentScopeScripts
import com.duckduckgo.contentscopescripts.impl.WebViewCompatContentScopeScripts
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.SubscriptionEvent
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.js.messaging.api.WebMessagingPlugin
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ContentScopeScriptsPostMessageWrapperPluginTest {
    private val mockWebMessagingPlugin: WebMessagingPlugin = mock()
    private val mockJsHelper: JsMessageHelper = mock()
    private val mockCoreContentScopeScripts: CoreContentScopeScripts = mock()
    private val mockWebViewCompatContentScopeScripts: WebViewCompatContentScopeScripts = mock()
    private val mockWebViewCapabilityChecker: WebViewCapabilityChecker = mock()
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

    lateinit var testee: ContentScopeScriptsPostMessageWrapperPlugin

    @Before
    fun setup() = runTest {
        whenever(mockCoreContentScopeScripts.callbackName).thenReturn("callbackName")
        whenever(mockCoreContentScopeScripts.secret).thenReturn("secret")
        whenever(mockWebMessagingPlugin.context).thenReturn("contentScopeScripts")
        whenever(mockJsonObject.toString()).thenReturn("{}")
        whenever(mockWebViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(true)
        testee = ContentScopeScriptsPostMessageWrapperPlugin(
            webMessagingPlugin = mockWebMessagingPlugin,
            jsMessageHelper = mockJsHelper,
            coreContentScopeScripts = mockCoreContentScopeScripts,
            webViewCompatContentScopeScripts = mockWebViewCompatContentScopeScripts,
            webViewCapabilityChecker = mockWebViewCapabilityChecker,
        )
    }

    @Test
    fun whenWebMessagingIsEnabledAndCapabilitySupportedThenPostMessageToWebMessagingPlugin() =
        runTest {
            whenever(mockWebViewCompatContentScopeScripts.isWebMessagingEnabled()).thenReturn(true)

            testee.postMessage(subscriptionEventData, mockWebView)

            verify(mockWebMessagingPlugin).postMessage(mockWebView, subscriptionEventData)
        }

    @Test
    fun whenWebMessagingIsNotEnabledThenPostMessageToContentScopeScriptsJsMessaging() =
        runTest {
            whenever(mockWebViewCompatContentScopeScripts.isWebMessagingEnabled()).thenReturn(false)

            testee.postMessage(subscriptionEventData, mockWebView)

            verify(mockJsHelper).sendSubscriptionEvent(
                eq(subscriptionEvent),
                eq("callbackName"),
                eq("secret"),
                eq(mockWebView),
            )
        }

    @Test
    fun whenCapabilityNotSupportedThenUseLegacyPath() =
        runTest {
            whenever(mockWebViewCapabilityChecker.isSupported(WebMessageListener)).thenReturn(false)
            val legacyTestee = ContentScopeScriptsPostMessageWrapperPlugin(
                webMessagingPlugin = mockWebMessagingPlugin,
                jsMessageHelper = mockJsHelper,
                coreContentScopeScripts = mockCoreContentScopeScripts,
                webViewCompatContentScopeScripts = mockWebViewCompatContentScopeScripts,
                webViewCapabilityChecker = mockWebViewCapabilityChecker,
            )
            whenever(mockWebViewCompatContentScopeScripts.isWebMessagingEnabled()).thenReturn(true)

            legacyTestee.postMessage(subscriptionEventData, mockWebView)

            verify(mockJsHelper).sendSubscriptionEvent(
                eq(subscriptionEvent),
                eq("callbackName"),
                eq("secret"),
                eq(mockWebView),
            )
        }
}
