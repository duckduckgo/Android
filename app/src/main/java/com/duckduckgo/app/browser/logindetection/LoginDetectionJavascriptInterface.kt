/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser.logindetection

import android.webkit.JavascriptInterface
import logcat.LogPriority.INFO
import logcat.logcat

@Suppress("unused")
class LoginDetectionJavascriptInterface(private val onLoginDetected: () -> Unit) {

    @JavascriptInterface
    fun log(message: String) {
        logcat(INFO) { "LoginDetectionInterface $message" }
    }

    @JavascriptInterface
    fun loginDetected() {
        onLoginDetected()
    }

    companion object {
        // Interface name used inside login_form_detection.js
        const val JAVASCRIPT_INTERFACE_NAME = "LoginDetection"
    }
}
