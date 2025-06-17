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

// TODO
// @InjectWith(ActivityScope::class)
// class DuckChatWebViewPoCActivity : DuckChatWebViewActivity() {
//
//     @Inject
//     lateinit var appBrowserNav: BrowserNav
//
//     private val duckChatOmnibar: DuckChatOmnibarLayout by lazy { findViewById(R.id.duckChatOmnibar) }
//
//     override val layoutResId: Int = R.layout.activity_duck_chat_webview_poc
//
//     override fun onCreate(savedInstanceState: Bundle?) {
//         super.onCreate(savedInstanceState)
//         configurePoCUI()
//     }
//
//     @SuppressLint("ClickableViewAccessibility")
//     private fun configurePoCUI() {
//         duckChatOmnibar.apply {
//             isVisible = true
//             selectTab(1)
//             onFire = {
//                 ActionBottomSheetDialog.Builder(this@DuckChatWebViewPoCActivity)
//                     .setTitle(context.getString(R.string.duck_chat_delete_this_chat))
//                     .setPrimaryItem(context.getString(R.string.duck_chat_delete_chat))
//                     .setSecondaryItem(context.getString(R.string.duck_chat_cancel))
//                     .addEventListener(
//                         object : ActionBottomSheetDialog.EventListener() {
//                             override fun onPrimaryItemClicked() {
//                                 contentScopeScripts.sendSubscriptionEvent(
//                                     SubscriptionEventData(
//                                         featureName = DUCK_CHAT_FEATURE_NAME,
//                                         subscriptionName = "submitFireButtonAction",
//                                         params = JSONObject("{}"),
//                                     ),
//                                 )
//                             }
//                         },
//                     )
//                     .show()
//             }
//             onNewChat = {
//                 contentScopeScripts.sendSubscriptionEvent(
//                     SubscriptionEventData(
//                         featureName = DUCK_CHAT_FEATURE_NAME,
//                         subscriptionName = "submitNewChatAction",
//                         params = JSONObject("{}"),
//                     ),
//                 )
//             }
//             onSearchSent = { message ->
//                 context.startActivity(appBrowserNav.openInNewTab(context, message))
//                 finish()
//             }
//             onDuckChatSent = { message ->
//                 contentScopeScripts.sendSubscriptionEvent(
//                     SubscriptionEventData(
//                         featureName = DUCK_CHAT_FEATURE_NAME,
//                         subscriptionName = "submitAIChatNativePrompt",
//                         params = JSONObject(
//                             """
//                             {
//                               "platform": "android",
//                               "query": {
//                                 "prompt": "$message",
//                                 "autoSubmit": true
//                               }
//                             }
//                             """,
//                         ),
//                     ),
//                 )
//                 hideKeyboard(duckChatInput)
//             }
//             onStop = {
//                 contentScopeScripts.sendSubscriptionEvent(
//                     SubscriptionEventData(
//                         featureName = DUCK_CHAT_FEATURE_NAME,
//                         subscriptionName = "submitPromptInterruption",
//                         params = JSONObject("{}"),
//                     ),
//                 )
//             }
//             onBack = { onBackPressed() }
//             enableFireButton = true
//             enableNewChatButton = true
//         }
//
//         lifecycleScope.launch {
//             repeatOnLifecycle(Lifecycle.State.STARTED) {
//                 duckChat.chatState.collect { state ->
//                     Log.d("DuckChatWebViewActivity", "ChatState changed to: $state")
//
//                     when (state) {
//                         START_STREAM_NEW_PROMPT -> duckChatOmnibar.hideStopButton()
//                         LOADING -> duckChatOmnibar.showStopButton()
//                         STREAMING -> duckChatOmnibar.showStopButton()
//                         ERROR -> duckChatOmnibar.hideStopButton()
//                         READY -> {
//                             duckChatOmnibar.hideStopButton()
//                             duckChatOmnibar.isVisible = true
//                         }
//                         BLOCKED -> duckChatOmnibar.hideStopButton()
//                         HIDE -> duckChatOmnibar.isVisible = false
//                         SHOW -> duckChatOmnibar.isVisible = true
//                     }
//                 }
//             }
//         }
//
//         simpleWebview.apply {
//             setOnTouchListener { v, event ->
//                 if (event.action == MotionEvent.ACTION_DOWN) {
//                     hideKeyboard(duckChatOmnibar.duckChatInput)
//                 }
//                 false
//             }
//             isFocusableInTouchMode = true
//         }
//     }
// }
