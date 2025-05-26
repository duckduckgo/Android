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

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.ChatState.BLOCKED
import com.duckduckgo.duckchat.impl.ChatState.ERROR
import com.duckduckgo.duckchat.impl.ChatState.HIDE
import com.duckduckgo.duckchat.impl.ChatState.LOADING
import com.duckduckgo.duckchat.impl.ChatState.READY
import com.duckduckgo.duckchat.impl.ChatState.SHOW
import com.duckduckgo.duckchat.impl.ChatState.START_STREAM_NEW_PROMPT
import com.duckduckgo.duckchat.impl.ChatState.STREAMING
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper.Companion.DUCK_CHAT_FEATURE_NAME
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.json.JSONObject

@InjectWith(ActivityScope::class)
class DuckChatWebViewPoCActivity : DuckChatWebViewActivity() {

    @Inject
    lateinit var appBrowserNav: BrowserNav

    private val duckChatOmnibar: DuckChatOmnibarLayout by lazy { findViewById(R.id.duckChatOmnibar) }

    override val layoutResId: Int = R.layout.activity_duck_chat_webview_poc

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configurePoCUI()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun configurePoCUI() {
        duckChatOmnibar.apply {
            isVisible = true
            selectTab(1)
            onFire = {
                ActionBottomSheetDialog.Builder(this@DuckChatWebViewPoCActivity)
                    .setTitle(context.getString(R.string.duck_chat_delete_this_chat))
                    .setPrimaryItem(context.getString(R.string.duck_chat_delete_chat))
                    .setSecondaryItem(context.getString(R.string.duck_chat_cancel))
                    .addEventListener(
                        object : ActionBottomSheetDialog.EventListener() {
                            override fun onPrimaryItemClicked() {
                                contentScopeScripts.sendSubscriptionEvent(
                                    SubscriptionEventData(
                                        featureName = DUCK_CHAT_FEATURE_NAME,
                                        subscriptionName = "submitFireButtonAction",
                                        params = JSONObject("{}"),
                                    ),
                                )
                            }
                        },
                    )
                    .show()
            }
            onNewChat = {
                contentScopeScripts.sendSubscriptionEvent(
                    SubscriptionEventData(
                        featureName = DUCK_CHAT_FEATURE_NAME,
                        subscriptionName = "submitNewChatAction",
                        params = JSONObject("{}"),
                    ),
                )
            }
            onSearchSent = { message ->
                context.startActivity(appBrowserNav.openInNewTab(context, message))
                finish()
            }
            onDuckChatSent = { message ->
                contentScopeScripts.sendSubscriptionEvent(
                    SubscriptionEventData(
                        featureName = DUCK_CHAT_FEATURE_NAME,
                        subscriptionName = "submitAIChatNativePrompt",
                        params = JSONObject(
                            """
                            {
                              "platform": "android",
                              "query": {
                                "prompt": "$message",
                                "autoSubmit": true
                              }
                            }
                            """,
                        ),
                    ),
                )
                hideKeyboard(duckChatInput)
            }
            onStop = {
                contentScopeScripts.sendSubscriptionEvent(
                    SubscriptionEventData(
                        featureName = DUCK_CHAT_FEATURE_NAME,
                        subscriptionName = "submitPromptInterruption",
                        params = JSONObject("{}"),
                    ),
                )
            }
            onBack = { onBackPressed() }
            enableFireButton = true
            enableNewChatButton = true
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                duckChat.chatState.collect { state ->
                    Log.d("DuckChatWebViewActivity", "ChatState changed to: $state")

                    when (state) {
                        START_STREAM_NEW_PROMPT -> duckChatOmnibar.hideStopButton()
                        LOADING -> duckChatOmnibar.showStopButton()
                        STREAMING -> duckChatOmnibar.showStopButton()
                        ERROR -> duckChatOmnibar.hideStopButton()
                        READY -> {
                            duckChatOmnibar.hideStopButton()
                            duckChatOmnibar.isVisible = true
                        }
                        BLOCKED -> duckChatOmnibar.hideStopButton()
                        HIDE -> duckChatOmnibar.isVisible = false
                        SHOW -> duckChatOmnibar.isVisible = true
                    }
                }
            }
        }

        simpleWebview.apply {
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    hideKeyboard(duckChatOmnibar.duckChatInput)
                }
                false
            }
            isFocusableInTouchMode = true
        }
    }
}
