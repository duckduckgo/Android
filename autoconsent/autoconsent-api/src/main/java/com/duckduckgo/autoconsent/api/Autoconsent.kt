/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autoconsent.api

import android.webkit.WebView
import androidx.fragment.app.DialogFragment

/** Public interface for the Autoconsent (CMP) feature */
interface Autoconsent {
    /**
     * This method injects the JS code needed to run autoconsent. It requires a [WebView] instance.
     */
    fun injectAutoconsent(webView: WebView)
    /**
     * This method adds the JS interface for autoconsent to create a bridge between JS and our client.
     * It requires a [WebView] instance and an [AutoconsentCallback].
     */
    fun addJsInterface(webView: WebView, autoconsentCallback: AutoconsentCallback)
}

/**
 * Public interface for the Autoconsent callback.
 * It is required to be implemented and passed when calling addJsInterface and provides a useful set of callbacks.
 */
interface AutoconsentCallback {
    /**
     * This method is called whenever a popup is handled for the first time.
     * It passes a [DialogFragment] and a [String] to be used as a dialog tag.
     */
    fun onFirstPopUpHandled(dialogFragment: DialogFragment, tag: String)

    /**
     * This method is called whenever a popup is handled but not for the first time.
     */
    fun onPopUpHandled()
}
