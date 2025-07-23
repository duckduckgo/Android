/*
 * Copyright (c) 2025 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.duckchat.impl.ui

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.SubscriptionScreens.RestoreSubscriptionScreenWithParams
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionPurchaseWithOrigin
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionScreenNoParams
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionsSettingsScreenWithEmptyParams
import com.duckduckgo.subscriptions.api.SubscriptionsJSHelper
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SubscriptionsHandlerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val dispatcherProvider = coroutineRule.testDispatcherProvider

    @Mock
    private lateinit var subscriptionsJSHelper: SubscriptionsJSHelper

    @Mock
    private lateinit var globalActivityStarter: GlobalActivityStarter

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var contentScopeScripts: JsMessaging

    private lateinit var subscriptionsHandler: SubscriptionsHandler

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        subscriptionsHandler = SubscriptionsHandler(
            subscriptionsJSHelper,
            globalActivityStarter,
            dispatcherProvider,
        )
    }

    @Test
    fun `handleSubscriptionsFeature processes js callback and responds when response is not null`() = runTest {
        val featureName = "subscriptions"
        val method = "someMethod"
        val id = "testId"
        val data = JSONObject()
        val response = JsCallbackData(JSONObject(), featureName, method, id)
        whenever(subscriptionsJSHelper.processJsCallbackMessage(featureName, method, id, data))
            .thenReturn(response)

        subscriptionsHandler.handleSubscriptionsFeature(
            featureName,
            method,
            id,
            data,
            context,
            coroutineRule.testScope,
            contentScopeScripts,
        )

        verify(subscriptionsJSHelper).processJsCallbackMessage(featureName, method, id, data)
        verify(contentScopeScripts).onResponse(response)
    }

    @Test
    fun `handleSubscriptionsFeature processes js callback but does not respond when response is null`() = runTest {
        val featureName = "subscriptions"
        val method = "someMethod"
        val id = "testId"
        val data = JSONObject()
        whenever(subscriptionsJSHelper.processJsCallbackMessage(featureName, method, id, data))
            .thenReturn(null)

        subscriptionsHandler.handleSubscriptionsFeature(
            featureName,
            method,
            id,
            data,
            context,
            coroutineRule.testScope,
            contentScopeScripts,
        )

        verify(subscriptionsJSHelper).processJsCallbackMessage(featureName, method, id, data)
        verify(contentScopeScripts, never()).onResponse(any())
    }

    @Test
    fun `handleSubscriptionsFeature launches settings screen when method is backToSettings`() = runTest {
        val featureName = "subscriptions"
        val method = "backToSettings"
        val id = "testId"
        val data = JSONObject()
        val response = JsCallbackData(JSONObject(), featureName, method, id)
        whenever(subscriptionsJSHelper.processJsCallbackMessage(featureName, method, id, data))
            .thenReturn(response)

        subscriptionsHandler.handleSubscriptionsFeature(
            featureName,
            method,
            id,
            data,
            context,
            coroutineRule.testScope,
            contentScopeScripts,
        )

        verify(globalActivityStarter).start(context, SubscriptionsSettingsScreenWithEmptyParams)
    }

    @Test
    fun `handleSubscriptionsFeature launches subscription activation screen when method is openSubscriptionActivation`() = runTest {
        val featureName = "subscriptions"
        val method = "openSubscriptionActivation"
        val id = "testId"
        val data = JSONObject()
        val response = JsCallbackData(JSONObject(), featureName, method, id)
        whenever(subscriptionsJSHelper.processJsCallbackMessage(featureName, method, id, data))
            .thenReturn(response)

        subscriptionsHandler.handleSubscriptionsFeature(
            featureName,
            method,
            id,
            data,
            context,
            coroutineRule.testScope,
            contentScopeScripts,
        )

        verify(globalActivityStarter).start(context, RestoreSubscriptionScreenWithParams(isOriginWeb = true))
    }

    @Test
    fun `handleSubscriptionsFeature launches subscription purchase screen when method is openSubscriptionPurchase`() = runTest {
        val featureName = "subscriptions"
        val method = "openSubscriptionPurchase"
        val id = "testId"
        val data = JSONObject()
        val response = JsCallbackData(JSONObject(), featureName, method, id)
        whenever(subscriptionsJSHelper.processJsCallbackMessage(featureName, method, id, data))
            .thenReturn(response)

        subscriptionsHandler.handleSubscriptionsFeature(
            featureName,
            method,
            id,
            data,
            context,
            coroutineRule.testScope,
            contentScopeScripts,
        )

        verify(globalActivityStarter).start(context, SubscriptionScreenNoParams)
    }

    @Test
    fun `handleSubscriptionsFeature launches subscription purchase with origin when valid origin provided`() = runTest {
        val featureName = "subscriptions"
        val method = "openSubscriptionPurchase"
        val id = "testId"
        val data = JSONObject("{\"origin\": \"duckai_chat\"}")
        val response = JsCallbackData(JSONObject(), featureName, method, id)
        whenever(subscriptionsJSHelper.processJsCallbackMessage(featureName, method, id, data))
            .thenReturn(response)

        subscriptionsHandler.handleSubscriptionsFeature(
            featureName,
            method,
            id,
            data,
            context,
            coroutineRule.testScope,
            contentScopeScripts,
        )

        verify(globalActivityStarter).start(context, SubscriptionPurchaseWithOrigin("duckai_chat"))
    }

    @Test
    fun `handleSubscriptionsFeature launches subscription purchase without origin when empty origin provided`() = runTest {
        val featureName = "subscriptions"
        val method = "openSubscriptionPurchase"
        val id = "testId"
        val data = JSONObject("{\"origin\": \"\"}")
        val response = JsCallbackData(JSONObject(), featureName, method, id)
        whenever(subscriptionsJSHelper.processJsCallbackMessage(featureName, method, id, data))
            .thenReturn(response)

        subscriptionsHandler.handleSubscriptionsFeature(
            featureName,
            method,
            id,
            data,
            context,
            coroutineRule.testScope,
            contentScopeScripts,
        )

        verify(globalActivityStarter).start(context, SubscriptionScreenNoParams)
    }

    @Test
    fun `handleSubscriptionsFeature launches subscription purchase without origin when data is null for openSubscriptionPurchase`() = runTest {
        val featureName = "subscriptions"
        val method = "openSubscriptionPurchase"
        val id = "testId"
        val data: JSONObject? = null
        val response = JsCallbackData(JSONObject(), featureName, method, id)
        whenever(subscriptionsJSHelper.processJsCallbackMessage(featureName, method, id, data))
            .thenReturn(response)

        subscriptionsHandler.handleSubscriptionsFeature(
            featureName,
            method,
            id,
            data,
            context,
            coroutineRule.testScope,
            contentScopeScripts,
        )

        verify(globalActivityStarter).start(context, SubscriptionScreenNoParams)
    }

    @Test
    fun `handleSubscriptionsFeature handles null data parameter`() = runTest {
        val featureName = "subscriptions"
        val method = "backToSettings"
        val id = "testId"
        val data: JSONObject? = null
        val response = JsCallbackData(JSONObject(), featureName, method, id)
        whenever(subscriptionsJSHelper.processJsCallbackMessage(featureName, method, id, data))
            .thenReturn(response)

        subscriptionsHandler.handleSubscriptionsFeature(
            featureName,
            method,
            id,
            data,
            context,
            coroutineRule.testScope,
            contentScopeScripts,
        )

        verify(subscriptionsJSHelper).processJsCallbackMessage(featureName, method, id, data)
        verify(globalActivityStarter).start(context, SubscriptionsSettingsScreenWithEmptyParams)
    }

    @Test
    fun `handleSubscriptionsFeature handles null id parameter`() = runTest {
        val featureName = "subscriptions"
        val method = "openSubscriptionPurchase"
        val id: String? = null
        val data = JSONObject()
        val response = JsCallbackData(JSONObject(), featureName, method, "")
        whenever(subscriptionsJSHelper.processJsCallbackMessage(featureName, method, id, data))
            .thenReturn(response)

        subscriptionsHandler.handleSubscriptionsFeature(
            featureName,
            method,
            id,
            data,
            context,
            coroutineRule.testScope,
            contentScopeScripts,
        )

        verify(subscriptionsJSHelper).processJsCallbackMessage(featureName, method, id, data)
        verify(globalActivityStarter).start(context, SubscriptionScreenNoParams)
    }
}
