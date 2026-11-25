/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.pagetranslation.impl

import android.webkit.WebView
import com.duckduckgo.di.scopes.FragmentScope
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

/**
 * The WebView of the tab, as reported by [RealPageTranslation.attach].
 */
@SingleIn(FragmentScope::class)
class AttachedWebView @Inject constructor() {
    var webView: WebView? = null
        private set

    fun attach(webView: WebView) {
        this.webView = webView
    }

    fun detach(webView: WebView) {
        if (this.webView === webView) this.webView = null
    }
}
