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
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import com.duckduckgo.autofill.api.Callback
import com.duckduckgo.common.utils.ConflatedJob
import java.util.*
import timber.log.Timber

abstract class AutofillWebMessageListener : WebViewCompat.WebMessageListener {

    abstract val key: String
    open val origins: Set<String> get() = setOf("*")

    lateinit var callback: Callback
    lateinit var tabId: String

    internal val job = ConflatedJob()

    @SuppressLint("RequiresFeature")
    fun onResponse(
        message: String,
        requestId: String,
    ): Boolean {
        val replier = replyMap[requestId] ?: return false
        replier.postMessage(message)
        return true
    }

    @CheckResult
    protected fun storeReply(reply: JavaScriptReplyProxy): String {
        return UUID.randomUUID().toString().also {
            replyMap[it] = reply
        }
    }

    fun cancelOutstandingRequests() {
        Timber.d("cancelOutstandingRequests ${this::class.java.simpleName}")
        replyMap.clear()
        job.cancel()
    }

    private val replyMap = mutableMapOf<String, JavaScriptReplyProxy>()

    companion object {
        val duckDuckGoOriginOnly = setOf("https://duckduckgo.com")
    }
}
