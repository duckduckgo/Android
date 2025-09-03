/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.api

import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment

/**
 * A PluginPoint for autofill dialog result handlers
 *
 * This ties into the fragment result API, and allows plugins to handle the result of a fragment
 * For a fragment and its result handler to be associated, they must communicate using the same `resultKey`
 */
interface AutofillFragmentResultsPlugin {

    /**
     * Called when the result handler has a result to process
     * Will be invoked on the main thread.
     */
    @MainThread
    suspend fun processResult(
        result: Bundle,
        context: Context,
        tabId: String,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
        webView: WebView?,
    )

    /**
     * A unique key that will match the result key a fragment specifies when returning a result
     * The tabId is provided to allow plugins to only handle results for a specific tab
     * @return the key used to identify the result of this fragment
     */
    fun resultKey(tabId: String): String
}
