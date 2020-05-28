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

package com.duckduckgo.app.browser

import android.content.Context
import android.webkit.JavascriptInterface
import timber.log.Timber

// Interface name used inside login_form_detection.js
const val LOGIN_DETECTION_INTERFACE_NAME = "LoginDetection"

class LoginDetectionInterface(private val viewModel: BrowserTabViewModel) {

    /** Show a toast from the web page  */
    @JavascriptInterface
    fun showToast(toast: String) {
        Timber.i("LoginDetectionInterface $toast")
    }

    /** Show a toast from the web page  */
    @JavascriptInterface
    fun loginDetected(toast: String) {
        Timber.i("LoginDetectionInterface $toast")
        //Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
        viewModel.loginDetected()
    }
}