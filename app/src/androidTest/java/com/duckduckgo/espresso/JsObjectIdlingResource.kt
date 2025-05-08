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

package com.duckduckgo.espresso

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import androidx.test.espresso.IdlingResource

class JsObjectIdlingResource(
    private val webView: WebView,
    private val objectName: String,
) : IdlingResource {

    @Volatile
    private var callback: IdlingResource.ResourceCallback? = null

    private var isIdle = false

    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 100L // milliseconds
    private val timeoutMillis = 20_000L // 20 seconds
    private val startTime = SystemClock.elapsedRealtime()

    override fun getName(): String = "JsObjectIdlingResource for $objectName"

    override fun isIdleNow(): Boolean = isIdle

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
        this.callback = callback
        pollForJsObject()
    }

    private fun pollForJsObject() {
        if (SystemClock.elapsedRealtime() - startTime > timeoutMillis) {
            isIdle = true
            callback?.onTransitionToIdle()
            // fail test if the object is not found within the timeout
            throw AssertionError("JS object '$objectName' did not appear within timeout.")
        }

        webView.evaluateJavascript(
            "(typeof $objectName !== 'undefined')",
        ) { result ->
            if (result == "true") {
                isIdle = true
                callback?.onTransitionToIdle()
            } else {
                handler.postDelayed({ pollForJsObject() }, checkInterval)
            }
        }
    }
}
