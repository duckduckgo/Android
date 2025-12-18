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

package com.duckduckgo.pir.impl.integration.fakes

import android.webkit.WebView
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.pir.impl.scripts.PIRScriptConstants
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestParams
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * A fake PirMessagingInterface that automatically returns success responses for all actions.
 * This is used for e2e integration testing to verify the entire PIR action flow without actually executing JavaScript in a WebView.
 */
class FakePirMessagingInterface(moshi: Moshi) : JsMessaging {
    private val requestAdapter = moshi.adapter(PirScriptRequestParams::class.java)

    private var jsMessageCallback: JsMessageCallback? = null

    @Suppress("NoHardcodedCoroutineDispatcher")
    private val scope = CoroutineScope(Dispatchers.Default)

    // Track all actions that were pushed to the JS layer
    val pushedActions = mutableListOf<BrokerAction>()

    // Configurable extract response for confirmation scan testing
    private var nextExtractResponseEmpty: Boolean = false

    /**
     * Configures the next Extract action to return an empty list of profiles.
     * Used to simulate confirmation scan where the profile has been removed.
     */
    fun setNextExtractResponseEmpty(empty: Boolean) {
        nextExtractResponseEmpty = empty
    }

    fun clearPushedActions() {
        pushedActions.clear()
    }

    override fun register(
        webView: WebView,
        jsMessageCallback: JsMessageCallback?,
    ) {
        this.jsMessageCallback = jsMessageCallback
    }

    override fun onResponse(response: JsCallbackData) {
        // Not needed for fake implementation
    }

    override fun process(
        message: String,
        secret: String,
    ) {
        // Not needed for fake implementation
    }

    override fun sendSubscriptionEvent(subscriptionEventData: SubscriptionEventData) {
        // This is called when an action is pushed
        // Parse the action and automatically send back a success response
        scope.launch {
            // Small delay to simulate async processing
            delay(50)

            val paramsJson = subscriptionEventData.params.toString()
            val requestParams = requestAdapter.fromJson(paramsJson)

            requestParams?.state?.action?.let { action ->
                pushedActions.add(action)

                val resultJsonObject = createSuccessResponseJson(action)

                jsMessageCallback?.process(
                    featureName = PIRScriptConstants.SCRIPT_FEATURE_NAME,
                    method = PIRScriptConstants.RECEIVED_METHOD_NAME_COMPLETED,
                    id = null,
                    data = resultJsonObject,
                )
            }
        }
    }

    private fun createSuccessResponseJson(action: BrokerAction): JSONObject {
        // TODO Use JSON string / snippets like with fake-broker.json
        val successJson = when (action) {
            is BrokerAction.Navigate -> JSONObject().apply {
                put("actionID", action.id)
                put("actionType", "navigate")
                put(
                    "response",
                    JSONObject().apply {
                        put("url", action.url)
                    },
                )
            }

            is BrokerAction.Extract -> JSONObject().apply {
                put("actionID", action.id)
                put("actionType", "extract")
                if (nextExtractResponseEmpty) {
                    // Return empty array for confirmation scan (profile removed)
                    put("response", JSONArray())
                    nextExtractResponseEmpty = false
                } else {
                    put(
                        "response",
                        JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("name", "John Doe")
                                    put("fullName", "John Doe")
                                    put("age", "33")
                                    put("profileUrl", "https://www.fake-broker.com/John-Doe/NY/New-York/abc123")
                                    put("identifier", "abc123")
                                    put("email", "john.doe@example.com")
                                    put("phoneNumbers", JSONArray(listOf("+1-555-0101")))
                                    put("relatives", JSONArray(listOf("Jane Doe")))
                                    put("alternativeNames", JSONArray(listOf("J. Doe")))
                                    put(
                                        "addresses",
                                        JSONArray().apply {
                                            put(
                                                JSONObject().apply {
                                                    put("city", "New York")
                                                    put("state", "NY")
                                                    put("fullAddress", "123 Main St, New York, NY 10001")
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            }

            is BrokerAction.FillForm -> JSONObject().apply {
                put("actionID", action.id)
                put("actionType", "fillForm")
            }

            is BrokerAction.Click -> JSONObject().apply {
                put("actionID", action.id)
                put("actionType", "click")
            }

            is BrokerAction.GetCaptchaInfo -> JSONObject().apply {
                put("actionID", action.id)
                put("actionType", "getCaptchaInfo")
                put(
                    "response",
                    JSONObject().apply {
                        put("siteKey", "fake-site-key")
                        put("url", "https://fake-captcha-url.com")
                        put("type", "recaptcha")
                    },
                )
            }

            is BrokerAction.SolveCaptcha -> JSONObject().apply {
                put("actionID", action.id)
                put("actionType", "solveCaptcha")
                put(
                    "response",
                    JSONObject().apply {
                        put(
                            "callback",
                            JSONObject().apply {
                                put("eval", "console.log('fake captcha callback');")
                            },
                        )
                    },
                )
            }

            is BrokerAction.Expectation -> JSONObject().apply {
                put("actionID", action.id)
                put("actionType", "expectation")
            }

            is BrokerAction.EmailConfirmation -> JSONObject().apply {
                put("actionID", action.id)
                put("actionType", "emailConfirmation")
            }

            is BrokerAction.Condition -> JSONObject().apply {
                put("actionID", action.id)
                put("actionType", "condition")
                put(
                    "response",
                    JSONObject().apply {
                        put("actions", JSONArray())
                    },
                )
            }
        }

        return JSONObject().apply {
            put(
                "result",
                JSONObject().apply {
                    put("success", successJson)
                },
            )
        }
    }

    override val context: String = PIRScriptConstants.SCRIPT_CONTEXT_NAME
    override val callbackName: String = "messageCallback"
    override val secret: String = "messageSecret"
    override val allowedDomains: List<String> = emptyList()
}
