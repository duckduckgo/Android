/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.configuration.integration.modern.listener

import android.annotation.SuppressLint
import androidx.annotation.CheckResult
import androidx.collection.LruCache
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import com.duckduckgo.autofill.api.Callback
import com.duckduckgo.common.utils.ConflatedJob
import java.util.*

/**
 * Base class for handling autofill web messages, which is how we communicate between JS and native code for autofill
 *
 * Each web message will have a unique key which is used in the JS when initiating a web message. e.g., `window.ddgGetAutofillData.postMessage()`
 * And so each listener will declare which key they respond to.
 *
 * Each listener can also declare which origins they permit messages to come from. by default it will respond to all origins unless overridden.
 *
 * When a web message is received, there will be a `reply` object attached which is how the listener can respond back to the JS.
 * If the listener needs to interact with the user first, it should call [storeReply] which will return it a `requestId`.
 * This `requestId` can then be provided later to [AutofillMessagePoster] which will route the message to the correct receiver in the JS.
 *
 * The recommended way to declare a new web message listener is add a class which extends this abstract base class and
 * annotate it with `@ContributesMultibinding(FragmentScope::class)`. This will then be automatically registered and unregistered
 * when a new WebView in a tab is initialised or destroyed. See [InlineBrowserAutofill] for where this automatic registration happens.
 */
abstract class AutofillWebMessageListener : WebViewCompat.WebMessageListener {

    /**
     * The key that the JS will use to send a message to this listener
     *
     * The key needs to be agreed upon between JS-layer and native layer.
     * See https://app.asana.com/0/1206851683898855/1206851683898855/f for documentation
     */
    abstract val key: String

    /**
     * By default, a web message listener can be sent messages from all origins. This can be overridden to restrict to specific origins.
     */
    open val origins: Set<String> get() = setOf("*")

    lateinit var callback: Callback
    lateinit var tabId: String

    internal val job = ConflatedJob()

    /**
     * Called when a web message response should be sent back to the JS
     *
     * @param message the message to send back. The contents of this message will depend on the specific listener and what the JS schema expects.
     * @param requestId the requestId that was provided when calling [storeReply]
     * @return true if the message was handled by this listener or false if not
     */
    @SuppressLint("RequiresFeature")
    fun onResponse(
        message: String,
        requestId: String,
    ): Boolean {
        val replier = replyMap[requestId] ?: return false
        replier.postMessage(message)
        replyMap.remove(requestId)
        return true
    }

    /**
     * Store the reply object so that it can be used later to send a response back to the JS
     *
     * If the listener can respond immediately, it should do so using the `reply` object it has access to.
     * If the listener cannot response immediately, e.g., need user interaction first, can store the reply and access it later.
     *
     * @param reply the reply object to store
     * @return a unique requestId that can be used later to send a response back to the JS.
     * This requestId must be provided when later sending the message. e.g., provided to [AutofillMessagePoster] alongside the message.
     */
    @CheckResult
    protected fun storeReply(reply: JavaScriptReplyProxy): String {
        return UUID.randomUUID().toString().also {
            replyMap.put(it, reply)
        }
    }

    /**
     * Cancel any outstanding requests and clean up resources
     */
    fun cancelOutstandingRequests() {
        replyMap.evictAll()
        job.cancel()
    }

    /**
     * Store a small list of reply objects, where the requestId is the key.
     * Replies are typically disposed of immediately upon using, but in some edge cases we might not respond and the stored replies are stale.
     * Using a LRU cache to limit the number of stale replies we'd keep around.
     */
    private val replyMap = LruCache<String, JavaScriptReplyProxy>(10)

    companion object {
        val duckDuckGoOriginOnly = setOf("https://duckduckgo.com")
    }
}
