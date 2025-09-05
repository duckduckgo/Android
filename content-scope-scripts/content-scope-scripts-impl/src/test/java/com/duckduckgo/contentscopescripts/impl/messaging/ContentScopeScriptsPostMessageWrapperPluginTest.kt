package com.duckduckgo.contentscopescripts.impl.messaging

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.contentscopescripts.impl.WebViewCompatContentScopeScripts
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.js.messaging.api.WebMessagingPlugin
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ContentScopeScriptsPostMessageWrapperPluginTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockWebMessagingPlugin: WebMessagingPlugin = mock()
    private val mockContentScopeScriptsJsMessaging: ContentScopeScriptsJsMessaging = mock()
    private val mockWebViewCompatContentScopeScripts: WebViewCompatContentScopeScripts = mock()
    private val subscriptionEventData = SubscriptionEventData(
        featureName = "testFeature",
        subscriptionName = "testSubscription",
        params = JSONObject(),
    )

    val testee = ContentScopeScriptsPostMessageWrapperPlugin(
        webMessagingPlugin = mockWebMessagingPlugin,
        contentScopeScriptsJsMessaging = mockContentScopeScriptsJsMessaging,
        webViewCompatContentScopeScripts = mockWebViewCompatContentScopeScripts,
        coroutineScope = coroutineRule.testScope,
    )

    @Test
    fun whenWebViewCompatContentScopeScriptsIsEnabledThenPostMessageToWebMessagingPlugin() = runTest {
        whenever(mockWebViewCompatContentScopeScripts.isEnabled()).thenReturn(true)

        testee.postMessage(subscriptionEventData)

        verify(mockWebMessagingPlugin).postMessage(subscriptionEventData)
    }

    @Test
    fun whenWebViewCompatContentScopeScriptsIsNotEnabledThenPostMessageToContentScopeScriptsJsMessaging() = runTest {
        whenever(mockWebViewCompatContentScopeScripts.isEnabled()).thenReturn(false)

        testee.postMessage(subscriptionEventData)

        verify(mockContentScopeScriptsJsMessaging).sendSubscriptionEvent(subscriptionEventData)
    }
}
